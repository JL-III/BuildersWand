package com.playtheatria.buildersWand.gesture;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Density;
import com.playtheatria.buildersWand.form.Expansion;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.FormLimits;
import com.playtheatria.buildersWand.form.Measurement;
import com.playtheatria.buildersWand.form.Orientation;
import com.playtheatria.buildersWand.form.PlanPolicy;
import com.playtheatria.buildersWand.form.SurfaceRestriction;
import com.playtheatria.buildersWand.wand.BlockOrientation;
import com.playtheatria.buildersWand.wand.MaterialSelectionSnapshot;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.PlanOptions;
import com.playtheatria.buildersWand.wave.PlanOrdering;
import com.playtheatria.buildersWand.wave.PlanValidationCell;
import com.playtheatria.buildersWand.wave.PlannedCell;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one place live plans are derived from aim (design §7.3–§7.6), shared by the gesture
 * listener and the ghost service. Pure of side effects; reads only the player's aim, resolved
 * material selection, and world.
 *
 * <p>Diagonal note: the core §6.2 geometry is faithful (tread width along L with a
 * negative-width mirror, run along N); the §7.5 tread-axis pivot and backward half-turn are
 * not replicated (they require the voxels-slim measurement source and a mutable orientation).
 */
public final class LivePlan {

    private LivePlan() {
    }

