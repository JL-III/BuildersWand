package com.playtheatria.buildersWand.wave;

import org.bukkit.Location;

/** Why a wave stopped, carrying its §13 reason phrase. */
public enum StopReason {
    BLOCK_IN_WAY("a block appeared in the way at %d, %d, %d", true),
    PERMISSION_LOST("build permission was lost at %d, %d, %d", true),
    BODY_STUCK("a body could not be moved clear", false),
    OUT_OF_MATERIAL("you ran out of materials", false),
    PLAYER_QUIT("you left the game", false),
    SERVER_STOPPING("the server is stopping", false);

    private final String template;
    private final boolean locational;

    StopReason(String template, boolean locational) {
        this.template = template;
        this.locational = locational;
    }

    /** The §13 reason phrase; {@code at} supplies the coordinates for the locational reasons. */
    public String phrase(Location at) {
        if (locational && at != null) {
            return String.format(template, at.getBlockX(), at.getBlockY(), at.getBlockZ());
        }
        return template;
    }
}
