// 엔티티가 아닌 블록이 일으킨 폭발(다른 차원의 침대, 오버월드 리스폰 정박기 등)이 블록을 부수기 직전 — 폭발 region 스레드에서 불린다.
// pos 는 폭발 중심 칸, blockType 은 터지기 전 블록. 나머지 규칙은 EntityExplodeEvent 와 같다.
package org.domi.furnace.api.event.block;

import java.util.List;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class BlockExplodeEvent extends BlockEvent implements Cancellable, org.domi.furnace.api.event.cause.Caused {

    private final String blockType;
    private final List<BlockPos> blocks;
    private boolean cancelled;
    private org.domi.furnace.api.event.cause.CauseChain cause;
    private float yield = 1.0F;
    private String result = "DESTROY";

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockExplodeEvent(String world, BlockPos pos, String blockType, List<BlockPos> blocks) {
        super(world, pos);
        this.blockType = blockType;
        this.blocks = blocks;
    }

    /** cause 는 블록을 터뜨린 쪽(침대를 쓴 플레이어 등, 없으면 NONE·null) — 사슬은 [터진 블록 explode] ← cause. */
    public BlockExplodeEvent(String world, BlockPos pos, String blockType, List<BlockPos> blocks, org.domi.furnace.api.event.cause.CauseChain cause) {
        this(world, pos, blockType, blocks);
        this.cause = org.domi.furnace.api.event.cause.CauseChain.of(org.domi.furnace.api.event.cause.CauseEntry.block(blockType, world, pos, org.domi.furnace.api.event.cause.CauseEntry.EXPLODE), cause);
    }

    /** 서버가 폭발 위력(드롭 확률 1/yield)과 블록 처리 방식(KEEP·DESTROY·DESTROY_WITH_DECAY·TRIGGER_BLOCK)까지 알리는 생성자. */
    public BlockExplodeEvent(String world, BlockPos pos, String blockType, List<BlockPos> blocks, org.domi.furnace.api.event.cause.CauseChain cause,
                             float yield, String result) {
        this(world, pos, blockType, blocks, cause);
        this.yield = yield;
        this.result = result;
    }

    /** 부서진 블록이 드롭을 남길 확률(1.0 = 전부). DESTROY_WITH_DECAY 폭발은 1/반지름. 서버는 이 값을 바꿔도 적용하지 않는다. */
    public float yield() { return yield; }

    /** 폭발이 블록을 다루는 방식 이름(KEEP·DESTROY·DESTROY_WITH_DECAY·TRIGGER_BLOCK). */
    public String result() { return result; }

    /** [터진 블록 explode] ← 그 블록을 쓴 플레이어(알 때). */
    @Override
    public org.domi.furnace.api.event.cause.CauseChain causeChain() {
        if (cause == null) cause = org.domi.furnace.api.event.cause.CauseChain.of(org.domi.furnace.api.event.cause.CauseEntry.block(blockType, world(), pos(), org.domi.furnace.api.event.cause.CauseEntry.EXPLODE));
        return cause;
    }

    /** 터진 블록 종류(minecraft:red_bed 등, 이미 사라졌으면 minecraft:air). */
    public String blockType() { return blockType; }

    public List<BlockPos> blocks() { return blocks; }
}
