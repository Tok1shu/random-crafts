package net.weyne1.randomcrafts;

import net.minecraft.world.level.GameRules;

public class RandomCraftsGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> RANDOMIZE_CRAFTS =
            GameRules.register(
                    "randomizeCrafts",
                    GameRules.Category.MISC,
                    GameRules.BooleanValue.create(false)
            );

    public static void init() {
        // Вызов этого метода из главного класса инициализирует статические поля
    }
}