    /**
     * The aim ray against solid blocks, ignoring passable ones (short grass, flowers, …) and
     * fluids — so aiming through grass anchors in the grass cell, like normal placement.
     */
    public static RayTraceResult rayTrace(Player player, PluginConfig config) {
        Location eye = player.getEyeLocation();
        return player.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), config.anchorReach, FluidCollisionMode.NEVER, true);
    }

    /** The cell the first click would anchor (aimed block + face normal), or empty. */
    public static Optional<BlockVector> wouldBeAnchor(Player player, PluginConfig config) {
        RayTraceResult hit = rayTrace(player, config);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            return Optional.empty();
        }
        Block block = hit.getHitBlock();
        BlockFace face = hit.getHitBlockFace();
        return Optional.of(new BlockVector(
                block.getX() + face.getModX(),
                block.getY() + face.getModY(),
                block.getZ() + face.getModZ()));
    }

    /** The live plan for the ghost and the print click. Empty when no valid material. */
    public static Optional<Plan> derive(Player player, GestureSession session, PluginConfig config, WandItems wandItems) {
        if (session.frozenPlan != null) {
            return Optional.of(session.frozenPlan);
        }
        Optional<MaterialSelectionSnapshot> selection = wandItems.selectedMaterialSnapshot(player);
        if (selection.isEmpty()) {
            return Optional.empty();
        }
        int rotation = wandItems.getRotation(player.getInventory().getItemInOffHand());
        Density density = session.anchor == null
                ? wandItems.getDensity(player.getInventory().getItemInOffHand()) : session.density;
        return derive(player, session, config, selection.get(), rotation, density);
    }

    /**
     * Derive a plan from an injected immutable hotbar-palette snapshot.
     */
    public static Optional<Plan> derive(Player player, GestureSession session, PluginConfig config,
                                        MaterialSelectionSnapshot selection, int rotation) {
        return derive(player, session, config, selection, rotation, session.density);
    }

    /** Derive a plan using the exact frozen density preference. */
    public static Optional<Plan> derive(Player player, GestureSession session, PluginConfig config,
                                        MaterialSelectionSnapshot selection, int rotation,
                                        Density requestedDensity) {
        if (session.frozenPlan != null) {
            return Optional.of(session.frozenPlan);
        }
        Map<Material, BlockData> orientedData = new EnumMap<>(Material.class);

        if (session.anchor == null) {
            Optional<BlockVector> anchor = wouldBeAnchor(player, config); // un-anchored ghost = one cell
            if (anchor.isEmpty()) {
                return Optional.empty();
            }
            World world = player.getWorld();
            BlockVector a = anchor.get();
            Location cell = new Location(world, a.getBlockX(), a.getBlockY(), a.getBlockZ());
            PlannedCell target = targetFor(world, cell, selection, rotation, orientedData);
            Density density = session.form.supportsDensity() ? requestedDensity : Density.SHELL;
            return Optional.of(new Plan(world, session.form, new Dims(1, 1, 1), a,
                    List.of(target), selection, density));
        }

        World world = Bukkit.getWorld(session.worldId);
        if (world == null) {
            return Optional.empty();
        }
        Density density = session.form.supportsDensity() ? requestedDensity : Density.SHELL;
        if (session.form == Form.EXTEND_SURFACE) {
            return deriveExtendedSurface(player, session, config, selection, rotation, world);
        }
        Reading reading = read(player, session, config, density);
        List<BlockVector> offsets;
        try {
            offsets = Expansion.previewCells(session.form, reading.dims(), reading.orientation(),
                    density, config.formLimits,
                    config.formLimits.maxScannedCellsPerPlan() + 1);
        } catch (IllegalArgumentException refused) {
            return Optional.empty();
        }
        List<PlannedCell> cells = new ArrayList<>(offsets.size());
        BlockVector base = reading.effectiveAnchor();
        for (BlockVector off : offsets) {
            Location location = new Location(world,
                    base.getBlockX() + off.getBlockX(),
                    base.getBlockY() + off.getBlockY(),
                    base.getBlockZ() + off.getBlockZ());
            cells.add(targetFor(world, location, selection, rotation, orientedData));
        }
        cells = new ArrayList<>(PlanOrdering.anchorOut(cells, session.anchor));
        return Optional.of(new Plan(world, session.form, reading.dims(), base, session.anchor,
                cells, selection, density));
    }

    private static PlannedCell targetFor(World world, Location location,
                                         MaterialSelectionSnapshot selection, int rotation,
                                         Map<Material, BlockData> orientedData) {
        PrintMaterial material = selection.select(world, location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
        BlockData blockData = orientedData.computeIfAbsent(material.placedBlock(), placed -> {
            BlockData created = BlockOrientation.oriented(placed, rotation);
            if (material.isWater() && created instanceof Levelled water) {
                water.setLevel(0); // source water explicitly, independent of registry defaults
            }
            return created;
        });
        return new PlannedCell(location, material, blockData);
    }

    /** Freeze the current stage into the session on a lock click (design §7.3). */
    public static void applyLock(Player player, GestureSession session, PluginConfig config) {
        if (session.anchor == null || session.frozenPlan != null) {
            return;
        }
        Aim aim = Aim.of(player, config, session.form);
        Orientation o = session.orientation;
        BlockVector anchor = session.anchor;
        Vector inPlane = aim.point(center(anchor), vec(o.n()));
        int offL = cellOffset(inPlane, anchor, o.l());
        int offSV = cellOffset(inPlane, anchor, o.s());
        switch (session.form) {
            case CYLINDER -> {
                int step = Measurement.radiusStep(offL, offSV,
                        config.formLimits.maxSpan(Form.CYLINDER, 0));
                if (new PlanPolicy(config.formLimits).evaluateDimensions(Form.CYLINDER,
                        new Dims(step, 1, 1), session.density).allowed()) {
                    session.lock1 = step;
                }
            }
            case SPHERE -> {
                int step = Measurement.radiusStep(offL, offSV,
                        config.formLimits.maxSpan(Form.SPHERE, 0));
                if (new PlanPolicy(config.formLimits).evaluateDimensions(Form.SPHERE,
                        new Dims(step, 1, 1), session.density).allowed()) {
                    session.lock1 = step;
                }
            }
            case DIAGONAL -> {
                // Lock the tread line's axis (dominant drag) and its width.
                session.firstAxisLateral = Math.abs(offL) >= Math.abs(offSV);
                session.lock1 = session.firstAxisLateral
                        ? clampExtent(offL, config.formLimits.maxSpan(Form.DIAGONAL, 1))
                        : clampExtent(offSV, config.formLimits.maxSpan(Form.DIAGONAL, 1));
            }
            case BOX -> {
                if (session.stage() == 0) {
                    // Lock the drawn line's axis (dominant drag) and its length.
                    session.firstAxisLateral = Math.abs(offL) >= Math.abs(offSV);
                    session.lock1 = session.firstAxisLateral
                            ? clampExtent(offL, config.formLimits.maxSpan(Form.BOX, 0))
                            : clampExtent(offSV, config.formLimits.maxSpan(Form.BOX, 1));
                } else {
                    // Lock the other in-plane axis (the rectangle's width).
                    session.lock2 = session.firstAxisLateral
                            ? clampExtent(offSV, config.formLimits.maxSpan(Form.BOX, 1))
                            : clampExtent(offL, config.formLimits.maxSpan(Form.BOX, 0));
                }
            }
            case WALL, LINE, FLOOR, EXTEND_SURFACE -> { }
        }
    }

    // ------------------------------------------------------------------ reading (design §7.3–§7.6)

    private static Reading read(Player player, GestureSession session, PluginConfig config,
                                Density density) {
        Aim aim = Aim.of(player, config, session.form);
        Orientation o = session.orientation;
        BlockVector anchor = session.anchor;
        Vector inPlane = aim.point(center(anchor), vec(o.n()));
        int offL = cellOffset(inPlane, anchor, o.l());
        int offSV = cellOffset(inPlane, anchor, o.s());

        return switch (session.form) {
            case BOX -> readBox(aim, session, o, anchor, offL, offSV, config.formLimits, density);
            case CYLINDER -> readCylinder(aim, session, o, anchor, offL, offSV,
                    config.formLimits, density);
            case SPHERE -> readSphere(aim, session, o, anchor, offL, offSV,
                    config.formLimits, density);
            case DIAGONAL -> readDiagonal(aim, session, o, anchor, offL, offSV,
                    config.formLimits);
            case WALL -> readWall(aim, o, anchor, config.formLimits);
            case FLOOR -> readFloor(aim, o, anchor, config.formLimits);
            case LINE -> readLine(player, config, o, anchor);
            case EXTEND_SURFACE -> throw new IllegalStateException(
                    "Extend Surface uses connected traversal");
        };
    }

    private static Reading readBox(Aim aim, GestureSession s, Orientation o, BlockVector anchor,
                                   int offL, int offSV, FormLimits limits, Density density) {
        int stage = s.stage();
        int lExtent;
        int svExtent;
        if (stage == 0) {
            // A one-wide line from the anchor along whichever in-plane axis is dragged more
            // (right/left = L, forward/back or up/down = S/V) — "first click is x or z".
            if (Math.abs(offL) >= Math.abs(offSV)) {
                lExtent = clampExtent(offL, limits.maxSpan(Form.BOX, 0));
                svExtent = 1;
            } else {
                lExtent = 1;
                svExtent = clampExtent(offSV, limits.maxSpan(Form.BOX, 1));
            }
        } else {
            // The first lock froze one in-plane axis; the other now widens the line to a rectangle.
            Integer lLock = s.firstAxisLateral ? s.lock1 : s.lock2;
            Integer svLock = s.firstAxisLateral ? s.lock2 : s.lock1;
            lExtent = lLock != null ? lLock : clampExtent(offL, limits.maxSpan(Form.BOX, 0));
            svExtent = svLock != null ? svLock : clampExtent(offSV, limits.maxSpan(Form.BOX, 1));
        }
        BlockVector inPlaneAnchor = shift(anchor, o.l(), Measurement.negativeAnchorShift(lExtent));
        inPlaneAnchor = shift(inPlaneAnchor, o.s(), Measurement.negativeAnchorShift(svExtent));

        int nExtent = 1;
        if (stage >= 2) { // both in-plane axes locked → height tracks along N
            Vector nTarget = aim.point(center(inPlaneAnchor), vec(o.s()));
            int requested = clampExtent(cellOffset(nTarget, inPlaneAnchor, o.n()),
                    limits.maxSpan(Form.BOX, 2));
            nExtent = requested;
        }
        BlockVector effAnchor = shift(inPlaneAnchor, o.n(), Measurement.negativeAnchorShift(nExtent));
        return new Reading(new Dims(Math.abs(lExtent), Math.abs(svExtent), Math.abs(nExtent)), effAnchor, o);
    }

    private static Reading readCylinder(Aim aim, GestureSession s, Orientation o,
                                        BlockVector anchor, int offL, int offSV,
                                        FormLimits limits, Density density) {
        int step = s.lock1 != null ? s.lock1 : Measurement.radiusStep(offL, offSV,
                limits.maxSpan(Form.CYLINDER, 0));
        if (s.lock1 == null) {
            return new Reading(new Dims(step, 1, 1), anchor, o); // circle centered, no anchor shift
        }
        Vector nTarget = aim.point(center(anchor), vec(o.s()));
        int requested = clampExtent(cellOffset(nTarget, anchor, o.n()),
                limits.maxSpan(Form.CYLINDER, 1));
        int nExtent = requested;
        int courses = Math.abs(nExtent);
        BlockVector effAnchor = shift(anchor, o.n(), Measurement.negativeAnchorShift(nExtent));
        return new Reading(new Dims(step, courses, 1), effAnchor, o);
    }

    private static Reading readSphere(Aim aim, GestureSession s, Orientation o,
                                      BlockVector anchor, int offL, int offSV,
                                      FormLimits limits, Density density) {
        int step = s.lock1 != null ? s.lock1 : Measurement.radiusStep(offL, offSV,
                limits.maxSpan(Form.SPHERE, 0));
        if (s.lock1 == null) {
            return new Reading(new Dims(step, 1, 1), anchor, o);
        }
        Vector nTarget = aim.point(center(anchor), vec(o.s()));
        int requested = clampExtent(cellOffset(nTarget, anchor, o.n()),
                limits.maxSpan(Form.SPHERE, 1));
        int signedLength = requested;
        int length = Math.abs(signedLength);
        BlockVector effAnchor = shift(anchor, o.n(), Measurement.negativeAnchorShift(signedLength));
        return new Reading(new Dims(step, length, 1), effAnchor, o);
    }

    private static Reading readDiagonal(Aim aim, GestureSession s, Orientation o,
                                        BlockVector anchor, int offL, int offSV,
                                        FormLimits limits) {
        // Stage 0 draws the tread as a directional line (dominant drag), like the box; the run
        // then climbs perpendicular to the tread, rising one block per step (design §6.2/§7.5).
        boolean treadLateral;
        int treadExtent;
        if (s.stage() == 0) {
            treadLateral = Math.abs(offL) >= Math.abs(offSV);
            treadExtent = treadLateral
                    ? clampExtent(offL, limits.maxSpan(Form.DIAGONAL, 1))
                    : clampExtent(offSV, limits.maxSpan(Form.DIAGONAL, 1));
        } else {
            treadLateral = s.firstAxisLateral;
            treadExtent = s.lock1;
        }
        BlockVector treadAxis = treadLateral ? o.l() : o.s();
        BlockVector perpAxis = treadLateral ? o.s() : o.l();
        BlockVector effAnchor = shift(anchor, treadAxis, Measurement.negativeAnchorShift(treadExtent));

        int run = 1;
        BlockVector climb = perpAxis;
        if (s.stage() >= 1) {
            Vector target = aim.point(center(effAnchor), vec(o.n()));
            int runExtent = clampExtent(cellOffset(target, effAnchor, perpAxis),
                    limits.maxSpan(Form.DIAGONAL, 0));
            run = Math.abs(runExtent);
            if (runExtent < 0) {
                climb = new BlockVector(-perpAxis.getBlockX(), -perpAxis.getBlockY(), -perpAxis.getBlockZ());
            }
        }
        // Effective orientation for §6.2: tread along L', run rises along S'(=climb) and N.
        Orientation eff = new Orientation(treadAxis, climb, o.n(), o.wall(), o.heading());
        return new Reading(new Dims(run, Math.abs(treadExtent), 1), effAnchor, eff);
    }

    private static Reading readWall(Aim aim, Orientation orientation, BlockVector anchor,
                                    FormLimits limits) {
        Vector target = aim.point(center(anchor), vec(orientation.s()));
        BlockVector up = new BlockVector(0, 1, 0);
        int width = clampExtent(cellOffset(target, anchor, orientation.l()),
                limits.maxSpan(Form.WALL, 0));
        int requestedHeight = clampExtent(cellOffset(target, anchor, up),
                limits.maxSpan(Form.WALL, 1));
        int height = requestedHeight;
        BlockVector effective = shift(anchor, orientation.l(), Measurement.negativeAnchorShift(width));
        effective = shift(effective, up, Measurement.negativeAnchorShift(height));
        return new Reading(new Dims(Math.abs(width), Math.abs(height), 1), effective, orientation);
    }

    private static Reading readFloor(Aim aim, Orientation orientation, BlockVector anchor,
                                     FormLimits limits) {
        BlockVector up = new BlockVector(0, 1, 0);
        Vector target = aim.point(center(anchor), vec(up));
        BlockVector a = orientation.l();
        BlockVector b = orientation.s();
        int first = clampExtent(cellOffset(target, anchor, a),
                limits.maxSpan(Form.FLOOR, 0));
        int requestedSecond = clampExtent(cellOffset(target, anchor, b),
                limits.maxSpan(Form.FLOOR, 1));
        int second = requestedSecond;
        BlockVector effective = shift(anchor, a, Measurement.negativeAnchorShift(first));
        effective = shift(effective, b, Measurement.negativeAnchorShift(second));
        return new Reading(new Dims(Math.abs(first), Math.abs(second), 1), effective,
                new Orientation(a, b, up, false, orientation.heading()));
    }

    private static Reading readLine(Player player, PluginConfig config, Orientation orientation,
                                    BlockVector anchor) {
        Vector target = currentAimTarget(player, config);
        int dx = target.getBlockX() - anchor.getBlockX();
        int dy = target.getBlockY() - anchor.getBlockY();
        int dz = target.getBlockZ() - anchor.getBlockZ();
        BlockVector axis = Measurement.dominantAxis(dx, dy, dz);
        int offset = dx * axis.getBlockX() + dy * axis.getBlockY() + dz * axis.getBlockZ();
        // dominantAxis carries the sign, so the inclusive extent is positive along that axis.
        int extent = Math.min(Math.abs(offset) + 1,
                config.formLimits.maxSpan(Form.LINE, 0));
        Orientation line = new Orientation(axis, orientation.s(), orientation.n(),
                orientation.wall(), orientation.heading());
        return new Reading(new Dims(extent, 1, 1), anchor, line);
    }

    /**
     * Bounded breadth-first traversal of the exact connected source face. The traversal itself
     * stops at the configured cell/span/chunk guards, so preview never loads or scans an
     * unbounded wall. Emission remains anchor-out and deterministic.
     */
    private static Optional<Plan> deriveExtendedSurface(
            Player player, GestureSession session, PluginConfig config,
            MaterialSelectionSnapshot selection, int rotation, World world) {
        if (session.sourceBlock == null || session.sourceFace == null
                || session.sourceBlockData == null) {
            return Optional.empty();
        }
        Block source = world.getBlockAt(session.sourceBlock.getBlockX(),
                session.sourceBlock.getBlockY(), session.sourceBlock.getBlockZ());
        if (!source.getBlockData().getAsString().equals(session.sourceBlockData)) {
            return Optional.empty();
        }

        BlockVector normal = new BlockVector(session.sourceFace.getModX(),
                session.sourceFace.getModY(), session.sourceFace.getModZ());
        BlockVector axisA = session.orientation.l();
        BlockVector axisB = Math.abs(normal.getBlockY()) == 1
                ? session.orientation.s() : new BlockVector(0, 1, 0);
        List<BlockVector> neighborAxes = switch (session.surfaceRestriction) {
            case ROW -> List.of(axisA, negate(axisA));
            case COLUMN -> List.of(axisB, negate(axisB));
            case FREE -> List.of(axisA, negate(axisA), axisB, negate(axisB));
        };

        FormLimits limits = config.formLimits;
        List<BlockVector> sources = cachedSurfaceSources(session, world);
        if (sources != null) {
            return extendedSurfacePlan(session, selection, rotation, world, normal, axisA, axisB,
                    sources);
        }

        Deque<BlockVector> frontier = new ArrayDeque<>();
        Set<BlockVector> visited = new HashSet<>();
        sources = new ArrayList<>();
        Set<Long> targetChunks = new LinkedHashSet<>();
        frontier.add(session.sourceBlock);
        visited.add(session.sourceBlock);
        int minA = 0;
        int maxA = 0;
        int minB = 0;
        int maxB = 0;

        while (!frontier.isEmpty() && sources.size() <= limits.maxScannedCellsPerPlan()) {
            BlockVector current = frontier.removeFirst();
            int relX = current.getBlockX() - session.sourceBlock.getBlockX();
            int relY = current.getBlockY() - session.sourceBlock.getBlockY();
            int relZ = current.getBlockZ() - session.sourceBlock.getBlockZ();
            int alongA = relX * axisA.getBlockX() + relY * axisA.getBlockY()
                    + relZ * axisA.getBlockZ();
            int alongB = relX * axisB.getBlockX() + relY * axisB.getBlockY()
                    + relZ * axisB.getBlockZ();
            int nextMinA = Math.min(minA, alongA);
            int nextMaxA = Math.max(maxA, alongA);
            int nextMinB = Math.min(minB, alongB);
            int nextMaxB = Math.max(maxB, alongB);
            if (nextMaxA - nextMinA + 1 > limits.maxSpan(Form.EXTEND_SURFACE, 0)
                    || nextMaxB - nextMinB + 1 > limits.maxSpan(Form.EXTEND_SURFACE, 1)) {
                continue;
            }

            int targetX = current.getBlockX() + normal.getBlockX();
            int targetY = current.getBlockY() + normal.getBlockY();
            int targetZ = current.getBlockZ() + normal.getBlockZ();
            long chunkKey = chunkKey(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16));
            if (!targetChunks.contains(chunkKey)
                    && targetChunks.size() >= limits.maxChunksPerPrint()) {
                continue;
            }

            sources.add(current);
            targetChunks.add(chunkKey);
            minA = nextMinA;
            maxA = nextMaxA;
            minB = nextMinB;
            maxB = nextMaxB;

            for (BlockVector axis : neighborAxes) {
                BlockVector next = new BlockVector(current.getBlockX() + axis.getBlockX(),
                        current.getBlockY() + axis.getBlockY(),
                        current.getBlockZ() + axis.getBlockZ());
                if (!visited.add(next)) {
                    continue;
                }
                int chunkX = Math.floorDiv(next.getBlockX(), 16);
                int chunkZ = Math.floorDiv(next.getBlockZ(), 16);
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                Block neighbor = world.getBlockAt(next.getBlockX(), next.getBlockY(), next.getBlockZ());
                if (neighbor.getBlockData().getAsString().equals(session.sourceBlockData)) {
                    frontier.addLast(next);
                }
            }
        }

        if (sources.isEmpty()) {
            return Optional.empty();
        }
        session.surfaceSources = List.copyOf(sources);
        return extendedSurfacePlan(session, selection, rotation, world, normal, axisA, axisB,
                sources);
    }

    private static Optional<Plan> extendedSurfacePlan(
            GestureSession session, MaterialSelectionSnapshot selection, int rotation,
            World world, BlockVector normal, BlockVector axisA, BlockVector axisB,
            List<BlockVector> sources) {
        Map<Material, BlockData> orientedData = new EnumMap<>(Material.class);
        List<PlannedCell> targets = new ArrayList<>(sources.size());
        int minA = 0;
        int maxA = 0;
        int minB = 0;
        int maxB = 0;
        for (BlockVector cell : sources) {
            int relX = cell.getBlockX() - session.sourceBlock.getBlockX();
            int relY = cell.getBlockY() - session.sourceBlock.getBlockY();
            int relZ = cell.getBlockZ() - session.sourceBlock.getBlockZ();
            int alongA = relX * axisA.getBlockX() + relY * axisA.getBlockY()
                    + relZ * axisA.getBlockZ();
            int alongB = relX * axisB.getBlockX() + relY * axisB.getBlockY()
                    + relZ * axisB.getBlockZ();
            minA = Math.min(minA, alongA);
            maxA = Math.max(maxA, alongA);
            minB = Math.min(minB, alongB);
            maxB = Math.max(maxB, alongB);
            Location location = new Location(world,
                    cell.getBlockX() + normal.getBlockX(),
                    cell.getBlockY() + normal.getBlockY(),
                    cell.getBlockZ() + normal.getBlockZ());
            targets.add(targetFor(world, location, selection, rotation, orientedData));
        }
        Dims dims = new Dims(maxA - minA + 1, maxB - minB + 1, 1);
        List<PlanValidationCell> validation = sources.stream()
                .map(cell -> new PlanValidationCell(cell, session.sourceBlockData))
                .toList();
        return Optional.of(new Plan(world, Form.EXTEND_SURFACE, dims, session.anchor,
                session.anchor, targets, selection, Density.SHELL,
                PlanOptions.ordinary(Form.EXTEND_SURFACE, validation)));
    }

    private static List<BlockVector> cachedSurfaceSources(GestureSession session, World world) {
        if (session.surfaceSources == null || session.surfaceSources.isEmpty()) {
            return null;
        }
        for (BlockVector cell : session.surfaceSources) {
            if (!world.isChunkLoaded(Math.floorDiv(cell.getBlockX(), 16),
                    Math.floorDiv(cell.getBlockZ(), 16))) {
                session.surfaceSources = null;
                return null;
            }
            if (!world.getBlockAt(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ())
                    .getBlockData().getAsString().equals(session.sourceBlockData)) {
                session.surfaceSources = null;
                return null;
            }
        }
        return session.surfaceSources;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static BlockVector negate(BlockVector vector) {
        return new BlockVector(-vector.getBlockX(), -vector.getBlockY(), -vector.getBlockZ());
    }

    private static Vector currentAimTarget(Player player, PluginConfig config) {
        Location eye = player.getEyeLocation();
        double reach = Math.max(config.anchorReach,
                config.formLimits.maxSpan(Form.LINE, 0) + config.anchorReach);
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), reach,
                FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
            Block block = hit.getHitBlock();
            BlockFace face = hit.getHitBlockFace();
            return new Vector(block.getX() + face.getModX() + 0.5,
                    block.getY() + face.getModY() + 0.5,
                    block.getZ() + face.getModZ() + 0.5);
        }
        return eye.toVector().add(eye.getDirection().multiply(reach));
    }

    // ------------------------------------------------------------------ aim helpers (design §7.4)

    private record Aim(Vector eye, Vector direction, double reach) {
        static Aim of(Player player, PluginConfig config, Form form) {
            Location eyeLoc = player.getEyeLocation();
            Dims spans = config.formLimits.maxSpans(form);
            double measurementReach = Math.max(config.anchorReach,
                    Math.max(spans.primary(), Math.max(spans.secondary(), spans.tertiary()))
                            + config.anchorReach);
            return new Aim(eyeLoc.toVector(), eyeLoc.getDirection(), measurementReach);
        }

        /**
         * The aim target in a measurement plane: where the eye ray crosses the plane, bounded
         * to reach. It never snaps to the block under the crosshair and never lets a
         * near-parallel (near-horizontal) look send the crossing to infinity — so terrain steps
         * and horizontal looks can't make the measured extent jump or skip values.
         */
        Vector point(Vector planeOrigin, Vector planeNormal) {
            double denom = direction.dot(planeNormal);
            if (Math.abs(denom) > 1e-4) {
                double t = planeOrigin.clone().subtract(eye).dot(planeNormal) / denom;
                if (t > 0) {
                    return eye.clone().add(direction.clone().multiply(Math.min(t, reach)));
                }
            }
            return eye.clone().add(direction.clone().multiply(reach));
        }
    }

    private record Reading(Dims dims, BlockVector effectiveAnchor, Orientation orientation) {
    }

    private static int cellOffset(Vector target, BlockVector anchor, BlockVector axis) {
        int dx = target.getBlockX() - anchor.getBlockX();
        int dy = target.getBlockY() - anchor.getBlockY();
        int dz = target.getBlockZ() - anchor.getBlockZ();
        return dx * axis.getBlockX() + dy * axis.getBlockY() + dz * axis.getBlockZ();
    }

    private static int clampExtent(int offset, int max) {
        return Measurement.clampExtent(Measurement.extentFromOffset(offset), max);
    }

    private static Vector center(BlockVector cell) {
        return new Vector(cell.getBlockX() + 0.5, cell.getBlockY() + 0.5, cell.getBlockZ() + 0.5);
    }

    private static Vector vec(BlockVector v) {
        return new Vector(v.getBlockX(), v.getBlockY(), v.getBlockZ());
    }

    private static BlockVector shift(BlockVector base, BlockVector axis, int delta) {
        return new BlockVector(
                base.getBlockX() + delta * axis.getBlockX(),
                base.getBlockY() + delta * axis.getBlockY(),
                base.getBlockZ() + delta * axis.getBlockZ());
    }
}
