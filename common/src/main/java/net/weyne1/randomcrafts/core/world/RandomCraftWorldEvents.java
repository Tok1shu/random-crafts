package net.weyne1.randomcrafts.core.world;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> dispatcher.register(Commands.literal("rc")
                .then(Commands.literal("generate")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> runGenerate(context, context.getSource().getLevel().getSeed()))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(context -> runGenerate(context, LongArgumentType.getLong(context, "seed")))
                        )
                )
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(RandomCraftWorldEvents::runClear)
                )
                .then(Commands.literal("seed")
                        .requires(source -> true)
                        .executes(RandomCraftWorldEvents::runGetSeed)
                )
        ));
    }

    private static int runGenerate(CommandContext<CommandSourceStack> context, long seed) {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel world = context.getSource().getLevel();

        context.getSource().sendSuccess(() -> Component.translatable("message.random_crafts.generate",
                Component.literal(String.valueOf(seed)).withStyle(ChatFormatting.GOLD)), true);

        world.getGameRules().getRule(RandomCraftsGameRules.RANDOMIZE_CRAFTS).set(true, server);
        generateRandomCrafts(server, world, seed);

        RandomCraftsState state = RandomCraftsState.get(world);
        state.applied = true;
        state.usedSeed = seed;
        state.setDirty();

        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        return 1;
    }

    private static int runClear(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel world = context.getSource().getLevel();
        File worldFolder = server.getWorldPath(LevelResource.ROOT).toFile();

        RandomCraftsDatapack.clear(worldFolder);
        world.getGameRules().getRule(RandomCraftsGameRules.RANDOMIZE_CRAFTS).set(false, server);

        RandomCraftsState state = RandomCraftsState.get(world);
        state.applied = false;
        state.setDirty();

        context.getSource().sendSuccess(() -> Component.translatable("message.random_crafts.clear"), true);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        return 1;
    }

    private static int runGetSeed(CommandContext<CommandSourceStack> context) {
        RandomCraftsState state = RandomCraftsState.get(context.getSource().getLevel());
        if (!state.applied) {
            context.getSource().sendFailure(Component.translatable("message.random_crafts.no_active"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("message.random_crafts.get_seed",
                Component.literal(String.valueOf(state.usedSeed)).withStyle(ChatFormatting.GOLD)), false);
        return 1;
    }

    private static void onServerStarted(MinecraftServer server) {
        ServerLevel world = server.overworld();
        boolean enabled = world.getGameRules().getBoolean(RandomCraftsGameRules.RANDOMIZE_CRAFTS);
        RandomCraftsState state = RandomCraftsState.get(world);

        if (enabled && !state.applied) {
            LOGGER.info("Applying RandomCraft for the first time via GameRule");
            long seed = world.getSeed();
            generateRandomCrafts(server, world, seed);
            state.applied = true;
            state.usedSeed = seed;
            state.setDirty();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        }
    }

    private static void generateRandomCrafts(MinecraftServer server, ServerLevel world, long seed) {
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

        vanillaRecipes.sort(Comparator.comparing(VanillaRecipeData::id));
        RecipeGraph graph = CoreGraphBuilder.build(vanillaRecipes);

        Map<Item, Integer> tierMap = TierCalculator.calculateTier(vanillaRecipes);
        graph.getCoreItemIdMap().values().forEach(coreItem -> {
            Integer tier = tierMap.get(coreItem.vanillaItem());
            graph.setTier(coreItem, Objects.requireNonNullElse(tier, 0));
        });

        RecipeGenerator generator = new RecipeGenerator(graph, seed);
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