// 바닐라 Barrel: 27칸 용기.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;

public class BarrelBlockEntity extends ContainerBlockEntity {

    public BarrelBlockEntity(Pos pos, int state) { super("minecraft:barrel", pos, state, 27); }

    @Override public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "barrel"); }

    @Override protected boolean usesOpenersCounter() { return true; }

    @Override protected void onOpen(BlockLevel level, Pos pos, int state) {
        playSound(level, pos, state, "minecraft:block.barrel.open");
        setOpen(level, pos, state, true);
    }

    @Override protected void onClose(BlockLevel level, Pos pos, int state) {
        playSound(level, pos, state, "minecraft:block.barrel.close");
        setOpen(level, pos, state, false);
    }

    private static void setOpen(BlockLevel level, Pos pos, int state, boolean open) {
        level.setBlock(pos, B.prop("open").with(state, open), 3);
    }

    /** 소리는 바라보는 쪽 면 가운데에서. */
    private static void playSound(BlockLevel level, Pos pos, int state, String sound) {
        int f = Dir.byName(B.prop("facing").valueName(state));
        level.playSound(null, pos.x() + 0.5 + Dir.DX[f] / 2.0, pos.y() + 0.5 + Dir.DY[f] / 2.0, pos.z() + 0.5 + Dir.DZ[f] / 2.0, sound, "blocks", 0.5F,
                level.random().nextFloat() * 0.1F + 0.9F);
    }

    @Override public String defaultTitleKey() { return "container.barrel"; }


    @Override protected boolean randomizable() { return true; }
}
