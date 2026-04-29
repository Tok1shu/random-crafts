package net.weyne1.randomcrafts.core.generator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.weyne1.randomcrafts.RandomCraftsGameRules;

public record GenerationSettings(
        boolean useTiers,
        boolean excludeColors,
        boolean excludeTools,
        boolean excludeFunctional,
        int tierSpread
) {
    public static GenerationSettings fromWorld(ServerLevel world) {
        GameRules rules = world.getGameRules();

        int clampedSpread = Math.max(1, Math.min(20, rules.getInt(RandomCraftsGameRules.TIER_SPREAD)));

        return new GenerationSettings(
                rules.getBoolean(RandomCraftsGameRules.BALANCED_TIERS),
                rules.getBoolean(RandomCraftsGameRules.EXCLUDE_COLOR_BLOCKS),
                rules.getBoolean(RandomCraftsGameRules.EXCLUDE_TOOLS_ARMOR),
                rules.getBoolean(RandomCraftsGameRules.EXCLUDE_FUNCTIONAL_BLOCKS),
                clampedSpread
        );
    }
}