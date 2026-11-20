package net.minecraft.world.entity.ai.village.poi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import org.domi.furnace.compat.nms.NmsEffects;
import org.domi.furnace.compat.nms.NmsPoiTypes;

/**
 * 레벨의 관심 지점 저장소(NMS shim 5d — Axiom 블록 버퍼). 서버가 청크마다 들고 있는 POI 를 바꾸는 보기다(바닐라는 구역 저장소 SectionStorage 를
 * 상속한다 — Furnace 는 서버가 들고 있어 상속하지 않는다, 서명 관문 기준선). {@link ServerLevel#getPoiManager} 로 얻는다.
 * <p>넣기·지우기는 서버의 같은 코드(바닐라 PoiManager·PoiSection 이식)를 그 칸의 주인 스레드에서 부른다: 주인 스레드면 곧바로, 다른 스레드면 주인 작업으로
 * 넘긴다(앞서 그 청크에 쓴 것이 먼저 들어간다 — 최대 한 틱 늦다; Paper 는 틱 스레드가 아니면 "Accessing poi chunk off-main" 예외).
 * 청크가 로드돼 있지 않으면 아무것도 하지 않는다.
 */
public class PoiManager {

    private final ServerLevel level;

    private PoiManager(ServerLevel level) { this.level = level; }

    /** 호환 모듈 전용 — {@link ServerLevel} 이 하나 만들어 쥔다. */
    public static PoiManager furnaceFor(ServerLevel level) { return new PoiManager(level); }

    /** 바닐라: 그 칸에 관심 지점을 넣는다(블록과 상관없이). 이미 다른 종류가 있으면 바닐라처럼 오류를 남기고 덮어쓴다. */
    public void add(BlockPos pos, Holder<PoiType> type) {
        int index = NmsPoiTypes.indexOf(type, "PoiManager#add");
        NmsEffects.run(level, pos, NmsEffects.POI_ADD, index, 0, 0, 0L, "PoiManager#add");
    }

    /** 바닐라: 그 칸의 관심 지점을 지운다. 없으면 바닐라처럼 오류를 남긴다. */
    public void remove(BlockPos pos) {
        NmsEffects.run(level, pos, NmsEffects.POI_REMOVE, 0, 0, 0, 0L, "PoiManager#remove");
    }
}
