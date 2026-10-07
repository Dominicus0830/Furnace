// ChunkColumn→NBT→ChunkColumn 블록 보존 + 편집 보존 검증
package org.domi.furnace.server.world.anvil;

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
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
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
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn fresh = new SuperflatWorldSource(reg, 0).generate(2, -3);
        ChunkColumn back = codec.fromNbt(codec.toNbt(fresh, 2, -3));
        // fromNbt가 블록서 heightmap 재계산 — 원본과 일치(라운드트립서 surface 손실 방지).
        assertArrayEquals(fresh.motionBlockingHeightmap(), back.motionBlockingHeightmap());
    }

    @Test
    void roundTrip_noBlockEntities_producesEmptyList() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);

        ChunkColumn back = codec.fromNbt(codec.toNbt(col, 0, 0));

        assertTrue(back.blockEntities().isEmpty());
    }

    @Test
    void roundTrip_preservesChestBlockEntity() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(1, 2);
        ItemStack[] items = new ItemStack[27];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        items[0] = new ItemStack(5, 12, null);
        items[26] = new ItemStack(9, 3, new byte[]{1, 2, 3});
        col.putChest(new ChestBlockEntity(16, 5, 33, items));

        NbtCompound nbt = codec.toNbt(col, 1, 2);
        ChunkColumn back = codec.fromNbt(nbt);

        ChestBlockEntity restored = back.chestAt(16, 5, 33);
        assertNotNull(restored);
        assertEquals(ItemStack.EMPTY, restored.items[1]);
        assertEquals(5, restored.items[0].itemId());
        assertEquals(12, restored.items[0].count());
        assertNull(restored.items[0].components());
        assertEquals(9, restored.items[26].itemId());
        assertEquals(3, restored.items[26].count());
        assertArrayEquals(new byte[]{1, 2, 3}, restored.items[26].components());
    }

    /** 바닐라 월드의 블록 엔티티(아이템 든 상자, 화로, 표지판 등)가 있어도 청크 로드가 예외 없이 되어야 한다. */
    @Test
    void fromNbt_vanillaBlockEntities_doNotThrow() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0, items);
        NbtCompound nbt = codec.toNbt(new SuperflatWorldSource(reg, 0).generate(0, 0), 0, 0);

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
        sign.putString("id", "minecraft:sign");
        sign.putInt("x", 5); sign.putInt("y", -60); sign.putInt("z", 5);
        bes.add(sign);
        nbt.putList("block_entities", bes);

        ChunkColumn col = codec.fromNbt(nbt);
        ChestBlockEntity c = col.chestAt(1, -60, 2);
        assertNotNull(c, "바닐라 상자는 블록 엔티티로 읽혀야 한다");
        assertEquals(7, c.items[3].count());
        assertEquals(1, col.blockEntities().size(), "화로·표지판은 (미지원이므로) 상자로 둔갑하면 안 된다");
    }
}
