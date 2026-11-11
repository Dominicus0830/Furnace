package net.minecraft.world.level.block.state;

import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 블록 상태 하나(Mojang 이름 shim, N0). 상태 번호마다 객체가 하나라 바닐라처럼 {@code ==} 로 비교된다
 * ({@code Block.stateById}·{@code setValue}·{@code defaultBlockState} 모두 같은 표에서 꺼낸다). 코덱은 DataFixerUpper 가 없어 두지 않았다.
 */
public class BlockState extends BlockBehaviour.BlockStateBase {

    /** 호환 모듈 전용 — 블록 shim 이 상태 표를 만들 때만 쓴다. */
    public BlockState(Block owner, Map<Property<?>, Comparable<?>> values, int furnaceStateId) { super(owner, values, furnaceStateId); }

    @Override
    protected BlockState asState() { return this; }
}
