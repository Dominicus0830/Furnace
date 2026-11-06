// 바닐라 1.21.1 서버가 저장한 청크를 읽어 다시 쓰면 섹션 팰릿·데이터·바이옴·높이맵이 바닐라 파일과 같아야 한다.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.Test;

class VanillaChunkFormatTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    private static AnvilChunkCodec codec() {
        var biomes = REGISTRIES.entries("minecraft:worldgen/biome");
        return new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), biomes,
                biomes.indexOf("minecraft:plains"), ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
    }

    private static NbtCompound load(String name) throws Exception {
        try (InputStream in = VanillaChunkFormatTest.class.getResourceAsStream("/storage/" + name)) {
            return NbtIo.readGzip(in);
        }
    }

    @Test
    void fullChunkRoundTripsLikeVanilla() throws Exception {
        NbtCompound vanilla = load("vanilla-chunk--25.-4.nbt.gz");
        AnvilChunkCodec codec = codec();
        ChunkColumn col = codec.fromNbt(vanilla);
        assertNotNull(col);
        NbtCompound ours = codec.toNbt(col, -25, -4);

        NbtList vs = vanilla.getList("sections");
        NbtList os = ours.getList("sections");
        int compared = 0;
        for (Object o : vs.items()) {
            NbtCompound v = (NbtCompound) o;
            if (!v.contains("block_states")) continue; // 조명 전용 섹션
            NbtCompound mine = null;
            for (Object p : os.items()) if (((NbtCompound) p).getByte("Y") == v.getByte("Y")) mine = (NbtCompound) p;
            assertNotNull(mine, "섹션 Y=" + v.getByte("Y"));
            assertNbtEquals(v.getCompound("block_states"), mine.getCompound("block_states"), "block_states Y=" + v.getByte("Y"));
            assertNbtEquals(v.getCompound("biomes"), mine.getCompound("biomes"), "biomes Y=" + v.getByte("Y"));
            compared++;
        }
        assertEquals(24, compared);
        NbtCompound vh = vanilla.getCompound("Heightmaps"), oh = ours.getCompound("Heightmaps");
        for (String k : new String[]{"WORLD_SURFACE", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"}) {
            assertArrayEquals(vh.getLongArray(k), oh.getLongArray(k), k);
        }
        assertEquals(-4, ours.getInt("yPos"));
        assertEquals("minecraft:full", ours.getString("Status"));
        assertEquals(3955, ours.getInt("DataVersion"));
        assertTrue(ours.contains("structures"), "읽은 structures 보존");
        assertEquals(24, ours.getList("PostProcessing").size());
    }

    @Test
    void protoChunkIsRegenerated() throws Exception {
        assertNull(codec().fromNbt(load("vanilla-chunk--13.-2.nbt.gz")), "structure_starts 상태 청크는 새로 생성");
    }

    @Test
    void unknownKeysAndBlockEntitiesSurvive() throws Exception {
        NbtCompound vanilla = load("vanilla-chunk--25.-4.nbt.gz");
        vanilla.putString("Paper.SomeKey", "kept");
        NbtList bes = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound sign = new NbtCompound();
        sign.putString("id", "minecraft:sign");
        sign.putInt("x", -25 * 16 + 3); sign.putInt("y", 70); sign.putInt("z", -4 * 16 + 5);
        sign.put("is_waxed", (byte) 1);
        bes.add(sign);
        vanilla.putList("block_entities", bes);
        AnvilChunkCodec codec = codec();
        NbtCompound ours = codec.toNbt(codec.fromNbt(vanilla), -25, -4);
        assertEquals("kept", ours.getString("Paper.SomeKey"));
        NbtCompound back = (NbtCompound) ours.getList("block_entities").get(0);
        assertEquals("minecraft:sign", back.getString("id"));
        assertEquals(-25 * 16 + 3, back.getInt("x"));
        assertEquals(70, back.getInt("y"));
        assertEquals(-4 * 16 + 5, back.getInt("z"));
        assertEquals((byte) 1, back.getByte("is_waxed"));
    }

    @Test
    void netherDimensionRoundTrip() {
        var dim = org.domi.furnace.server.world.gen.NoiseWorldSource.Dimension.NETHER;
        var source = org.domi.furnace.server.world.gen.NoiseWorldSource.nether(1234L, REG, REGISTRIES);
        var biomes = REGISTRIES.entries("minecraft:worldgen/biome");
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), biomes,
                biomes.indexOf("minecraft:plains"), dim.minY, dim.sectionCount);
        ChunkColumn col = source.generate(3, -2);
        NbtCompound nbt = codec.toNbt(col, 3, -2);
        assertEquals(0, nbt.getInt("yPos"));
        assertEquals(16, nbt.getList("sections").size());
        ChunkColumn back = codec.fromNbt(nbt);
        for (int y = 0; y < 256; y += 3) {
            for (int i = 0; i < 256; i += 7) {
                assertEquals(col.getBlock(i & 15, y, i >> 4), back.getBlock(i & 15, y, i >> 4), "y=" + y);
                assertEquals(col.sections()[y >> 4].getBiome(i & 3, (y >> 2) & 3, (i >> 4) & 3),
                        back.sections()[y >> 4].getBiome(i & 3, (y >> 2) & 3, (i >> 4) & 3));
            }
        }
        assertEquals(java.nio.file.Path.of("w", "DIM-1"), dim.storageDir(java.nio.file.Path.of("w")));
    }

    @Test
    void pluginBiomeIsSavedByNameAndFallsBackToPlainsWhenThePluginIsGone() {
        var base = REGISTRIES;
        var extended = base.withExtras(java.util.List.of(new org.domi.furnace.server.net.registry.RegistrySet.Extra(
                "minecraft:worldgen/biome", "glow:cave", new NbtCompound())));
        var withPlugin = extended.entries("minecraft:worldgen/biome");
        int cave = withPlugin.indexOf("glow:cave");
        assertEquals(base.entries("minecraft:worldgen/biome").size(), cave, "플러그인 바이옴은 바닐라 뒤");
        int plains = withPlugin.indexOf("minecraft:plains");
        var items = ItemRegistry.load("items/1.21.1.txt");
        AnvilChunkCodec saver = new AnvilChunkCodec(REG, items, withPlugin, plains, -64, 24);
        ChunkColumn col = new ChunkColumn(plains, REG.directBits(), 7, -64, 24);
        col.sections()[4].setBiome(1, 2, 3, cave);
        NbtCompound nbt = saver.toNbt(col, 0, 0);
        assertTrue(nbt.getList("sections").size() > 0);
        assertEquals(cave, saver.fromNbt(nbt).sections()[4].getBiome(1, 2, 3), "플러그인이 있으면 이름으로 돌아온다");
        AnvilChunkCodec without = new AnvilChunkCodec(REG, items, base.entries("minecraft:worldgen/biome"),
                base.entries("minecraft:worldgen/biome").indexOf("minecraft:plains"), -64, 24);
        assertEquals(base.entries("minecraft:worldgen/biome").indexOf("minecraft:plains"), without.fromNbt(nbt).sections()[4].getBiome(1, 2, 3),
                "플러그인이 빠지면 그 칸은 평원으로 읽힌다(월드는 열린다)");
    }

    private static void assertNbtEquals(Object expected, Object actual, String path) {
        if (expected instanceof NbtCompound e) {
            assertInstanceOf(NbtCompound.class, actual, path);
            NbtCompound a = (NbtCompound) actual;
            assertEquals(e.tags().keySet(), a.tags().keySet(), path + " 키");
            for (String k : e.tags().keySet()) assertNbtEquals(e.tags().get(k), a.tags().get(k), path + "." + k);
        } else if (expected instanceof NbtList e) {
            NbtList a = (NbtList) actual;
            assertEquals(e.size(), a.size(), path + " 길이");
            for (int i = 0; i < e.size(); i++) assertNbtEquals(e.get(i), a.get(i), path + "[" + i + "]");
        } else if (expected instanceof long[] e) {
            assertArrayEquals(e, (long[]) actual, path);
        } else {
            assertEquals(expected, actual, path);
        }
    }
}
