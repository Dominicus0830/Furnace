// 바닐라 EnderChestBlockEntity: 내용물이 없는(플레이어마다의 엔더 인벤토리를 연다) 블록 엔티티. 여는 수·소리·뚜껑 block_event 만 가진다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class EnderChestBlockEntity extends OpenersBlockEntity {

    public EnderChestBlockEntity(Pos pos, int state) { super("minecraft:ender_chest", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "ender_chest"); }

    @Override protected boolean usesOpenersCounter() { return true; }

    @Override protected void onOpen(BlockLevel level, Pos pos, int state) { playSound(level, pos, "minecraft:block.ender_chest.open"); }

    @Override protected void onClose(BlockLevel level, Pos pos, int state) { playSound(level, pos, "minecraft:block.ender_chest.close"); }

    @Override protected void openerCountChanged(BlockLevel level, Pos pos, int state, int old, int now) { level.blockEvent(pos, B.block(state), 1, now); }

    @Override public boolean triggerEvent(int type, int data) { return type == 1 || super.triggerEvent(type, data); }

    private static void playSound(BlockLevel level, Pos pos, String sound) {
        level.playSound(null, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, sound, "blocks", 0.5F, level.random().nextFloat() * 0.1F + 0.9F);
    }
}
