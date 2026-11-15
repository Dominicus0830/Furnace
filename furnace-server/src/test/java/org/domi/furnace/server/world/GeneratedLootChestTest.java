// 월드젠이 남긴 상자 NBT(LootTable·LootTableSeed)는 처음 접근할 때 전리품 표를 가진 상자 블록 엔티티가 된다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class GeneratedLootChestTest {

    @Test
    void generatedChestBecomesLootChest() {
        ChunkColumn col = new ChunkColumn(0);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:chest");
        nbt.putString("LootTable", "minecraft:chests/simple_dungeon");
        nbt.putLong("LootTableSeed", 42L);
        col.putGeneratedBlockEntity(3, 40, 5, nbt);

        ChestBlockEntity chest = col.chestAt(16 * 2 + 3, 40, 16 * 7 + 5);
        assertNotNull(chest);
        assertEquals("minecraft:chests/simple_dungeon", chest.lootTable);
        assertEquals(42L, chest.lootSeed);
        assertTrue(col.generatedBlockEntities().isEmpty(), "생성 NBT 는 상자로 옮겨진다");
        assertSame(chest, col.chestAt(16 * 2 + 3, 40, 16 * 7 + 5));
    }

    @Test
    void otherGeneratedBlockEntitiesStayRaw() {
        ChunkColumn col = new ChunkColumn(0);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:mob_spawner");
        col.putGeneratedBlockEntity(1, 10, 1, nbt);
        assertNull(col.chestAt(1, 10, 1));
        assertEquals(1, col.generatedBlockEntities().size());
    }

    /** 구조물 템플릿 상자의 이름·잠금·영속 데이터도 옮겨진다(디스크 읽기와 같은 필드). */
    @Test
    void generatedChestKeepsNameLockAndBukkitValues() {
        ChunkColumn col = new ChunkColumn(0);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:chest");
        nbt.putString("CustomName", "\"Box\"");
        nbt.putString("Lock", "key");
        NbtCompound pdc = new NbtCompound();
        pdc.putString("demo:owner", "Dana");
        nbt.putCompound("PublicBukkitValues", pdc);
        col.putGeneratedBlockEntity(3, 40, 5, nbt);

        ChestBlockEntity chest = col.chestAt(3, 40, 5);
        assertNotNull(chest);
        assertEquals("\"Box\"", chest.customName);
        assertEquals("key", chest.lock);
        assertEquals("Dana", chest.saveCustomOnly().getCompound("PublicBukkitValues").getString("demo:owner"));
    }
}
