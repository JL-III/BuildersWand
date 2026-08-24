package com.playtheatria.buildersWand.gesture;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Expansion;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.Measurement;
import com.playtheatria.buildersWand.form.Orientation;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.Plan;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one place live plans are derived from aim (design §7.3–§7.6), shared by the gesture
 * listener and the ghost service. Pure of side effects; reads only the player's aim, offhand,
 * and world.
 *
 * <p>Diagonal note: the core §6.2 geometry is faithful (tread width along L with a
 * negative-width mirror, run along N); the §7.5 tread-axis pivot and backward half-turn are
 * not replicated (they require the voxels-slim measurement source and a mutable orientation).
 */
public final class LivePlan {

    private LivePlan() {
    }

    /** The cell the first click would anchor (aimed block + face normal), or empty. */
    public static Optional<BlockVector> wouldBeAnchor(Player player, PluginConfig config) {
        RayTraceResult hit = player.rayTraceBlocks(config.anchorReach, FluidCollisionMode.NEVER);
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
        Optional<Material> material = wandItems.selectedMaterial(player);
        if (material.isEmpty()) {
            return Optional.empty();
        }
        if (session.anchor == null) {
            Optional<BlockVector> anchor = wouldBeAnchor(player, config); // un-anchored ghost = one cell
            if (anchor.isEmpty()) {
                return Optional.empty();
            }
            World world = player.getWorld();
            BlockVector a = anchor.get();
            Location cell = new Location(world, a.getBlockX(), a.getBlockY(), a.getBlockZ());
            return Optional.of(new Plan(world, session.form, new Dims(1, 1, 1), a, List.of(cell), material.get()));
        }

        World world = Bukkit.getWorld(session.worldId);
        if (world == null) {
            return Optional.empty();
        }
        Reading reading = read(player, session, config);
        List<BlockVector> offsets = Expansion.cells(session.form, reading.dims(), reading.orientation());
        List<Location> cells = new ArrayList<>(offsets.size());
        BlockVector base = reading.effectiveAnchor();
        for (BlockVector off : offsets) {
            cells.add(new Location(world,
                    base.getBlockX() + off.getBlockX(),
                    base.getBlockY() + off.getBlockY(),
                    base.getBlockZ() + off.getBlockZ()));
        }
        return Optional.of(new Plan(world, session.form, reading.dims(), base, cells, material.get()));
    }

    /** Freeze the current stage into the session on a lock click (design §7.3). */
    public static void applyLock(Player player, GestureSession session, PluginConfig config) {
        if (session.anchor == null) {
            return;
        }
        Aim aim = Aim.of(player, config);
        Orientation o = session.orientation;
        BlockVector anchor = session.anchor;
        Vector inPlane = aim.point(center(anchor), vec(o.n()));
        int offL = cellOffset(inPlane, anchor, o.l());
        int offSV = cellOffset(inPlane, anchor, o.s());
        switch (session.form) {
            case CYLINDER -> session.lock1 = Measurement.radiusStep(offL, offSV, 9);
            case SPHERE -> session.lock1 = Measurement.radiusStep(offL, offSV, 7);
            case DIAGONAL -> {
                // Lock the tread line's axis (dominant drag) and its width.
                session.firstAxisLateral = Math.abs(offL) >= Math.abs(offSV);
                session.lock1 = session.firstAxisLateral ? clampExtent(offL, 5) : clampExtent(offSV, 5);
            }
            case BOX -> {
                if (session.stage() == 0) {
                    // Lock the drawn line's axis (dominant drag) and its length.
                    session.firstAxisLateral = Math.abs(offL) >= Math.abs(offSV);
                    session.lock1 = session.firstAxisLateral ? clampExtent(offL, 8) : clampExtent(offSV, 8);
                } else {
                    // Lock the other in-plane axis (the rectangle's width).
                    session.lock2 = session.firstAxisLateral ? clampExtent(offSV, 8) : clampExtent(offL, 8);
                }
            }
        }
    }

    // ------------------------------------------------------------------ reading (design §7.3–§7.6)

    private static Reading read(Player player, GestureSession session, PluginConfig config) {
        Aim aim = Aim.of(player, config);
        Orientation o = session.orientation;
        BlockVector anchor = session.anchor;
        Vector inPlane = aim.point(center(anchor), vec(o.n()));
        int offL = cellOffset(inPlane, anchor, o.l());
        int offSV = cellOffset(inPlane, anchor, o.s());

        return switch (session.form) {
            case BOX -> readBox(aim, session, o, anchor, offL, offSV);
            case CYLINDER -> readCylinder(aim, session, o, anchor, offL, offSV);
            case SPHERE -> readSphere(aim, session, o, anchor, offL, offSV);
            case DIAGONAL -> readDiagonal(aim, session, o, anchor, offL, offSV);
        };
    }

