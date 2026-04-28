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
        // Очистка старого содержимого
        clear(worldFolder);

        if (!datapackRoot.exists() && !datapackRoot.mkdirs()) {
            LOGGER.error("CRITICAL: Could not create datapack directory: {}", datapackRoot.getAbsolutePath());
            return;
        }

        try {
            writePackMeta(datapackRoot);
            File recipesFolder = new File(datapackRoot, "data/minecraft/recipe");

            if (!recipesFolder.exists() && !recipesFolder.mkdirs()) {
                LOGGER.error("Could not create recipe folder: {}", recipesFolder.getAbsolutePath());
                return;
            }

            for (CoreRecipe recipe : graph.getAllRecipes()) {
                if (recipe.inputs().isEmpty()) continue;
                writeRecipeJson(recipe, recipesFolder);
            }
        } catch (Exception e) {
            LOGGER.error("Datapack generation failed", e);
        }
    }

    public static void clear(File worldFolder) {
        File datapackRoot = new File(worldFolder, "datapacks/" + DATAPACK_NAME);
        if (datapackRoot.exists()) {
            if (deleteDirectory(datapackRoot)) {
                LOGGER.info("Datapack '{}' successfully removed", DATAPACK_NAME);
            } else {
                LOGGER.warn("Failed to fully remove datapack '{}'. Some files might remain.", DATAPACK_NAME);
            }
        }
    }

    private static boolean deleteDirectory(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!deleteDirectory(f)) return false;
            }
        }
        return file.delete();
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

    private static void writeRecipeJson(CoreRecipe recipe, File recipesFolder) {
        Item outputItem = recipe.output().vanillaItem();
        if (outputItem == Items.AIR) return;

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

        if (ingredients.isEmpty()) return;

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
        }

    }
}