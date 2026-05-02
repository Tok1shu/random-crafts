package net.weyne1.randomcrafts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;

public class RandomCraftsState extends SavedData {
    public boolean applied = false;
    public long usedSeed = 0;

    public RandomCraftsState() { }

    /*
    * Теперь Minecraft сам разбирается, как читать данные, ранее был ручной NBT, что делало зоопарк, сейчас у них есть
    * унифицированная штука, под названием Codec, что позволяет описать структуру один раз и использовать для любого формата.
    * DataFixTypes - система миграции между версиями, то есть автоматическую миграцию сделает если изменится структура (как я понял)
    * */

    public static final Codec<RandomCraftsState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("Applied").orElse(false).forGetter(s -> s.applied),
            Codec.LONG.fieldOf("UsedSeed").orElse(0L).forGetter(s -> s.usedSeed)
    ).apply(instance, (applied, usedSeed) -> {
        RandomCraftsState state = new RandomCraftsState();
        state.applied = applied;
        state.usedSeed = usedSeed;
        return state;
    }));

    public static final SavedDataType<RandomCraftsState> TYPE = new SavedDataType<>(
            "randomcrafts",
            RandomCraftsState::new,
            CODEC,
            DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES // или любой подходящий DataFixTypes
    );

    public static RandomCraftsState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}