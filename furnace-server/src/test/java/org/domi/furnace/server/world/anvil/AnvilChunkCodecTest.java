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
}