    private static Reading readBox(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL, int offSV) {
        int stage = s.stage();
        int lExtent;
        int svExtent;
        if (stage == 0) {
            // A one-wide line from the anchor along whichever in-plane axis is dragged more
            // (right/left = L, forward/back or up/down = S/V) — "first click is x or z".
            if (Math.abs(offL) >= Math.abs(offSV)) {
                lExtent = clampExtent(offL, 8);
                svExtent = 1;
            } else {
                lExtent = 1;
                svExtent = clampExtent(offSV, 8);
            }
        } else {
            // The first lock froze one in-plane axis; the other now widens the line to a rectangle.
            Integer lLock = s.firstAxisLateral ? s.lock1 : s.lock2;
            Integer svLock = s.firstAxisLateral ? s.lock2 : s.lock1;
            lExtent = lLock != null ? lLock : clampExtent(offL, 8);
            svExtent = svLock != null ? svLock : clampExtent(offSV, 8);
        }
        BlockVector inPlaneAnchor = shift(anchor, o.l(), Measurement.negativeAnchorShift(lExtent));
        inPlaneAnchor = shift(inPlaneAnchor, o.s(), Measurement.negativeAnchorShift(svExtent));

        int nExtent = 1;
        if (stage >= 2) { // both in-plane axes locked → height tracks along N
            Vector nTarget = aim.point(center(inPlaneAnchor), vec(o.s()));
            nExtent = clampExtent(cellOffset(nTarget, inPlaneAnchor, o.n()), 8);
        }
        BlockVector effAnchor = shift(inPlaneAnchor, o.n(), Measurement.negativeAnchorShift(nExtent));
        return new Reading(new Dims(Math.abs(lExtent), Math.abs(svExtent), Math.abs(nExtent)), effAnchor, o);
    }

    private static Reading readCylinder(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL, int offSV) {
        int step = s.lock1 != null ? s.lock1 : Measurement.radiusStep(offL, offSV, 9);
        if (s.lock1 == null) {
            return new Reading(new Dims(step, 1, 1), anchor, o); // circle centered, no anchor shift
        }
        Vector nTarget = aim.point(center(anchor), vec(o.s()));
        int nExtent = clampExtent(cellOffset(nTarget, anchor, o.n()), 8);
        int courses = Math.abs(nExtent);
        BlockVector effAnchor = shift(anchor, o.n(), Measurement.negativeAnchorShift(nExtent));
        return new Reading(new Dims(step, courses, 1), effAnchor, o);
    }

    private static Reading readSphere(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL, int offSV) {
        int step = s.lock1 != null ? s.lock1 : Measurement.radiusStep(offL, offSV, 7);
        if (s.lock1 == null) {
            return new Reading(new Dims(step, 1, 1), anchor, o);
        }
        Vector nTarget = aim.point(center(anchor), vec(o.s()));
        int nExtent = clampExtent(cellOffset(nTarget, anchor, o.n()), 8);
        int length = Math.abs(nExtent);
        // Capsule walk-down (design §7.6): shrink length instead of vanishing over the cap.
        while (length > 1 && Expansion.cellCount(Form.SPHERE, new Dims(step, length, 1)) > Expansion.MAX_WAVE_CELLS) {
            length--;
        }
        int signedLength = nExtent < 0 ? -length : length;
        BlockVector effAnchor = shift(anchor, o.n(), Measurement.negativeAnchorShift(signedLength));
        return new Reading(new Dims(step, length, 1), effAnchor, o);
    }

    private static Reading readDiagonal(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL, int offSV) {
        // Stage 0 draws the tread as a directional line (dominant drag), like the box; the run
        // then climbs perpendicular to the tread, rising one block per step (design §6.2/§7.5).
        boolean treadLateral;
        int treadExtent;
        if (s.stage() == 0) {
            treadLateral = Math.abs(offL) >= Math.abs(offSV);
            treadExtent = treadLateral ? clampExtent(offL, 5) : clampExtent(offSV, 5);
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
            int runExtent = clampExtent(cellOffset(target, effAnchor, perpAxis), 8);
            run = Math.abs(runExtent);
            if (runExtent < 0) {
                climb = new BlockVector(-perpAxis.getBlockX(), -perpAxis.getBlockY(), -perpAxis.getBlockZ());
            }
        }
        // Effective orientation for §6.2: tread along L', run rises along S'(=climb) and N.
        Orientation eff = new Orientation(treadAxis, climb, o.n(), o.wall(), o.heading());
        return new Reading(new Dims(run, Math.abs(treadExtent), 1), effAnchor, eff);
    }

    // ------------------------------------------------------------------ aim helpers (design §7.4)

    private record Aim(Vector eye, Vector direction, double reach) {
        static Aim of(Player player, PluginConfig config) {
            Location eyeLoc = player.getEyeLocation();
            return new Aim(eyeLoc.toVector(), eyeLoc.getDirection(), config.anchorReach);
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
