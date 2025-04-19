package com.playtheatria.buildersWand.wand;

import org.bukkit.inventory.ItemStack;

public record ParsedWandData(ItemStack originalItem, WandMode mode, WandDimensions dimensions) {}

