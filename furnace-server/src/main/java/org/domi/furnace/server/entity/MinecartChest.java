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

    /** interact: 창을 열고 TODO(게임 이벤트): container_open, TODO(피글린): PiglinAi.angerNearbyPiglins(player, true). */
    @Override
    public boolean interact(Player p, int hand) { return super.interact(p, hand); }
}
