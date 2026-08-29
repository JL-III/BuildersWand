package com.playtheatria.buildersWand.gesture;

import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.Density;
import com.playtheatria.buildersWand.form.Orientation;
import com.playtheatria.buildersWand.form.SurfaceRestriction;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockVector;

import java.util.UUID;
import java.util.List;

/**
 * Mutable per-player gesture state (design §7), in memory only. {@code lock1}/{@code lock2}
 * hold the frozen signed extents/steps of the locked stages; {@link #stage()} is derived
 * from which are set.
 */
public final class GestureSession {

    public Form form;
    public Density density = Density.DEFAULT;
    public SurfaceRestriction surfaceRestriction = SurfaceRestriction.DEFAULT;
    /** Permanent identity of the exact wand that created this gesture. */
    public String wandId;
    public UUID worldId;
    public BlockVector anchor;
    /** Exact source block/face for Extend Surface's connected traversal. */
    public BlockVector sourceBlock;
    public BlockFace sourceFace;
    public String sourceBlockData;
    /** Cached bounded membership for Extend Surface; exact states are revalidated before reuse. */
    public List<BlockVector> surfaceSources;
    public Orientation orientation;
    public Integer lock1;
    public Integer lock2;
    /** Box only: whether the first-locked in-plane axis was lateral (L) rather than away (S/V). */
    public boolean firstAxisLateral;
    public int stage() {
        if (lock1 == null) {
            return 0;
        }
        if (lock2 == null) {
            return 1;
        }
        return 2;
    }
}
