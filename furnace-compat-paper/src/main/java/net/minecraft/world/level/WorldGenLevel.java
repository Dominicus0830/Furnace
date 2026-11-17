package net.minecraft.world.level;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

/** 지형 생성 레벨(Mojang 이름 shim, T-211) — 바닐라 1.21.1 그대로. 지형지물 배치({@code ConfiguredFeature#place} 등)가 받는다. */
public interface WorldGenLevel extends ServerLevelAccessor {

    long getSeed();

    default boolean ensureCanWrite(BlockPos pos) { return true; }

    default void setCurrentlyGenerating(Supplier<String> structureName) {}
}
