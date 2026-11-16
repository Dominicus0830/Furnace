package net.minecraft.world.level.lighting;

/**
 * 빛 엔진(Mojang 이름 shim, N2b-6 — 자리만). {@code ServerChunkCache#getLightEngine()} 이 돌려주는 객체의 타입이고, 청크 패킷 생성자 인자로 넘어간다.
 * 서버 빛 읽기·쓰기(층 듣기 {@code getLayerListener}, {@code checkBlock}, {@code queueSectionData})는 N2b-5 몫이라 아직 없다 — 부르면 덫.
 */
public class LevelLightEngine {

    public static final int LIGHT_SECTION_PADDING = 1;

    protected LevelLightEngine() {}
}
