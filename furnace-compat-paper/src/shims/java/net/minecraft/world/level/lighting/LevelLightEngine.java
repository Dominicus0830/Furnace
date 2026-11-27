package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import org.domi.furnace.compat.nms.NmsLight;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 빛 엔진(Mojang 이름 shim) — {@code ServerChunkCache#getLightEngine()} 이 주는 객체의 타입이고 청크 패킷 생성자 인자로도 넘어간다.
 *
 * <p>T-205: Paper 1.21.1(Moonrise·Starlight {@code LevelLightEngineMixin}) 이 바꿔 둔 동작을 그대로 따른다. 값은 서버 빛에서 온다(주인 region 스레드는
 * 살아 있는 값, 다른 스레드는 주인이 틱 끝에 낸 공개 스냅샷 — {@link NmsLight}).
 * <ul>
 *   <li>{@link #getLayerListener}: 층 듣기. {@code getDataLayerData} 는 그 빛 섹션의 <b>새 사본</b>을 준다(Paper 도 사본 — 고쳐도 서버 빛은 그대로).</li>
 *   <li>{@link #checkBlock}: 그 칸의 빛 검사를 걸고 곧바로 돌아온다(아무 스레드). 계산은 그 청크 주인의 다음 빛 계산.</li>
 *   <li>{@link #queueSectionData}·{@link #setLightEnabled}·{@link #propagateLightSources}·{@link #retainData}: Paper 처럼 아무것도 하지 않는다
 *       (Starlight 는 빛 자료를 청크에 두고 이 길로 바꾸지 못하게 막는다).</li>
 *   <li>{@link #hasLightWork}·{@link #runLightUpdates}: Furnace 의 빛 대기열은 region 마다 따로 돌아 아무 스레드에서
 *       세계 전체 빛 일을 묻거나 돌릴 수 없다 — 값을 지어내지 않고 {@link UnsupportedNmsCall}.</li>
 *   <li>{@link #updateSectionStatus}: 아무것도 하지 않는다(NMS shim 5d — Furnace 에는 섹션 비었음 표가 없다).</li>
 * </ul>
 */
class LevelLightEngine {

    public static final int LIGHT_SECTION_PADDING = 1;

    private final Level level;
    private final LayerLightEventListener skyReader, blockReader;

    protected LevelLightEngine(Level level) {
        this.level = level;
        this.blockReader = new Reader(false);
        this.skyReader = NmsLight.hasSkyLight(level) ? new Reader(true) : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
    }

    /** Paper: 블록빛은 블록 읽개, 하늘빛은 하늘 읽개(하늘빛이 없는 차원은 빈 읽개). 같은 객체를 준다. */
    public LayerLightEventListener getLayerListener(LightLayer layer) { return layer == LightLayer.BLOCK ? blockReader : skyReader; }

    /** Paper: 그 칸의 빛 검사를 대기열에 넣는다(아무 스레드, 기다리지 않음). 로드되지 않았거나 빛이 없는 청크·높이 밖은 버린다. */
    @Override
    public void checkBlock(BlockPos pos) { NmsLight.checkBlock(level, pos); }

    /** Paper(Starlight getRawBrightness): 하늘빛 − 어둠이 15 면 15, 아니면 그것과 블록빛 중 큰 값. */
    public int getRawBrightness(BlockPos pos, int ambientDarkness) { return NmsLight.rawBrightness(level, pos, ambientDarkness); }

    /** Paper: 아무것도 하지 않는다 — 빛 자료는 청크를 불러올 때만 들어간다(이 길로 서버 빛을 바꾸지 못한다). */
    public void queueSectionData(LightLayer layer, SectionPos pos, DataLayer data) {}

    /** Paper: 아무것도 하지 않는다. */
    public void retainData(ChunkPos pos, boolean retain) {}

    /** Paper: 아무것도 하지 않는다(빛 켜기는 청크 시스템이 한다). */
    @Override
    public void setLightEnabled(ChunkPos pos, boolean retainData) {}

    /** Paper: 아무것도 하지 않는다(청크 빛 단계가 한다). */
    @Override
    public void propagateLightSources(ChunkPos pos) {}

    /** Paper: "n/a". */
    public String getDebugData(LightLayer layer, SectionPos pos) { return "n/a"; }

    @Override
    public boolean hasLightWork() {
        throw new UnsupportedNmsCall("LevelLightEngine#hasLightWork", "Furnace runs light updates per region on that region's thread, so no thread can ask about or run the whole world's light queue");
    }

    @Override
    public int runLightUpdates() {
        throw new UnsupportedNmsCall("LevelLightEngine#runLightUpdates", "Furnace runs light updates per region on that region's thread at the end of its tick - use checkBlock, the next tick computes it");
    }

    /**
     * 바닐라: 섹션이 공기뿐이 되었는지(notReady) 빛 저장소에 알린다. Paper(Starlight)는 그 섹션의 비었음 표를 고치는 작업을 빛 대기열에 건다(Paper jar 대조).
     * Furnace 의 빛 자료는 모든 섹션에 늘 있고(청크마다 26 층) 공기뿐인 섹션은 빛 계산이 칸을 읽어 스스로 판정하므로 고칠 표가 없다 — 아무것도 하지 않는다. 섹션에 직접 쓴 칸의 빛은
     * 바닐라처럼 {@link #checkBlock} 이나 다시 계산(Starlight {@code serverRelightChunks})으로 맞춘다(Axiom 블록 버퍼가 이 순서로 부른다).
     */
    @Override
    public void updateSectionStatus(SectionPos pos, boolean notReady) {}

    /** 바닐라: 빛 섹션 수(블록 섹션 + 아래위 하나씩). */
    public int getLightSectionCount() { return NmsLight.maxLightSection(level) - NmsLight.minLightSection(level) + 1; }

    /** 바닐라: 가장 낮은 빛 섹션(가장 낮은 블록 섹션 − 1). */
    public int getMinLightSection() { return NmsLight.minLightSection(level); }

    /** 바닐라: 가장 낮은 빛 섹션 + 빛 섹션 수(배타). */
    public int getMaxLightSection() { return getMinLightSection() + getLightSectionCount(); }

    /** Paper(Starlight) 의 층 읽개 — 하늘빛·블록빛. 쓰기 면은 Starlight 읽개처럼 막거나 엔진으로 넘긴다. */
    private final class Reader implements LayerLightEventListener {
        private final boolean sky;

        Reader(boolean sky) { this.sky = sky; }

        @Override
        public DataLayer getDataLayerData(SectionPos pos) { return NmsLight.dataLayer(level, sky, pos); }

        @Override
        public int getLightValue(BlockPos pos) { return sky ? NmsLight.skyLight(level, pos) : NmsLight.blockLight(level, pos); }

        /** Starlight 읽개: 엔진의 checkBlock 으로. */
        @Override
        public void checkBlock(BlockPos pos) { LevelLightEngine.this.checkBlock(pos); }

        @Override
        public boolean hasLightWork() { return LevelLightEngine.this.hasLightWork(); }

        /** Starlight 읽개: UnsupportedOperationException. */
        @Override
        public int runLightUpdates() { throw new UnsupportedOperationException(); }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean notReady) { LevelLightEngine.this.updateSectionStatus(pos, notReady); }

        /** Starlight 읽개: UnsupportedOperationException. */
        @Override
        public void setLightEnabled(ChunkPos pos, boolean retainData) { throw new UnsupportedOperationException(); }

        /** Starlight 읽개: UnsupportedOperationException. */
        @Override
        public void propagateLightSources(ChunkPos pos) { throw new UnsupportedOperationException(); }
    }
}
