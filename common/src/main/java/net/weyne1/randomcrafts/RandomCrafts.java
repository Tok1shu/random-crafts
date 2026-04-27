package net.weyne1.randomcrafts;

import net.weyne1.randomcrafts.core.world.RandomCraftWorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RandomCrafts {
    public static final String MOD_ID = "random_crafts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        RandomCraftsGameRules.init();
        RandomCraftWorldEvents.init();
    }
}
