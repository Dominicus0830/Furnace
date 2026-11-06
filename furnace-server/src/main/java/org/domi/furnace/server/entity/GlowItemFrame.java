// 바닐라 GlowItemFrame: 어두운 곳에서도 아이템이 밝게 보이는 액자(소리·떨구는 아이템만 다르다).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class GlowItemFrame extends ItemFrame {
    public GlowItemFrame(ChunkLevel level, int id) { super(EntityType.GLOW_ITEM_FRAME, level, id, true); }
}
