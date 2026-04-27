package net.weyne1.randomcrafts.core.graph;

import net.weyne1.randomcrafts.core.item.CoreItem;
import net.weyne1.randomcrafts.core.recipe.CoreRecipe;

import java.util.*;

public class RecipeGraph {
    private final Map<String, List<CoreRecipe>> recipesByOutput = new HashMap<>();
    private final Map<String, CoreItem> coreItemById = new HashMap<>();

    /* ===================== ITEMS ===================== */

    public CoreItem getCoreItemById(String id) {
        return coreItemById.get(id);
    }

    public Map<String, CoreItem> getCoreItemIdMap() {
        return coreItemById;
    }

    public void setTier(CoreItem item, int tier) {
        CoreItem old = coreItemById.get(item.id());
        if (old == null) return;

        CoreItem updated = new CoreItem(
                old.id(),
                old.name(),
                tier,
                old.vanillaItem()
        );

        coreItemById.put(item.id(), updated);

        List<CoreRecipe> recipes = recipesByOutput.get(item.id());
        if (recipes == null) return;

        List<CoreRecipe> updatedList = new ArrayList<>();

        for (CoreRecipe recipe : recipes) {
            updatedList.add(new CoreRecipe(
                    recipe.id(),
                    updated,
                    recipe.inputs(),
                    recipe.outputCount()
            ));
        }

        recipesByOutput.put(item.id(), updatedList);
    }

    /* ===================== RECIPES ===================== */

    /**
     * Добавляет рецепт.
     * Если рецепт с таким output уже есть — ПЕРЕЗАПИСЫВАЕТ его.
     */
    public void addRecipe(CoreRecipe recipe) {
        if (recipe == null) return;

        CoreItem output = normalizeItem(recipe.output());
        List<CoreItem> inputs = recipe.inputs().stream()
                .map(this::normalizeItem)
                .toList();

        CoreRecipe normalized = new CoreRecipe(recipe.id(), output, inputs, recipe.outputCount());

        recipesByOutput
                .computeIfAbsent(output.id(), k -> new ArrayList<>())
                .add(normalized);
    }

    public List<CoreRecipe> getAllRecipes() {
        return recipesByOutput.values()
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    /* ===================== INTERNAL ===================== */

    /**
     * Гарантирует, что для каждого itemId в графе
     * существует ровно один CoreItem-инстанс.
     */
    private CoreItem normalizeItem(CoreItem item) {
        CoreItem existing = coreItemById.get(item.id());
        if (existing != null) return existing;

        coreItemById.put(item.id(), item);
        return item;
    }
}