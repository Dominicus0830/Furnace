// 체스트 블록엔티티: 월드좌표 + 27칸 아이템 순수 홀더(다형 레지스트리 없음, 체스트 전용)
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.ItemStack;

public final class ChestBlockEntity {

    public static final int SLOTS = 27;

    public final int x, y, z; // 월드 좌표(섹션 내부 아님)
    public final ItemStack[] items;
    /** 아직 열지 않은 전리품 상자(바닐라 RandomizableContainer LootTable/LootTableSeed). 열면 채우고 null 로. */
    public String lootTable;
    public long lootSeed;

    public ChestBlockEntity(int x, int y, int z, ItemStack[] items) {
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
