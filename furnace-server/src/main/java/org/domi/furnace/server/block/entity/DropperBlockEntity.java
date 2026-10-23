// 바닐라 Dropper: 9칸 용기.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.Pos;

public class DropperBlockEntity extends ContainerBlockEntity {

    public DropperBlockEntity(Pos pos, int state) { super("minecraft:dropper", pos, state, 9); }

    @Override public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "dropper"); }

    @Override public String defaultTitleKey() { return "container.dropper"; }
}
