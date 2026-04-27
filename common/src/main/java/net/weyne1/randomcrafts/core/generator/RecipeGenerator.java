package net.weyne1.randomcrafts.core.generator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.*;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.weyne1.randomcrafts.core.item.CoreItem;
import net.weyne1.randomcrafts.core.recipe.CoreRecipe;
import net.weyne1.randomcrafts.core.graph.RecipeGraph;

import java.util.*;

import static net.weyne1.randomcrafts.RandomCrafts.LOGGER;

public class RecipeGenerator {
    private final RecipeGraph graph;
    private final Random random;

    private static final Set<Class<? extends Item>> FORBIDDEN_TYPES = Set.of(
            ArmorItem.class, DiggerItem.class, SwordItem.class, BoatItem.class,
            ShieldItem.class, ElytraItem.class, BowItem.class,
            BedItem.class, CrossbowItem.class, SpyglassItem.class
    );

    private static final Set<Class<? extends Block>> FORBIDDEN_BLOCK_TYPES = Set.of(
            AbstractFurnaceBlock.class, CraftingTableBlock.class, EnchantingTableBlock.class,
            AnvilBlock.class, SmithingTableBlock.class, LoomBlock.class, CartographyTableBlock.class,
            GrindstoneBlock.class, LecternBlock.class, StonecutterBlock.class, BrewingStandBlock.class,
            FletchingTableBlock.class, TrappedChestBlock.class, CandleBlock.class
    );

    public RecipeGenerator(RecipeGraph graph, long seed) {
        this.graph = graph;
        this.random = new Random(seed);
    }

    public CoreRecipe generateRandomRecipe(CoreRecipe original) {
        if (original == null) return null;

        CoreItem realOutput = graph.getCoreItemById(original.output().id());
        if (realOutput == null) realOutput = original.output();

        int outputTier = realOutput.tier();
        int outputCount = original.outputCount();

        // считает группы одинаковых ингредиентов
        Map<String, Integer> inputCounts = new LinkedHashMap<>();
        for (CoreItem ci : original.inputs()) {
            inputCounts.merge(ci.id(), 1, Integer::sum);
        }

        List<CoreItem> newInputs = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : inputCounts.entrySet()) {
            int count = entry.getValue();

            int minTier;
            int maxTier;

            if (outputTier <= 2) {
                minTier = -1;
                maxTier = 2;
            } else {
                minTier = Math.max(-1, outputTier - 4);
                maxTier = outputTier - 1;
            }

            CoreItem finalRealOutput = realOutput;
            List<CoreItem> candidates = graph.getCoreItemIdMap().values().stream()
                    .filter(i -> i.vanillaItem() != Items.AIR)
                    .filter(i -> i.tier() >= minTier && i.tier() <= maxTier)
                    .filter(i -> !i.id().equals(finalRealOutput.id()))
                    .filter(i -> !isForbiddenIngredient(i.vanillaItem()))
                    .toList();

            CoreItem chosen;
            if (candidates.isEmpty()) {
                LOGGER.warn("No candidates for output tier {}! Min: {}, Max: {}", outputTier, minTier, maxTier);
                chosen = graph.getCoreItemById(entry.getKey());
                if (chosen == null) continue;
            } else {
                chosen = candidates.get(random.nextInt(candidates.size()));
            }

            CoreItem realChosen = graph.getCoreItemById(chosen.id());
            if (realChosen == null) realChosen = chosen;

            for (int i = 0; i < count; i++) {
                newInputs.add(realChosen);
            }
        }


        return new CoreRecipe(original.id(), realOutput, newInputs, outputCount);
    }

    private static boolean isForbiddenIngredient(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();

        // Предметы
        if (FORBIDDEN_TYPES.stream().anyMatch(clazz -> clazz.isInstance(item))) {
            return true;
        }

        // Цветные баннеры
        if (item instanceof BannerItem) {
            return !path.equals("white_banner");
        }

        if (path.contains("concrete")) {
            return !path.equals("white_concrete") && !path.equals("white_concrete_powder");
        }

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            BlockState defaultState = block.defaultBlockState();

            // Блоки
            if (FORBIDDEN_BLOCK_TYPES.stream().anyMatch(clazz -> clazz.isInstance(block))) {
                return true;
            }

            // Цветное стекло и панели
            if (block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock) {
                return true;
            }

            // Цветная шерсть
            if (defaultState.is(BlockTags.WOOL)) {
                return !path.equals("white_wool");
            }

            // Цветные ковры
            if (block instanceof CarpetBlock) {
                return !path.equals("white_carpet");
            }

            // Цветная терракота
            if (defaultState.is(BlockTags.TERRACOTTA)) {
                return !path.equals("terracotta");
            }
        }
        return false;
    }
}