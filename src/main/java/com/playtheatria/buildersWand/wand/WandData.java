package com.playtheatria.buildersWand.wand;

import org.bukkit.inventory.ItemStack;

public record WandData(ItemStack originalItem, WandMode mode, WandDimensions dimensions) {}

