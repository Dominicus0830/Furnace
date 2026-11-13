// 이웃 블록이 바뀌어 이 블록이 갱신(neighborChanged·모양 갱신)을 받기 직전 — 그 블록의 region 스레드에서 불린다. 아주 자주 불린다.
// 취소하면 이 블록은 이번 갱신을 무시한다(붙어 있을 수 없는 블록도 떨어지지 않고, 레드스톤 부품도 신호를 다시 보지 않는다).
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.LazyValue;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class BlockPhysicsEvent extends BlockEvent implements Cancellable {

    // 아주 자주 불리고 처리기는 흔히 좌표만 본다 — 블록 값·종류 이름은 처음 읽을 때 만든다(서버는 불변인 상태 번호만 잡는다, T-032)
    private final LazyValue<BlockData> block;
    private final BlockPos sourcePos;
    private final LazyValue<String> changedType;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockPhysicsEvent(String world, BlockPos pos, BlockData block, BlockPos sourcePos, String changedType) {
        this(world, pos, LazyValue.ready(block), sourcePos, LazyValue.ready(changedType));
    }

    /** 블록 값·종류 이름을 처음 읽을 때 만드는 꼴(서버용). 결과는 위 생성자에 미리 만든 값을 준 것과 같다. */
    public BlockPhysicsEvent(String world, BlockPos pos, LazyValue<BlockData> block, BlockPos sourcePos, LazyValue<String> changedType) {
        super(world, pos);
        this.block = java.util.Objects.requireNonNull(block, "block");
        this.sourcePos = sourcePos;
        this.changedType = java.util.Objects.requireNonNull(changedType, "changedType");
    }

    /** 갱신을 받는 블록. */
    public BlockData block() { return block.get(); }

    /** 갱신을 일으킨 칸. */
    public BlockPos sourcePos() { return sourcePos; }

    /** 갱신을 일으킨 블록 종류(minecraft:redstone_wire 등). */
    public String changedType() { return changedType.get(); }
}
