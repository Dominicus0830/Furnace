// 바닐라 DropperBlockEntity: 발사기 블록 엔티티와 같고 제목만 다르다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.Pos;

public class DropperBlockEntity extends DispenserBlockEntity {

    public DropperBlockEntity(Pos pos, int state) { super("minecraft:dropper", pos, state); }

    @Override public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "dropper"); }

    @Override public String defaultTitleKey() { return "container.dropper"; }


    @Override protected boolean randomizable() { return true; }
}
