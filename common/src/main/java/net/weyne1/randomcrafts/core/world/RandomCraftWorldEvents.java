package net.weyne1.randomcrafts.core.world;

import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.storage.LevelResource;
import net.weyne1.randomcrafts.RandomCraftsDatapack;
import net.weyne1.randomcrafts.RandomCraftsGameRules;
import net.weyne1.randomcrafts.RandomCraftsState;
import net.weyne1.randomcrafts.core.generator.RecipeGenerator;
import net.weyne1.randomcrafts.core.graph.CoreGraphBuilder;
import net.weyne1.randomcrafts.core.graph.RecipeGraph;
import net.weyne1.randomcrafts.core.recipe.CoreRecipe;
import net.weyne1.randomcrafts.core.recipe.VanillaRecipeData;
import net.weyne1.randomcrafts.core.tier.TierCalculator;

import java.io.File;
import java.util.*;

import static net.weyne1.randomcrafts.RandomCrafts.LOGGER;

public class RandomCraftWorldEvents {

    public static void init() {
        LifecycleEvent.SERVER_STARTED.register(RandomCraftWorldEvents::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        ServerLevel world = server.overworld();

        boolean enabled = world.getGameRules().getBoolean(RandomCraftsGameRules.RANDOMIZE_CRAFTS);

        if (!enabled) {
            LOGGER.info("RandomCraft disabled via gamerule");
            return;
        }

        RandomCraftsState state = RandomCraftsState.get(world);

        if (state.applied) {
            LOGGER.info("RandomCraft already applied for this world");
            return;
        }

        LOGGER.info("Applying RandomCraft for the first time");

        generateRandomCrafts(server, world);

        state.applied = true;
        state.setDirty();

        LOGGER.info("Reloading datapacks");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
    }

    private static void generateRandomCrafts(MinecraftServer server, ServerLevel world) {
        File worldFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        RecipeManager recipeManager = world.getRecipeManager();

        LOGGER.info("Extracting original recipes...");

        List<VanillaRecipeData> vanillaRecipes = new ArrayList<>();

        for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            ResourceLocation recipeId = holder.id();
            CraftingRecipe recipe = holder.value();

            ItemStack result = recipe.getResultItem(world.registryAccess());

            if (result.isEmpty() || result.is(Items.AIR)) continue;

            vanillaRecipes.add(new VanillaRecipeData(
                    recipeId.toString(),
                    result.getItem(),
                    result.getCount(),
                    getIngredientsList(recipe)
            ));
        }

        LOGGER.info("Extracted {} original recipes", vanillaRecipes.size());

        RecipeGraph graph = CoreGraphBuilder.build(vanillaRecipes);

        Map<Item, Integer> tierMap = TierCalculator.calculateTier(vanillaRecipes);
        graph.getCoreItemIdMap().values().forEach(coreItem -> {
            Integer tier = tierMap.get(coreItem.vanillaItem());
            graph.setTier(coreItem, Objects.requireNonNullElse(tier, 0));
        });

        RecipeGenerator generator = new RecipeGenerator(graph, world.getSeed());
        int generated = 0;

        for (CoreRecipe original : graph.getAllRecipes()) {
            CoreRecipe random = generator.generateRandomRecipe(original);
            if (random == null || random.inputs().isEmpty()) continue;

            graph.addRecipe(random);
            generated++;
        }

        LOGGER.info("Generated {} random recipes", generated);

        RandomCraftsDatapack.generate(worldFolder, graph);
    }

    private static List<Item> getIngredientsList(CraftingRecipe recipe) {
        List<Item> inputs = new ArrayList<>();

        for (Ingredient ing : recipe.getIngredients()) {
            ItemStack[] stacks = ing.getItems();
            if (stacks.length > 0 && !stacks[0].is(Items.AIR)) {
                inputs.add(stacks[0].getItem());
            }
        }

        return inputs;
    }
}