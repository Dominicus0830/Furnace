// 체스트 블록엔티티: 월드좌표 + 27칸 아이템 홀더(저장 꼴은 ChunkSnapshot·AnvilChunkCodec 이 직접 다룬다)
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.ItemStack;

public final class ChestBlockEntity extends org.domi.furnace.server.block.entity.BlockEntity {

    public static final int SLOTS = 27;

    public final int x, y, z; // 월드 좌표(섹션 내부 아님)
    public final ItemStack[] items;
    /** 아직 열지 않은 전리품 상자(바닐라 RandomizableContainer LootTable/LootTableSeed). 열면 채우고 null 로. */
    public String lootTable;
    public long lootSeed;

    public ChestBlockEntity(int x, int y, int z, ItemStack[] items) {
        super("minecraft:chest", new org.domi.furnace.server.block.Pos(x, y, z), -1);
        this.x = x;
        this.y = y;
        this.z = z;
        this.items = items;
    }

    public static ChestBlockEntity empty(int x, int y, int z) {
        ItemStack[] items = new ItemStack[SLOTS];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        return new ChestBlockEntity(x, y, z, items);
    }
}
