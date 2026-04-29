package net.weyne1.randomcrafts;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

import static com.mojang.text2speech.Narrator.LOGGER;

public class RandomCraftsGameRules {
    public static GameRules.Key<GameRules.BooleanValue> RANDOMIZE_CRAFTS;
    public static GameRules.Key<GameRules.BooleanValue> BALANCED_TIERS;
    public static GameRules.Key<GameRules.BooleanValue> EXCLUDE_COLOR_BLOCKS;
    public static GameRules.Key<GameRules.BooleanValue> EXCLUDE_TOOLS_ARMOR;
    public static GameRules.Key<GameRules.BooleanValue> EXCLUDE_FUNCTIONAL_BLOCKS;

    // Новое правило для разброса тиров
    public static GameRules.Key<GameRules.IntegerValue> TIER_SPREAD;

    public static void init() {
        RANDOMIZE_CRAFTS = register("randomizeCrafts", false);
        BALANCED_TIERS = register("rcBalancedTiers", true);
        EXCLUDE_COLOR_BLOCKS = register("rcExcludeColorBlocks", true);
        EXCLUDE_TOOLS_ARMOR = register("rcExcludeToolsArmor", true);
        EXCLUDE_FUNCTIONAL_BLOCKS = register("rcExcludeFunctionalBlocks", true);
        TIER_SPREAD = register();
    }

    private static GameRules.Key<GameRules.BooleanValue> register(String name, boolean defaultValue) {
        return GameRules.register(name, GameRules.Category.MISC, GameRules.BooleanValue.create(defaultValue, (server, value) -> {
            broadcastChange(server, name, String.valueOf(value.get()));
        }));
    }

    private static GameRules.Key<GameRules.IntegerValue> register() {
        return GameRules.register("rcTierSpread", GameRules.Category.MISC, GameRules.IntegerValue.create(4, (server, value) -> {
            int current = value.get();
            int clamped = Math.max(1, Math.min(20, current));

            if (current != clamped) {
                value.set(clamped, null);
                LOGGER.info("GameRule {} was out of range (1-20). Clamped to {}", "rcTierSpread", clamped);
            }

            broadcastChange(server, "rcTierSpread", String.valueOf(value.get()));
        }));
    }

    private static void broadcastChange(MinecraftServer server, String name, String newValue) {
        if (server != null) {
            Component msg = Component.translatable("message.random_crafts.rule_changed",
                            Component.literal(name).withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.YELLOW);

            server.getPlayerList().getPlayers().forEach(player -> {
                if (server.getPlayerList().isOp(player.getGameProfile())) {
                    player.sendSystemMessage(msg);
                }
            });

            LOGGER.info("GameRule {} changed to {}", name, newValue);
        }
    }
}