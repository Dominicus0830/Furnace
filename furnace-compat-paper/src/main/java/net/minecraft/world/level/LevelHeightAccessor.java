package net.minecraft.world.level;

import net.minecraft.core.BlockPos;

/** 높이 질의(Mojang 이름, N2a). 바닐라 기본 메서드 그대로 — 섹션 번호 0 = 맨 아래 섹션. */
public interface LevelHeightAccessor {

    int getHeight();

    int getMinBuildHeight();

    default int getMaxBuildHeight() { return getMinBuildHeight() + getHeight(); }

    default int getSectionsCount() { return getMaxSection() - getMinSection(); }

    default int getMinSection() { return getMinBuildHeight() >> 4; }

    default int getMaxSection() { return ((getMaxBuildHeight() - 1) >> 4) + 1; }

    default boolean isOutsideBuildHeight(BlockPos pos) { return isOutsideBuildHeight(pos.getY()); }

    default boolean isOutsideBuildHeight(int y) { return y < getMinBuildHeight() || y >= getMaxBuildHeight(); }

    default int getSectionIndex(int y) { return getSectionIndexFromSectionY(y >> 4); }

    default int getSectionIndexFromSectionY(int sectionY) { return sectionY - getMinSection(); }

    default int getSectionYFromSectionIndex(int index) { return index + getMinSection(); }

    static LevelHeightAccessor create(int minBuildHeight, int height) {
        return new LevelHeightAccessor() {
            @Override
            public int getHeight() { return height; }

            @Override
            public int getMinBuildHeight() { return minBuildHeight; }
        };
    }
}
