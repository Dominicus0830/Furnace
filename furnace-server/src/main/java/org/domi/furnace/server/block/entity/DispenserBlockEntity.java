// 바닐라 Dispenser: 9칸 용기.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.Pos;

public class DispenserBlockEntity extends ContainerBlockEntity {

    public DispenserBlockEntity(Pos pos, int state) { super("minecraft:dispenser", pos, state, 9); }

    @Override public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "dispenser"); }

    @Override public String defaultTitleKey() { return "container.dispenser"; }
}
