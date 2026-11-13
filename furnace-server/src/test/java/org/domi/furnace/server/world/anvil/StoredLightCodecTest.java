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

    /**
     * 바닐라가 저장된 하늘빛을 읽는 방식: 빠진 층은 가장 가까운 위쪽 저장 층의 맨 아래 줄(y=0, 앞 128바이트)을 16번 되풀이한 것
     * (SkyLightSectionStorage.repeatFirstLayer — 섹션을 열의 맨 위보다 아래에 새로 만들 때), 위에 층이 없으면 15.
     */
    private static byte[][] vanillaSkyView(NbtCompound nbt, int minLightSection, int count) {
        byte[][] stored = new byte[count][];
        for (Object o : nbt.getList("sections").items()) {
            NbtCompound s = (NbtCompound) o;
            int i = s.getByte("Y") - minLightSection;
            if (i >= 0 && i < count && s.tags().get("SkyLight") instanceof byte[] a) stored[i] = a;
        }
        byte[][] view = new byte[count][];
        byte[] above = null;
        for (int i = count - 1; i >= 0; i--) {
            if (stored[i] != null) {
                view[i] = stored[i];
                above = stored[i];
            } else {
                byte[] v = new byte[ChunkLight.LAYER_BYTES];
                if (above == null) java.util.Arrays.fill(v, (byte) 0xFF);
                else for (int r = 0; r < 16; r++) System.arraycopy(above, 0, v, r * 128, 128);
                view[i] = v;
            }
        }
        return view;
    }

    @Test
    void zeroSkyLayer_underLitSlabRow_isWritten_soVanillaKeepsItDark() {
        // 열 전체를 y=112(섹션 7 의 맨 아래 줄)의 아래 반 블록으로 덮는다 — 반 블록 칸은 하늘빛 15, 그 아래 섹션 6 은 모두 0.
        // 바닐라 서버로 확인한 장면(밀폐된 어두운 방 위 반 블록 지붕)과 같은 꼴: 0 층을 빼면 바닐라는 섹션 6 을 15 로 채운다.
        ChunkColumn col = new SuperflatWorldSource(REG, 0).generate(0, 0);
        int slab = REG.stateIdOrAir("minecraft:smooth_stone_slab", java.util.Map.of("type", "bottom", "waterlogged", "false"));
        assertNotEquals(REG.defaultStateId("minecraft:air"), slab);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) col.setBlock(x, 112, z, slab);
        ChunkLevel.precomputeLight(col, 0, 0, REG.defaultStateId("minecraft:bedrock"), true);
        ChunkLight light = col.light();
        int s6 = ChunkLight.sectionIndex(96), s7 = ChunkLight.sectionIndex(112);
        assertTrue(ChunkLight.isKnownZero(light.sky(s6)) || !hasNonZero(light.sky(s6)), "전제: 지붕 아래 섹션은 모두 0");
        assertEquals(15, light.skyAt(3, 112, 5), "전제: 반 블록 줄은 밝다");

        NbtCompound nbt = codec(ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT).toNbt(col, 0, 0);
        byte[][] view = vanillaSkyView(nbt, ChunkLight.MIN_SECTION, ChunkLight.SECTIONS);
        for (int i = 0; i < ChunkLight.SECTIONS; i++) {
            assertArrayEquals(light.sky(i), view[i], "바닐라가 읽는 하늘빛 섹션 " + (ChunkLight.MIN_SECTION + i));
        }
        assertEquals(0, ChunkLight.get(view[s7 - 1], 3, 100, 5), "바닐라가 보는 방 안은 어둡다");
    }

    @Test
    void columnWithoutSky_writesNoSkyLight() {
        ChunkColumn col = new SuperflatWorldSource(REG, 0).generate(0, 0);
        col.setBlock(4, -50, 9, REG.defaultStateId("minecraft:glowstone"));
        ChunkLevel.precomputeLight(col, 0, 0, REG.defaultStateId("minecraft:bedrock"), false);
        NbtCompound nbt = codec(ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT).toNbt(col, 0, 0);
        boolean anyBlock = false;
        for (Object o : nbt.getList("sections").items()) {
            NbtCompound s = (NbtCompound) o;
            assertFalse(s.contains("SkyLight"), "하늘 없는 열은 하늘빛을 쓰지 않는다 Y=" + s.getByte("Y"));
            anyBlock |= s.contains("BlockLight");
        }
        assertTrue(anyBlock, "전제: 블록빛은 있다");
        assertTrue(codec(ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT).fromNbt(nbt).lightStored());
    }

    private static boolean hasNonZero(byte[] a) {
        for (byte b : a) if (b != 0) return true;
        return false;
    }
}
