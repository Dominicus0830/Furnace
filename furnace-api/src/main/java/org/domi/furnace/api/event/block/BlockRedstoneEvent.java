// 레드스톤 세기가 바뀌기 직전 — 그 블록의 region 스레드에서 불린다. 대상: 레드스톤 가루, 레버, 버튼, 압력판, 레드스톤 램프.
// 새 세기를 바꿀 수 있다(취소 대신). 레버·버튼·램프는 켜짐(> 0)/꺼짐(0)만 본다 — 원래와 같은 쪽으로 돌리면 바뀌지 않는다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.world.BlockPos;

public final class BlockRedstoneEvent extends BlockEvent {

    private final String blockType;
    private final int oldCurrent;
    private int newCurrent;

    public BlockRedstoneEvent(String world, BlockPos pos, String blockType, int oldCurrent, int newCurrent) {
        super(world, pos);
        this.blockType = blockType;
        this.oldCurrent = oldCurrent;
        this.newCurrent = newCurrent;
    }

    public String blockType() { return blockType; }

    public int oldCurrent() { return oldCurrent; }

    public int newCurrent() { return newCurrent; }

    /** 0..15 로 자른다. */
    public void setNewCurrent(int current) { this.newCurrent = Math.max(0, Math.min(15, current)); }
}
