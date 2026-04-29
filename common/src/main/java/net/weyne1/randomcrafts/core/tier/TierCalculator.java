package net.weyne1.randomcrafts.core.tier;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceLocation;
import net.weyne1.randomcrafts.core.recipe.VanillaRecipeData;

import java.util.*;
import java.util.stream.Collectors;

import static net.weyne1.randomcrafts.RandomCrafts.LOGGER;

public class TierCalculator {

    public static Map<Item, Integer> calculateTier(List<VanillaRecipeData> vanillaRecipes) {

        Map<Item, Set<Item>> graph = buildGraph(vanillaRecipes);

        Set<Item> locked = new HashSet<>();

        Set<Item> allItems = new HashSet<>();

        for (VanillaRecipeData vr : vanillaRecipes) {
            allItems.add(vr.output());
            allItems.addAll(vr.inputs());
        }

        Set<Item> graphItems = new HashSet<>();
        for (var e : graph.entrySet()) {
            graphItems.add(e.getKey());
            graphItems.addAll(e.getValue());
        }
        allItems.addAll(graphItems);

        Map<Item, Integer> tiers = initializeBaseTiers(allItems, vanillaRecipes, locked);

        propagateTiers(graph, tiers, locked);

        //logGroupedTiers(tiers);

        return tiers;
    }

    private record AnchorRule(String keyword, int tier) {}

    private static final List<AnchorRule> ANCHOR_RULES = List.of(
            new AnchorRule("netherite", 15),
            new AnchorRule("banner_pattern", 15),
            new AnchorRule("smithing_template", 15),

            new AnchorRule("ender", 14),
            new AnchorRule("elytra", 14),
            new AnchorRule("dragon", 14),
            new AnchorRule("end_", 14),
            new AnchorRule("chorus", 14),
            new AnchorRule("shulker", 14),
            new AnchorRule("purpur", 14),

            new AnchorRule("heavy_core", 13),
            new AnchorRule("enchanted_golden_apple", 13),
            new AnchorRule("mace", 13),
            new AnchorRule("creeper_head", 13),
            new AnchorRule("diamond", 13),
            new AnchorRule("wither", 13),
            new AnchorRule("beacon", 13),
            new AnchorRule("conduit", 13),

            new AnchorRule("blaze", 12),
            new AnchorRule("ghast", 12),
            new AnchorRule("magma", 12),
            new AnchorRule("nether", 12),
            new AnchorRule("quartz", 12),
            new AnchorRule("soul", 12),
            new AnchorRule("crimson", 12),
            new AnchorRule("warped", 12),
            new AnchorRule("blackstone", 12),
            new AnchorRule("basalt", 12),

            new AnchorRule("torchflower", 11),
            new AnchorRule("pither", 11),
            new AnchorRule("heart_of_the_sea", 10),
            new AnchorRule("breeze", 10),
            new AnchorRule("prismarine", 10),
            new AnchorRule("gold", 10),
            new AnchorRule("iron", 9),
            new AnchorRule("copper", 7),
            new AnchorRule("disc", 6),

            new AnchorRule("banner", 3),
            new AnchorRule("dye", 0)
    );

    private static Map<Item, Set<Item>> buildGraph(List<VanillaRecipeData> vanillaRecipes) {
        Comparator<Item> itemComparator = Comparator.comparing(item ->
                BuiltInRegistries.ITEM.getKey(item).toString());

        Map<Item, Set<Item>> graph = new LinkedHashMap<>();

        for (VanillaRecipeData vr : vanillaRecipes) {
            if (!graph.containsKey(vr.output())) {
                graph.put(vr.output(), new TreeSet<>(itemComparator));
            }
            graph.get(vr.output()).addAll(vr.inputs());
        }
        return graph;
    }

    private static Map<Item, Integer> initializeBaseTiers(Set<Item> allItems, List<VanillaRecipeData> vanillaRecipes, Set<Item> locked)
    {
        Set<Item> outputs = vanillaRecipes.stream()
                .map(VanillaRecipeData::output)
                .collect(Collectors.toSet());

        Map<Item, Integer> tiers = new HashMap<>();

        for (Item item : allItems) {

            // 1. anchor (жёстко фиксированные тиры)
            Integer anchor = detectAnchorTier(item);
            Integer mining = detectMiningTier(item);

            if (anchor != null || mining != null) {
                int tier = Math.max(
                        anchor != null ? anchor : 0,
                        mining != null ? mining : 0
                );

                tiers.put(item, tier);
                locked.add(item);
                continue;
            }

            // 2. предмет имеет рецепты (может быть скрафчен)
            if (outputs.contains(item)) {
                tiers.put(item, -1); // "не вычислен"
                continue;
            }

            // 3. остальное — базовый нейтрал
            tiers.put(item, 0);
            locked.add(item);
        }

        return tiers;
    }

    private static void propagateTiers(Map<Item, Set<Item>> graph, Map<Item, Integer> tiers, Set<Item> locked)
    {
        boolean changed;

        do {
            changed = false;

            for (Map.Entry<Item, Set<Item>> e : graph.entrySet()) {

                Item out = e.getKey();

                if (locked.contains(out)) continue;

                int current = tiers.getOrDefault(out, -1);

                if (current != -1) continue;

                int maxInput = 0;
                boolean allKnown = true;

                for (Item in : e.getValue()) {
                    int t = tiers.getOrDefault(in, 0);

                    if (t == -1) {
                        allKnown = false;
                        break;
                    }

                    maxInput = Math.max(maxInput, t);
                }

                if (!allKnown) continue;

                int candidate = maxInput + 1;

                tiers.put(out, candidate);
                changed = true;
            }

        } while (changed);
    }

    // Правила тиров

    private static Integer detectMiningTier(Item item) {
        if (!(item instanceof BlockItem blockItem)) return null;

        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();

        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 13;
            if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 10;
            if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 6;
            return 5;
        }

        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return 2;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return 1;

        return null;
    }

    private static Integer detectAnchorTier(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath().toLowerCase(Locale.ROOT);

        for (AnchorRule rule : ANCHOR_RULES) {
            if (path.contains(rule.keyword)) {
                return rule.tier;
            }
        }

        return null;
    }

    private static void logGroupedTiers(Map<Item, Integer> tiers) {
        Map<Integer, List<Item>> grouped = new TreeMap<>();

        for (var e : tiers.entrySet()) {
            grouped.computeIfAbsent(e.getValue(), k -> new ArrayList<>())
                    .add(e.getKey());
        }

        for (var entry : grouped.entrySet()) {
            LOGGER.info("=== TIER {} ===", entry.getKey());

            for (Item item : entry.getValue()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                LOGGER.info(" - {}", id.getPath());
            }
        }
    }
}