package net.weyne1.randomcrafts.core.item;

import net.minecraft.world.item.Item;

public record CoreItem(
        String id,
        String name,
        int tier,
        Item vanillaItem
) {

    @Override
    public String toString() {
        return name + " (Tier " + tier + ")";
    }
}