package net.weyne1.randomcrafts.core.world;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.weyne1.randomcrafts.core.generator.GenerationSettings;
import net.weyne1.randomcrafts.core.graph.CoreGraphBuilder;
import net.weyne1.randomcrafts.core.graph.RecipeGraph;
import net.weyne1.randomcrafts.core.recipe.CoreRecipe;
import net.weyne1.randomcrafts.core.recipe.VanillaRecipeData;
import net.weyne1.randomcrafts.core.tier.TierCalculator;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

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
        File worldFolder = server.getWorldPath(LevelResource.ROOT).toFile();

        boolean hasPreviousData = RandomCraftsDatapack.exists(worldFolder);

        Runnable generationTask = () -> {
            generateRandomCrafts(server, world, seed);

            RandomCraftsState state = RandomCraftsState.get(world);
            state.applied = true;
            state.usedSeed = seed;
            state.setDirty();

            var rule = world.getGameRules().getRule(RandomCraftsGameRules.RANDOMIZE_CRAFTS);
            if (!rule.get()) rule.set(true, null);

            context.getSource().sendSuccess(() -> Component.translatable("message.random_crafts.generate_success",
                    Component.literal(String.valueOf(seed)).withStyle(ChatFormatting.GOLD)), true);

            // Второй (или единственный) релоад: применяет новые рецепты
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        };

        if (hasPreviousData) {
            context.getSource().sendSuccess(() -> Component.translatable("message.random_crafts.generate_started"), true);
            RandomCraftsDatapack.clear(worldFolder);
            // Релоад нужен, чтобы выгрузить старое
            server.reloadResources(server.getPackRepository().getSelectedIds()).thenAccept(v -> server.execute(generationTask));
        } else {
            // Датапака не было, можно генерить сразу без лишнего релоада
            context.getSource().sendSuccess(() -> Component.translatable("message.random_crafts.short_generate_started"), true);
            generationTask.run();
        }

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
            LOGGER.info("Applying RandomCraft for the first time via GameRule for world: {}", world.dimension().location());
            long seed = world.getSeed();
            generateRandomCrafts(server, world, seed);
            state.applied = true;
            state.usedSeed = seed;
            state.setDirty();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        }
    }

    private static void generateRandomCrafts(MinecraftServer server, ServerLevel world, long seed) {
        GenerationSettings settings = GenerationSettings.fromWorld(world);
        File worldFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        RecipeManager recipeManager = world.getRecipeManager();

        LOGGER.info("Extracting original recipes...");

        List<VanillaRecipeData> vanillaRecipes = new ArrayList<>();

        for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            ResourceLocation recipeId = holder.id();
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(world.registryAccess());

            if (result.isEmpty() || result.is(Items.AIR)) continue;

            List<String> patternLayout = new ArrayList<>();
            List<Item> recipeInputs = new ArrayList<>();
            boolean isShapeless = true;

            // Для рецептов с паттерном
            if (recipe instanceof ShapedRecipe shaped) {
                isShapeless = false;
                int width = shaped.getWidth();
                int height = shaped.getHeight();
                NonNullList<Ingredient> ingredients = shaped.getIngredients();

                // Карта для сопоставления: ингредиент ориг рецепта -> (A, B, C...)
                Map<Ingredient, Character> ingToChar = new HashMap<>();
                char nextSymbol = 'A';

                for (int y = 0; y < height; y++) {
                    StringBuilder row = new StringBuilder();
                    for (int x = 0; x < width; x++) {
                        Ingredient ing = ingredients.get(y * width + x);
                        if (ing.isEmpty()) {
                            row.append(" ");
                        } else {
                            // Логика назначения символа
                            if (!ingToChar.containsKey(ing)) {
                                ingToChar.put(ing, nextSymbol++);
                            }
                            char symbol = ingToChar.get(ing);
                            row.append(symbol);

                            Item origItem = Arrays.stream(ing.getItems())
                                    .map(ItemStack::getItem)
                                    .min(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()))
                                    .orElse(Items.AIR);
                            recipeInputs.add(origItem);
                        }
                    }
                    patternLayout.add(row.toString());
                }
            } else {
                recipeInputs = recipe.getIngredients().stream()
                        .filter(ing -> !ing.isEmpty())
                        .map(ing -> ing.getItems()[0].getItem())
                        .collect(Collectors.toCollection(ArrayList::new));

                recipeInputs.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
            }

            vanillaRecipes.add(new VanillaRecipeData(
                    recipeId.toString(),
                    result.getItem(),
                    result.getCount(),
                    recipeInputs,
                    isShapeless,
                    patternLayout));
        }

        LOGGER.info("Extracted {} recipes for shuffle pool", vanillaRecipes.size());

        // Сортировка для одинакового графа на одном и том же сиде
        vanillaRecipes.sort(Comparator.comparing(VanillaRecipeData::id));

        // Сбор графа и расчет тиров
        RecipeGraph graph = CoreGraphBuilder.build(vanillaRecipes);
        Map<Item, Integer> tierMap = TierCalculator.calculateTier(vanillaRecipes);

        graph.getCoreItemIdMap().values().forEach(coreItem -> {
            Integer tier = tierMap.get(coreItem.vanillaItem());
            graph.setTier(coreItem, Objects.requireNonNullElse(tier, 0));
        });

        // Генерация
        RecipeGenerator generator = new RecipeGenerator(graph, seed, settings);
        int generatedCount = 0;

        List<CoreRecipe> sortedRecipes = new ArrayList<>(graph.getAllRecipes());
        sortedRecipes.sort(Comparator.comparing(CoreRecipe::id));

        for (CoreRecipe original : sortedRecipes) {
            CoreRecipe randomRecipe = generator.generateRandomRecipe(original);
            if (randomRecipe != null && !randomRecipe.inputs().isEmpty()) {
                graph.addRecipe(randomRecipe);
                generatedCount++;
            }
        }

        LOGGER.info("Successfully randomized {} recipes using seed {}", generatedCount, seed);

        // Сохранение в датапак
        RandomCraftsDatapack.generate(worldFolder, graph);
    }

    private static List<Item> getIngredientsList(CraftingRecipe recipe) {
        List<Item> inputs = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;

            ItemStack[] stacks = ing.getItems();
            if (stacks.length > 0 && !stacks[0].is(Items.AIR)) {
                inputs.add(stacks[0].getItem());
            }
        }
        return inputs;
    }
}