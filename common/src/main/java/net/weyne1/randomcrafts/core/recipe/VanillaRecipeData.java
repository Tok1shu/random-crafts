package net.weyne1.randomcrafts.core.recipe;

import net.minecraft.world.item.Item;

import java.util.List;

public record VanillaRecipeData(
        String id,
        Item output,
        int outputCount,
        List<Item> inputs,
        boolean isShapeless,
        List<String> patternLayout
) {}
