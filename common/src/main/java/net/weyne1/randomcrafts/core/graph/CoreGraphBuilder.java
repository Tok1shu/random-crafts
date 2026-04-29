package net.weyne1.randomcrafts.core.graph;

import net.minecraft.core.registries.BuiltInRegistries;
import net.weyne1.randomcrafts.core.item.CoreItem;
import net.weyne1.randomcrafts.core.recipe.CoreRecipe;
import net.minecraft.world.item.Item;
import net.weyne1.randomcrafts.core.recipe.VanillaRecipeData;

import java.util.*;

public class CoreGraphBuilder {

    public static RecipeGraph build(List<VanillaRecipeData> vanillaRecipes) {
        RecipeGraph graph = new RecipeGraph();
        Map<Item, CoreItem> coreItems = new LinkedHashMap<>();

        // 1. Собираем все уникальные предметы
        Set<Item> allItemsSet = new HashSet<>();
        for (VanillaRecipeData vr : vanillaRecipes) {
            allItemsSet.add(vr.output());
            allItemsSet.addAll(vr.inputs());
        }

        // 2. Сортируем их отдельно
        List<Item> sortedItems = new ArrayList<>(allItemsSet);
        sortedItems.sort(Comparator.comparing(item ->
                BuiltInRegistries.ITEM.getKey(item).toString()));

        // 3. Создаем CoreItem в строго определенном порядке
        for (Item i : sortedItems) {
            coreItems.put(i, new CoreItem(i.toString(), i.getDescriptionId(), -1, i));
        }

        // 4. Добавляем рецепты
        for (VanillaRecipeData vr : vanillaRecipes) {
            CoreItem output = coreItems.get(vr.output());
            List<CoreItem> inputs = new ArrayList<>();
            for (Item inputItem : vr.inputs()) {
                inputs.add(coreItems.get(inputItem));
            }
            graph.addRecipe(new CoreRecipe(vr.id(), output, inputs, vr.outputCount(), vr.isShapeless(), vr.patternLayout()));
        }

        return graph;
    }
}