// ChunkColumn→NBT→ChunkColumn 블록 보존 + 편집 보존 검증
package org.domi.furnace.server.world.anvil;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.Test;

class AnvilChunkCodecTest {

    @Test
    void roundTrip_preservesBlocks() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(2, -3);
        int grass = reg.defaultStateId("minecraft:grass_block");
        col.setBlock(0, -61, 0, ChunkSection.AIR); // 편집

        NbtCompound nbt = codec.toNbt(col, 2, -3);
        ChunkColumn back = codec.fromNbt(nbt);

        assertEquals(ChunkSection.AIR, back.getBlock(0, -61, 0));      // 편집 보존
        assertEquals(grass, back.getBlock(1, -61, 0));                 // 미편집 grass
        assertEquals(reg.defaultStateId("minecraft:bedrock"), back.getBlock(0, -64, 0));
    }

    @Test
    void roundTrip_recomputesHeightmap() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn fresh = new SuperflatWorldSource(reg, 0).generate(2, -3);
        ChunkColumn back = codec.fromNbt(codec.toNbt(fresh, 2, -3));
        // fromNbt가 블록서 heightmap 재계산 — 원본과 일치(라운드트립서 surface 손실 방지).
        assertArrayEquals(fresh.motionBlockingHeightmap(), back.motionBlockingHeightmap());
    }

    @Test
    void roundTrip_noBlockEntities_producesEmptyList() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);

        ChunkColumn back = codec.fromNbt(codec.toNbt(col, 0, 0));

        assertTrue(back.blockEntities().isEmpty());
    }

    @Test
    void roundTrip_preservesChestBlockEntity() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(1, 2);
        ItemStack[] items = new ItemStack[27];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        items[0] = new ItemStack(5, 12, null);
        items[26] = new ItemStack(9, 3, org.domi.furnace.server.item.ComponentPatch.of(java.util.Map.of("minecraft:custom_model_data", 4)));
        col.putChest(new ChestBlockEntity(16, 5, 33, items));

        NbtCompound nbt = codec.toNbt(col, 1, 2);
        ChunkColumn back = codec.fromNbt(nbt);

        ChestBlockEntity restored = back.chestAt(16, 5, 33);
        assertNotNull(restored);
        assertEquals(ItemStack.EMPTY, restored.items[1]);
        assertEquals(5, restored.items[0].itemId());
        assertEquals(12, restored.items[0].count());
        assertTrue(restored.items[0].patch().isEmpty());
        assertEquals(9, restored.items[26].itemId());
        assertEquals(3, restored.items[26].count());
        assertEquals(org.domi.furnace.server.item.ComponentPatch.of(java.util.Map.of("minecraft:custom_model_data", 4)), restored.items[26].patch());
    }

    /**
     * 바닐라 LevelChunk.getBlockEntityNbtForSaving: 저장하는 블록 엔티티마다 keepPacked=false(byte 0)가 마지막 키로 붙는다.
     * 상자·종류별(화로)·NBT 그대로 든 것(전달체) 모두, 디스크에서 true 로 읽어 온 것도 false 로 되쓴다.
     */
    @Test
    void savedBlockEntitiesCarryKeepPackedFalse() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        ItemRegistry items = ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0, items);
        var base = new SuperflatWorldSource(reg, 0).generate(0, 0);
        base.setBlock(4, -60, 4, reg.defaultStateId("minecraft:furnace"));
        base.setBlock(1, -60, 2, reg.defaultStateId("minecraft:chest"));
        base.setBlock(5, -60, 5, reg.defaultStateId("minecraft:conduit"));
        NbtCompound nbt = codec.toNbt(base, 0, 0);

        NbtList bes = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound chest = new NbtCompound();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", 1); chest.putInt("y", -60); chest.putInt("z", 2);
        chest.putBoolean("keepPacked", true);
        bes.add(chest);
        NbtCompound furnace = new NbtCompound();
        furnace.putString("id", "minecraft:furnace");
        furnace.putInt("x", 4); furnace.putInt("y", -60); furnace.putInt("z", 4);
        bes.add(furnace);
        NbtCompound conduit = new NbtCompound();
        conduit.putString("id", "minecraft:conduit");
        conduit.putInt("x", 5); conduit.putInt("y", -60); conduit.putInt("z", 5);
        conduit.putBoolean("keepPacked", true);
        bes.add(conduit);
        nbt.putList("block_entities", bes);

        // 읽은 뒤 다시 쓴 것과, 서버가 직접 만든 상자(디스크에 keepPacked 가 없던 것) 둘 다
        ChunkColumn back = codec.fromNbt(nbt);
        back.putChest(ChestBlockEntity.empty("minecraft:chest", 7, -60, 7));
        back.setBlock(7, -60, 7, reg.defaultStateId("minecraft:chest"));
        NbtList out = codec.toNbt(back, 0, 0).getList("block_entities");
        assertEquals(4, out.size());
        for (Object o : out.items()) {
            NbtCompound be = (NbtCompound) o;
            assertEquals((byte) 0, be.tags().get("keepPacked"), be.tags().get("id") + " 은 keepPacked:0b 로 저장돼야 한다");
            java.util.List<String> keys = new java.util.ArrayList<>(be.tags().keySet());
            assertEquals("keepPacked", keys.get(keys.size() - 1), "바닐라는 keepPacked 를 마지막에 붙인다");
        }
    }

    /** 바닐라 월드의 블록 엔티티(아이템 든 상자, 화로, 표지판 등)가 있어도 청크 로드가 예외 없이 되어야 한다. */
    @Test
    void fromNbt_vanillaBlockEntities_doNotThrow() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        ItemRegistry items = ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0, items);
        var base = new SuperflatWorldSource(reg, 0).generate(0, 0);
        base.setBlock(4, -60, 4, reg.defaultStateId("minecraft:furnace"));   // 블록 엔티티는 맞는 블록 위에서만 읽힌다(isValid)
        base.setBlock(1, -60, 2, reg.defaultStateId("minecraft:chest"));
        NbtCompound nbt = codec.toNbt(base, 0, 0);

        NbtList bes = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound chest = new NbtCompound();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", 1); chest.putInt("y", -60); chest.putInt("z", 2);
        NbtList chestItems = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound it = new NbtCompound();
        it.putByte("Slot", (byte) 3);
        it.putString("id", "minecraft:stone");
        it.putInt("count", 7);
        chestItems.add(it);
        chest.putList("Items", chestItems);
        bes.add(chest);
        NbtCompound furnace = new NbtCompound();
        furnace.putString("id", "minecraft:furnace");
        furnace.putInt("x", 4); furnace.putInt("y", -60); furnace.putInt("z", 4);
        NbtList fItems = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound fi = new NbtCompound();
        fi.putByte("Slot", (byte) 0);
        fi.putString("id", "minecraft:coal");
        fi.putInt("count", 1);
        fItems.add(fi);
        furnace.putList("Items", fItems);
        bes.add(furnace);
        NbtCompound sign = new NbtCompound();
        sign.putString("id", "minecraft:conduit");   // 아직 이식하지 않은 종류(표지판·침대는 이식됨)
        sign.putInt("x", 5); sign.putInt("y", -60); sign.putInt("z", 5);
        bes.add(sign);
        nbt.putList("block_entities", bes);

        ChunkColumn col = codec.fromNbt(nbt);
        ChestBlockEntity c = col.chestAt(1, -60, 2);
        assertNotNull(c, "바닐라 상자는 블록 엔티티로 읽혀야 한다");
        assertEquals(7, c.items[3].count());
        assertEquals(2, col.blockEntities().size(), "상자·화로는 읽히고, 미지원 종류(전달체)는 상자로 둔갑하면 안 된다");
    }

    /**
     * 상자의 이름·잠금과 블록 엔티티의 플러그인 영속 데이터(PublicBukkitValues, Bukkit TileState PDC)가 디스크 왕복에 남는다.
     * 예전엔 상자의 이름·잠금이 스냅숏에만 있고 디스크 NBT 에 적히지 않았다.
     */
    @Test
    void roundTrip_preservesChestNameLockAndBukkitValues() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        ItemRegistry items = ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0, items);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);
        col.setBlock(1, -60, 2, reg.defaultStateId("minecraft:chest"));
        col.setBlock(4, -60, 4, reg.defaultStateId("minecraft:furnace"));
        NbtCompound pdc = new NbtCompound();
        pdc.putString("demo:owner", "Dana");
        NbtCompound chestTag = new NbtCompound();
        chestTag.putString("CustomName", "\"Loot\"");
        chestTag.putString("Lock", "key");
        chestTag.putCompound("PublicBukkitValues", pdc);
        ChestBlockEntity chest = ChestBlockEntity.empty("minecraft:chest", 1, -60, 2);
        chest.loadWithBukkitValues(chestTag);
        col.putChest(chest);
        NbtCompound furnaceTag = new NbtCompound();
        furnaceTag.putString("id", "minecraft:furnace");
        furnaceTag.putCompound("PublicBukkitValues", pdc.copy());
        col.putBlockEntity(org.domi.furnace.server.block.entity.BlockEntities.loadStatic(new org.domi.furnace.server.block.Pos(4, -60, 4),
                reg.defaultStateId("minecraft:furnace"), furnaceTag));

        ChunkColumn back = codec.fromNbt(codec.toNbt(col, 0, 0));

        ChestBlockEntity c = back.chestAt(1, -60, 2);
        assertEquals("\"Loot\"", c.customName);
        assertEquals("key", c.lock);
        assertEquals(pdc.tags(), c.saveCustomOnly().getCompound("PublicBukkitValues").tags());
        var f = back.blockEntityAt(4, -60, 4);
        assertNotNull(f);
        assertEquals(pdc.tags(), f.saveWithFullMetadata().getCompound("PublicBukkitValues").tags());
        assertFalse(f.sentUpdateTag() != null && f.sentUpdateTag().contains("PublicBukkitValues"), "클라이언트로는 보내지 않는다");
    }

    /** 디스크의 빈 Lock("")은 잠금이 아니다(바닐라 LockCode.fromTag — 빈 열쇠는 NO_LOCK, 다시 쓰지 않는다). */
    @Test
    void emptyChestLockOnDiskIsNoLock() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        ItemRegistry items = ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0, items);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);
        col.setBlock(1, -60, 2, reg.defaultStateId("minecraft:chest"));
        ChestBlockEntity chest = ChestBlockEntity.empty("minecraft:chest", 1, -60, 2);
        chest.lock = "key";
        col.putChest(chest);
        NbtCompound root = codec.toNbt(col, 0, 0);
        int found = 0;
        for (Object o : root.getList("block_entities").items()) {
            if (o instanceof NbtCompound be && be.contains("Lock")) { be.putString("Lock", ""); found++; }
        }
        assertEquals(1, found, "상자의 Lock 이 디스크에 적혔다");
        ChunkColumn back = codec.fromNbt(root);
        assertNull(back.chestAt(1, -60, 2).lock, "빈 Lock 은 잠금 없음");
        for (Object o : codec.toNbt(back, 0, 0).getList("block_entities").items()) {
            assertFalse(((NbtCompound) o).contains("Lock"), "다시 쓰지 않는다");
        }
    }

    /** 갱신 태그가 saveCustomOnly 인 종류(표지판)도 클라이언트로는 영속 데이터를 보내지 않는다(Paper Sanitize-sent-BlockEntity-NBT). */
    @Test
    void sentUpdateTagDropsBukkitValues() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        NbtCompound pdc = new NbtCompound();
        pdc.putString("demo:owner", "Dana");
        NbtCompound tag = new NbtCompound();
        tag.putString("id", "minecraft:sign");
        tag.putCompound("PublicBukkitValues", pdc);
        var sign = org.domi.furnace.server.block.entity.BlockEntities.loadStatic(new org.domi.furnace.server.block.Pos(0, 0, 0),
                reg.defaultStateId("minecraft:oak_sign"), tag);
        assertTrue(sign.saveCustomOnly().contains("PublicBukkitValues"));
        assertTrue(sign.updateTag().contains("PublicBukkitValues"), "표지판 갱신 태그는 saveCustomOnly 그대로");
        assertFalse(sign.sentUpdateTag().contains("PublicBukkitValues"));
        assertTrue(sign.sentUpdateTag().contains("front_text"));
    }
}
