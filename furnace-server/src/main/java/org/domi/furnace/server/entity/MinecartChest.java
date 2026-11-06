// 바닐라 MinecartChest: 27 칸 상자 수레. 표시 블록은 북쪽을 보는 상자, 높이 8.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;

public class MinecartChest extends AbstractMinecartContainer {

    public MinecartChest(ChunkLevel level, int id) { super(EntityType.CHEST_MINECART, level, id, 27); }

    @Override public Type getMinecartType() { return Type.CHEST; }

    @Override protected String dropItem() { return "minecraft:chest_minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.prop("facing").with(B.defaultState("chest"), "north"); }

    @Override public int getDefaultDisplayOffset() { return 8; }

    /** interact: 창을 열고 그 플레이어를 출처로 container_open, 주변 피글린을 화나게 한다. */
    @Override
    public boolean interact(Player p, int hand) {
        boolean r = super.interact(p, hand);
        if (r) {
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.CONTAINER_OPEN, p);
            PiglinAi.angerNearbyPiglins(level, p, true);
        }
        return r;
    }

    /** stopOpen: 창을 닫으면 내 자리에서 그 플레이어를 출처로 container_close. */
    @Override
    public void stopOpen(Player p) { gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.CONTAINER_CLOSE, p); }
}
