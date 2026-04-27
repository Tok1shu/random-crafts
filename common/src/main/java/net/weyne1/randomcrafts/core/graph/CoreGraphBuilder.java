package net.weyne1.randomcrafts.core.graph;

import net.weyne1.randomcrafts.core.item.CoreItem;
import net.weyne1.randomcrafts.core.recipe.CoreRecipe;
import net.minecraft.world.item.Item;
import net.weyne1.randomcrafts.core.recipe.VanillaRecipeData;

import java.util.*;

public class CoreGraphBuilder {

    public static RecipeGraph build(List<VanillaRecipeData> vanillaRecipes) {
        RecipeGraph graph = new RecipeGraph();
        Map<Item, CoreItem> coreItems = new HashMap<>();

        // CoreItem для output
        for (VanillaRecipeData vr : vanillaRecipes) {
            coreItems.putIfAbsent(
                    vr.output(),
                    new CoreItem(vr.output().toString(), vr.output().getDescriptionId(), -1, vr.output())
            );
        }

        // CoreItem для inputs
        for (VanillaRecipeData vr : vanillaRecipes) {
            for (Item i : vr.inputs()) {
                coreItems.putIfAbsent(
                        i,
                        new CoreItem(i.toString(), i.getDescriptionId(), -1, i)
                );
            }
        }

        // CoreRecipe с outputCount
        for (VanillaRecipeData vr : vanillaRecipes) {
            CoreItem output = coreItems.get(vr.output());

            List<CoreItem> inputs = vr.inputs().stream()
                    .map(coreItems::get)
                    .toList();

            graph.addRecipe(new CoreRecipe(vr.id(), output, inputs, vr.outputCount()));
        }

        return graph;
    }
}