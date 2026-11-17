package net.minecraft.world.level.dimension;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** 바닐라 차원 줄기(차원 타입 + 생성기, Mojang 이름, T-203). */
public record LevelStem(Holder<DimensionType> type, ChunkGenerator generator) {

    public static final ResourceKey<LevelStem> OVERWORLD = key("overworld");
    public static final ResourceKey<LevelStem> NETHER = key("the_nether");
    public static final ResourceKey<LevelStem> END = key("the_end");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceKey<LevelStem> key(String path) {
        return (ResourceKey) ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.withDefaultNamespace(path));
    }
}
