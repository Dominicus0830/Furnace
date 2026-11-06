// 바닐라 BedBlockEntity: 저장할 것은 없다(색은 블록이 정한다). 클라이언트는 이 블록 엔티티가 있어야 침대를 그린다(BedRenderer).
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;

public class BedBlockEntity extends BlockEntity {

    public BedBlockEntity(Pos pos, int state) { super("minecraft:bed", pos, state); }

    /** BlockEntityType.BED 의 유효 블록 = 16색 침대(minecraft:beds 태그와 같다). */
    @Override public boolean isValid(int state) { return B.isTag(state, "minecraft:beds"); }

    /** getUpdatePacket = ClientboundBlockEntityDataPacket.create(this). */
    @Override public boolean hasUpdatePacket() { return true; }
}
