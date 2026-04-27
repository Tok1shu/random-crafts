package net.weyne1.randomcrafts;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public class RandomCraftsState extends SavedData {
    public static final SavedData.Factory<RandomCraftsState> FACTORY =
            new SavedData.Factory<>(
                    RandomCraftsState::new,
                    RandomCraftsState::load,
                    null
            );

    public boolean applied = false;

    public static RandomCraftsState get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(FACTORY, "randomcrafts");
    }

    public RandomCraftsState() { }

    public static RandomCraftsState load(
            CompoundTag nbt,
            HolderLookup.Provider lookup
    ) {
        RandomCraftsState state = new RandomCraftsState();
        state.applied = nbt.getBoolean("Applied");
        return state;
    }

    @Override
    public @NotNull CompoundTag save(
            CompoundTag nbt,
            HolderLookup.Provider lookup
    ) {
        nbt.putBoolean("Applied", applied);
        return nbt;
    }
}