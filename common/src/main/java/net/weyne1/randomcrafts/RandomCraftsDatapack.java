package net.weyne1.randomcrafts;

import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.weyne1.randomcrafts.core.graph.RecipeGraph;
import net.weyne1.randomcrafts.core.item.CoreItem;
import net.weyne1.randomcrafts.core.recipe.CoreRecipe;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static net.weyne1.randomcrafts.RandomCrafts.LOGGER;

public class RandomCraftsDatapack {
    private static final String DATAPACK_NAME = "randomcrafts";

    public static void generate(File worldFolder, RecipeGraph graph) {
        File datapackRoot = new File(worldFolder, "datapacks/" + DATAPACK_NAME);

        if (datapackRoot.exists()) {
            LOGGER.info("Datapack '{}' already exists, skipping generation", DATAPACK_NAME);
            return;
        }

        if (!datapackRoot.mkdirs()) {
            LOGGER.error("Failed to create datapack root: {}", datapackRoot);
            return;
        }

        LOGGER.info("Generating datapack '{}'", DATAPACK_NAME);

        try {
            writePackMeta(datapackRoot);

            File recipesFolder = new File(datapackRoot, "data/minecraft/recipe");
            if (!recipesFolder.mkdirs()) {
                LOGGER.error("Failed to create recipe folder");
                return;
            }

            int generated = 0;

            for (CoreRecipe recipe : graph.getAllRecipes()) {
                if (recipe.inputs().isEmpty()) continue;

                if (writeRecipeJson(recipe, recipesFolder)) {
                    generated++;
                }
            }

            LOGGER.info("Generated {} random recipes", generated);

        } catch (Exception e) {
            LOGGER.error("Datapack generation failed", e);
        }
    }

    private static void writePackMeta(File root) throws IOException {
        File packFile = new File(root, "pack.mcmeta");

        try (Writer writer = new FileWriter(packFile)) {
            // В 1.21.1 pack_format — 48
            writer.write("""
            {
              "pack": {
                "pack_format": 48,
                "description": "RandomCrafts generated recipes"
              }
            }
            """);
        }
    }

    private static boolean writeRecipeJson(CoreRecipe recipe, File recipesFolder) {
        Item outputItem = recipe.output().vanillaItem();
        if (outputItem == Items.AIR) return false;

        ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(outputItem);

        String raw = recipe.id();

        // Обработка имени файла
        String fileName = raw.contains(":")
                ? raw.split(":")[1]
                : raw;

        fileName += ".json";
        File file = new File(recipesFolder, fileName);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", "minecraft:crafting_shapeless");

        List<Map<String, Object>> ingredients = new ArrayList<>();
        for (CoreItem ci : recipe.inputs()) {
            Item item = ci.vanillaItem();
            if (item == Items.AIR) continue;

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            ingredients.add(Map.of("item", id.toString()));
        }

        if (ingredients.isEmpty()) return false;

        json.put("ingredients", ingredients);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", outputId.toString());
        result.put("count", recipe.outputCount());

        json.put("result", result);

        try (Writer writer = new FileWriter(file)) {
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(json, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write recipe {}", fileName, e);
            return false;
        }

        return true;
    }
}