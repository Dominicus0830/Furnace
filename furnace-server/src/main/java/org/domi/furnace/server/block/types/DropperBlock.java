// 바닐라 DropperBlock: 공급기. 아이템을 앞 용기에 넣거나 그냥 떨군다(블록 엔티티). 빈 공급기는 실패 소리만(게임 이벤트 없음).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class DropperBlock extends DispenserBlock {

    public DropperBlock(int block) { super(block); }

    @Override
    protected void dispenseFrom(BlockLevel level, int state, Pos pos) {
        // TODO(블록엔티티·아이템): 무작위 칸 하나를 앞 용기에 넣거나 기본 발사.
        level.levelEvent(null, 1001, pos, 0);
    }
}
