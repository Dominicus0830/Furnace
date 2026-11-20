package net.minecraft.world.entity.ai.village.poi;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 관심 지점 종류(NMS shim 5d — Axiom 블록 버퍼). 값은 서버의 바닐라 표({@code PoiTypes} 등록) 그대로이고 레지스트리
 * {@link net.minecraft.core.registries.BuiltInRegistries#POINT_OF_INTEREST_TYPE} 에 든 것을 쓴다.
 */
public record PoiType(Set<BlockState> matchingStates, int maxTickets, int validRange) {

    /** 바닐라: 아무 종류도 받지 않는 조건. */
    public static final Predicate<Holder<PoiType>> NONE = type -> false;

    public PoiType {
        matchingStates = Set.copyOf(matchingStates);
    }

    /** 바닐라: 그 상태가 이 종류에 드는가. */
    public boolean is(BlockState state) {
        return matchingStates.contains(state);
    }
}
