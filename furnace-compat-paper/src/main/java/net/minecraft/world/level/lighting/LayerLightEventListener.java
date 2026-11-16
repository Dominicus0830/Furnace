package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * 빛 층 하나(하늘빛·블록빛)의 읽기 면(Mojang 이름 shim, T-205) — {@code LevelLightEngine#getLayerListener} 가 준다. 바닐라 1.21.1 모양 그대로이고,
 * 서버 값은 {@link LevelLightEngine} 의 층 듣기가 Paper(Starlight) 규칙으로 준다.
 */
public interface LayerLightEventListener extends LightEventListener {

    /** 그 빛 섹션의 니블 배열(없으면 null). Paper 는 늘 새 사본을 준다 — 고쳐도 서버 빛은 바뀌지 않는다. */
    DataLayer getDataLayerData(SectionPos pos);

    /** 그 칸의 빛 값(0~15). */
    int getLightValue(BlockPos pos);

    /** 빛이 없는 층(바닐라 그대로 — 하늘빛이 없는 차원의 하늘빛 층). */
    enum DummyLightLayerEventListener implements LayerLightEventListener {
        INSTANCE;

        @Override
        public DataLayer getDataLayerData(SectionPos pos) { return null; }

        @Override
        public int getLightValue(BlockPos pos) { return 0; }

        @Override
        public void checkBlock(BlockPos pos) {}

        @Override
        public boolean hasLightWork() { return false; }

        @Override
        public int runLightUpdates() { return 0; }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean notReady) {}

        @Override
        public void setLightEnabled(ChunkPos pos, boolean retainData) {}

        @Override
        public void propagateLightSources(ChunkPos pos) {}
    }
}
