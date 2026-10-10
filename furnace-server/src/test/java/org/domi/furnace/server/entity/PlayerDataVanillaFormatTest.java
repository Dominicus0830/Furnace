// playerdata 가 바닐라 슬롯 번호·문자열 id 로 저장되고, 서버가 아직 다루지 않는 바닐라 필드는 보존되는지.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataVanillaFormatTest {

    private static final ItemRegistry ITEMS = ItemRegistry.load("items/1.21.1.txt");

    @AfterEach
    void reset() { PlayerData.itemRegistry(null); }

    @Test
    void vanillaSlotsAndPreservedFields(@TempDir Path dir) throws Exception {
        PlayerData.itemRegistry(ITEMS);
        UUID id = UUID.fromString("6ab43178-89fd-4905-97f6-0f67d9d76fd9");
        int stone = ITEMS.idOf("minecraft:stone"), helmet = ITEMS.idOf("minecraft:iron_helmet");
        int shield = ITEMS.idOf("minecraft:shield"), stick = ITEMS.idOf("minecraft:stick");
        ItemStack[] slots = new ItemStack[46];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        slots[36] = new ItemStack(stone, 64, null);     // 핫바 0
        slots[20] = new ItemStack(stone, 3, new byte[]{9});
        slots[5] = new ItemStack(helmet, 1, null);      // 머리
        slots[45] = new ItemStack(shield, 1, null);     // 왼손
        slots[2] = new ItemStack(stick, 2, null);       // 제작 칸

        // 바닐라가 쓴 파일인 척 — Health·XpLevel 이 있다
        Path f = dir.resolve("playerdata").resolve(id + ".dat");
        Files.createDirectories(f.getParent());
        NbtCompound vanilla = new NbtCompound();
        vanilla.put("Health", 7.5f);
        vanilla.putInt("XpLevel", 30);
        vanilla.putString("Dimension", "minecraft:the_nether");
        try (var os = Files.newOutputStream(f)) { NbtIo.writeGzip(os, vanilla, ""); }

        PlayerData.save(dir, id, 1.5, 70, -2.5, 45f, 0f, true, slots);
        assertTrue(Files.exists(dir.resolve("playerdata").resolve(id + ".dat_old")));

        NbtCompound root;
        try (var is = Files.newInputStream(f)) { root = NbtIo.readGzip(is); }
        assertEquals(7.5f, root.tags().get("Health"));
        assertEquals(30, root.getInt("XpLevel"));
        assertEquals("minecraft:the_nether", root.getString("Dimension"), "기존 차원 보존");
        assertArrayEquals(new int[]{0x6ab43178, 0x89fd4905, 0x97f60f67, 0xd9d76fd9}, (int[]) root.tags().get("UUID"));
        assertEquals(3955, root.getInt("DataVersion"));
        NbtList inv = root.getList("Inventory");
        assertEquals(4, inv.size());
        NbtCompound first = (NbtCompound) inv.get(0);
        assertEquals((byte) 0, first.getByte("Slot"));
        assertEquals("minecraft:stone", first.getString("id"));
        assertEquals(64, first.getInt("count"));
        assertEquals((byte) 20, ((NbtCompound) inv.get(1)).getByte("Slot"));
        assertEquals((byte) 103, ((NbtCompound) inv.get(2)).getByte("Slot"));
        assertEquals((byte) -106, ((NbtCompound) inv.get(3)).getByte("Slot"));

        PlayerData.Snapshot back = PlayerData.load(dir, id);
        assertEquals(stone, back.slots()[36].itemId());
        assertEquals(64, back.slots()[36].count());
        assertArrayEquals(new byte[]{9}, back.slots()[20].components());
        assertEquals(helmet, back.slots()[5].itemId());
        assertEquals(shield, back.slots()[45].itemId());
        assertEquals(stick, back.slots()[2].itemId());
        assertEquals(2, back.slots()[2].count());
    }

    @Test
    void slotMappingIsInverse() {
        for (int w = 5; w <= 45; w++) assertEquals(w, PlayerData.vanillaToWindowSlot(PlayerData.windowToVanillaSlot(w)), "창 슬롯 " + w);
        assertEquals(-1, PlayerData.windowToVanillaSlot(0));
    }
}
