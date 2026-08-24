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

    private static final int STRETCH_FALLBACK = 64; // design §7.4 step 3

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
        List<BlockVector> offsets = Expansion.cells(session.form, reading.dims(), session.orientation);
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

    /** The signed extent/step to freeze for the current stage on a lock click (design §7.3). */
    public static Optional<Integer> lockValue(Player player, GestureSession session, PluginConfig config) {
        if (session.anchor == null) {
            return Optional.empty();
        }
        Aim aim = Aim.of(player, config);
        Orientation o = session.orientation;
        BlockVector anchor = session.anchor;
        Vector inPlane = aim.point(center(anchor), vec(o.n()));
        int offL = cellOffset(inPlane, anchor, o.l());
        int offSV = cellOffset(inPlane, anchor, o.s());
        int stage = session.stage();
        return Optional.of(switch (session.form) {
            case CYLINDER -> Measurement.radiusStep(offL, offSV, 9);
            case SPHERE -> Measurement.radiusStep(offL, offSV, 7);
            case DIAGONAL -> clampExtent(offL, 5);
            case BOX -> o.wall()
                    ? (stage == 0 ? clampExtent(offL, 8) : clampExtent(offSV, 8))
                    : (stage == 0 ? clampExtent(offSV, 8) : clampExtent(offL, 8));
        });
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
            case DIAGONAL -> readDiagonal(aim, session, o, anchor, offL);
        };
    }

    private static Reading readBox(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL, int offSV) {
        // Both in-plane extents live-track the aim until locked (design §7.3): the footprint
        // is a full rectangle from the first stage, not a one-wide line.
        int lExtent;
        int svExtent;
        if (o.wall()) {
            lExtent = s.lock1 != null ? s.lock1 : clampExtent(offL, 8);
            svExtent = s.lock2 != null ? s.lock2 : clampExtent(offSV, 8);
        } else {
            svExtent = s.lock1 != null ? s.lock1 : clampExtent(offSV, 8);
            lExtent = s.lock2 != null ? s.lock2 : clampExtent(offL, 8);
        }
        BlockVector inPlaneAnchor = shift(anchor, o.l(), Measurement.negativeAnchorShift(lExtent));
        inPlaneAnchor = shift(inPlaneAnchor, o.s(), Measurement.negativeAnchorShift(svExtent));

        int nExtent = 1;
        if (s.lock1 != null && s.lock2 != null) {
            Vector nTarget = aim.point(center(inPlaneAnchor), vec(o.s()));
            nExtent = clampExtent(cellOffset(nTarget, inPlaneAnchor, o.n()), 8);
        }
        BlockVector effAnchor = shift(inPlaneAnchor, o.n(), Measurement.negativeAnchorShift(nExtent));
        return new Reading(new Dims(Math.abs(lExtent), Math.abs(svExtent), Math.abs(nExtent)), effAnchor);
    }

    private static Reading readCylinder(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL, int offSV) {
        int step = s.lock1 != null ? s.lock1 : Measurement.radiusStep(offL, offSV, 9);
        if (s.lock1 == null) {
            return new Reading(new Dims(step, 1, 1), anchor); // circle centered, no anchor shift
        }
        Vector nTarget = aim.point(center(anchor), vec(o.s()));
        int nExtent = clampExtent(cellOffset(nTarget, anchor, o.n()), 8);
        int courses = Math.abs(nExtent);
        BlockVector effAnchor = shift(anchor, o.n(), Measurement.negativeAnchorShift(nExtent));
        return new Reading(new Dims(step, courses, 1), effAnchor);
    }

    private static Reading readSphere(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL, int offSV) {
        int step = s.lock1 != null ? s.lock1 : Measurement.radiusStep(offL, offSV, 7);
        if (s.lock1 == null) {
            return new Reading(new Dims(step, 1, 1), anchor);
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
        return new Reading(new Dims(step, length, 1), effAnchor);
    }

    private static Reading readDiagonal(Aim aim, GestureSession s, Orientation o, BlockVector anchor, int offL) {
        int widthExtent = s.lock1 != null ? s.lock1 : clampExtent(offL, 5);
        BlockVector inPlaneAnchor = shift(anchor, o.l(), Measurement.negativeAnchorShift(widthExtent));
        int run = 1;
        if (s.lock1 != null) {
            Vector nTarget = aim.point(center(inPlaneAnchor), vec(o.s()));
            int offN = cellOffset(nTarget, inPlaneAnchor, o.n());
            run = Math.min(8, Math.abs(Measurement.extentFromOffset(offN)));
        }
        return new Reading(new Dims(run, Math.abs(widthExtent), 1), inPlaneAnchor);
    }

    // ------------------------------------------------------------------ aim helpers (design §7.4)

    private record Aim(Vector eye, Vector direction, RayTraceResult hit) {
        static Aim of(Player player, PluginConfig config) {
            Location eye = player.getEyeLocation();
            RayTraceResult hit = player.rayTraceBlocks(config.anchorReach, FluidCollisionMode.NEVER);
            return new Aim(eye.toVector(), eye.getDirection(), hit);
        }

        /** The aim target point for a measurement plane (design §7.4 priority order). */
        Vector point(Vector planeOrigin, Vector planeNormal) {
            if (hit != null && hit.getHitBlock() != null) {
                Block block = hit.getHitBlock();
                return new Vector(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
            }
            double denom = direction.dot(planeNormal);
            if (Math.abs(denom) > 1e-6) {
                double t = planeOrigin.clone().subtract(eye).dot(planeNormal) / denom;
                if (t > 0) {
                    return eye.clone().add(direction.clone().multiply(t));
                }
            }
            Vector projected = direction.clone().subtract(planeNormal.clone().multiply(direction.dot(planeNormal)));
            if (projected.lengthSquared() > 1e-9) {
                return planeOrigin.clone().add(projected.normalize().multiply(STRETCH_FALLBACK));
            }
            return planeOrigin.clone();
        }
    }

    private record Reading(Dims dims, BlockVector effectiveAnchor) {
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
