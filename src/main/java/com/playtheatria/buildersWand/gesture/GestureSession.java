package com.playtheatria.buildersWand.gesture;

import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.Orientation;
import org.bukkit.util.BlockVector;

import java.util.UUID;

/**
 * Mutable per-player gesture state (design §7), in memory only. {@code lock1}/{@code lock2}
 * hold the frozen signed extents/steps of the locked stages; {@link #stage()} is derived
 * from which are set.
 */
public final class GestureSession {

    public Form form;
    public UUID worldId;
    public BlockVector anchor;
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
