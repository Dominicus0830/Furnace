package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** 위치별 난수 공장(Mojang 이름 shim) — 바닐라 1.21.1 의 기본 메서드 그대로. */
interface PositionalRandomFactory {

    default RandomSource at(BlockPos pos) { return at(pos.getX(), pos.getY(), pos.getZ()); }

    default RandomSource fromHashOf(ResourceLocation name) { return fromHashOf(name.toString()); }
}
