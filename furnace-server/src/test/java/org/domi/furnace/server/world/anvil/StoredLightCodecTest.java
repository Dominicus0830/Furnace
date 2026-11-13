// T-012(O02-01): Furnace 가 쓴 빛(furnace:light_version=1)만 다시 읽고, 표시·판·높이·층 길이가 하나라도 어긋나면 버린다(다시 계산).
// 바닐라·다른 서버 청크(표시 없음)는 isLightOn=1 이어도 읽지 않는다. 차원 밖 빛 섹션은 바닐라처럼 0.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.function.Consumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.light.ChunkLight;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class StoredLightCodecTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    private static AnvilChunkCodec codec(int minY, int sections) {
        var biomes = REGISTRIES.entries("minecraft:worldgen/biome");
        return new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), biomes, biomes.indexOf("minecraft:plains"), minY, sections);
    }

    private static ChunkColumn litFlat() {
        ChunkColumn col = new SuperflatWorldSource(REG, 0).generate(2, -1);
        col.setBlock(4, 20, 9, REG.defaultStateId("minecraft:glowstone"));
        col.setBlock(4, 21, 9, REG.defaultStateId("minecraft:stone"));
        ChunkLevel.precomputeLight(col, 2, -1, REG.defaultStateId("minecraft:bedrock"), true);
        return col;
    }

    @Test
    void furnaceWrittenLight_roundTripsExactly() {
        AnvilChunkCodec codec = codec(ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        ChunkColumn col = litFlat();
        NbtCompound nbt = codec.toNbt(col, 2, -1);
        assertEquals(AnvilChunkCodec.LIGHT_VERSION, nbt.getInt(AnvilChunkCodec.LIGHT_VERSION_KEY));
        assertEquals((byte) 1, nbt.getByte("isLightOn"));
        ChunkColumn back = codec.fromNbt(nbt);
        assertTrue(back.lightStored());
        for (int s = 0; s < ChunkLight.SECTIONS; s++) {
            assertArrayEquals(col.light().sky(s), back.light().sky(s), "sky " + s);
            assertArrayEquals(col.light().block(s), back.light().block(s), "block " + s);
        }
        assertTrue(back.light().ownedLayers() <= col.light().ownedLayers(), "균일 층은 읽을 때 공유로");
        // 다시 쓰면 같은 NBT(빛 섹션)
        assertEquals(nbt.getList("sections").items().size(), codec.toNbt(back, 2, -1).getList("sections").items().size());
    }

    private static void assertNotRead(String why, Consumer<NbtCompound> tamper) {
        AnvilChunkCodec codec = codec(ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        NbtCompound nbt = codec.toNbt(litFlat(), 2, -1);
        tamper.accept(nbt);
        ChunkColumn back = codec.fromNbt(nbt);
        assertNotNull(back, why);
        assertFalse(back.lightStored(), why);
        assertNull(back.light(), why + " — 빛은 다시 계산한다");
    }

    @Test
    void storedLight_isIgnored_whenAnyGuardFails() {
        assertNotRead("표시 없음(바닐라·다른 서버)", n -> n.tags().remove(AnvilChunkCodec.LIGHT_VERSION_KEY));
        assertNotRead("다른 빛 판", n -> n.putInt(AnvilChunkCodec.LIGHT_VERSION_KEY, AnvilChunkCodec.LIGHT_VERSION + 1));
        assertNotRead("isLightOn=0", n -> n.put("isLightOn", (byte) 0));
        assertNotRead("다른 DataVersion", n -> n.putInt("DataVersion", 3953));
        assertNotRead("층 길이가 틀림", n -> {
            for (Object o : n.getList("sections").items()) {
                NbtCompound s = (NbtCompound) o;
                if (s.contains("BlockLight")) { s.putByteArray("BlockLight", new byte[1024]); return; }
            }
            fail("블록빛 섹션이 없다");
        });
    }

    @Test
    void vanillaChunk_withLightOn_isRecomputed() throws Exception {
        NbtCompound vanilla;
        try (InputStream in = StoredLightCodecTest.class.getResourceAsStream("/storage/vanilla-chunk--25.-4.nbt.gz")) {
            vanilla = NbtIo.readGzip(in);
        }
        assertFalse(vanilla.contains(AnvilChunkCodec.LIGHT_VERSION_KEY));
        ChunkColumn col = codec(ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT).fromNbt(vanilla);
        assertNotNull(col);
        assertFalse(col.lightStored(), "바닐라가 쓴 빛은 믿지 않는다(이 판의 빛 엔진 결과가 아니다)");
        assertNull(col.light());
    }

    @Test
    void netherHeight_readsOnlyTheDimensionsLightSections() {
        AnvilChunkCodec codec = codec(0, 16);   // 네더: 섹션 0..15, 빛 섹션 -1..16
        ChunkColumn col = new ChunkColumn(0, REG.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, 0, 16);
        ChunkLight l = new ChunkLight();
        for (int i = 0; i < ChunkLight.SECTIONS; i++) {
            int y = ChunkLight.MIN_Y + i * 16;
            l.setSky(1, y + 3, 2, 9);
            l.setBlock(5, y + 7, 6, 4);
        }
        col.light(l);
        NbtCompound nbt = codec.toNbt(col, 0, 0);
        // 차원 밖 섹션까지 넣어 둔다 — 읽을 때 무시해야 한다
        NbtList sections = nbt.getList("sections");
        for (int y : new int[]{-3, -2, 17, 18}) {
            NbtCompound s = new NbtCompound();
            s.put("Y", (byte) y);
            byte[] a = new byte[2048];
            java.util.Arrays.fill(a, (byte) 0x77);
            s.putByteArray("SkyLight", a);
            sections.items().add(s);
        }
        ChunkColumn back = codec.fromNbt(nbt);
        assertTrue(back.lightStored());
        for (int i = 0; i < ChunkLight.SECTIONS; i++) {
            int sectionY = ChunkLight.MIN_SECTION + i, y = sectionY * 16;
            boolean inside = sectionY >= -1 && sectionY <= 16;
            assertEquals(inside ? 9 : 0, back.light().skyAt(1, y + 3, 2), "sky 섹션 " + sectionY);
            assertEquals(inside ? 4 : 0, back.light().blockAt(5, y + 7, 6), "block 섹션 " + sectionY);
        }
    }
}
