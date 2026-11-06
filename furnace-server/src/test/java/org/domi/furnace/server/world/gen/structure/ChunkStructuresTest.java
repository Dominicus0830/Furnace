// 청크 구조물 시작점·참조: 참조 청크 계산, NBT 왕복, 바닐라 형식 읽기(원본 보존), 질의 경계.
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkStructuresTest {

    static final class Box extends StructurePiece {
        Box(int x0, int y0, int z0, int x1, int y1, int z1) { super(new BoundingBox(x0, y0, z0, x1, y1, z1), Direction.NORTH); }
    }

    private static StructureStart start(String id, int cx, int cz, String adaptation, StructurePiece... pieces) {
        return new StructureStart(id, cx, cz, List.of(pieces), adaptation, null);
    }

    @Test
    void references_followVanillaCreateReferences() {
        // 조각 x 0..40, z 0..5 → 청크 x 0..2, z 0 이 참조한다
        StructureStart s = start("minecraft:swamp_hut", 0, 0, "none", new Box(0, 60, 0, 40, 70, 5));
        List<StructureStart> nearby = List.of(s);
        for (int cx = -1; cx <= 3; cx++) {
            ChunkStructures cs = ChunkStructures.generated(cx, 0, cx == 0 ? nearby : List.of(), nearby);
            boolean expect = cx >= 0 && cx <= 2;
            assertEquals(expect, cs.references().containsKey("minecraft:swamp_hut"), "cx=" + cx);
            if (expect) assertArrayEquals(new long[]{ChunkStructures.chunkLong(0, 0)}, cs.references().get("minecraft:swamp_hut"));
        }
        assertTrue(ChunkStructures.generated(0, 1, List.of(), nearby).isEmpty());
        // 반경 8 밖의 시작점은 상자가 걸쳐도 참조하지 않는다
        StructureStart far = start("minecraft:mineshaft", 0, 0, "none", new Box(0, 0, 0, 16 * 12, 10, 5));
        assertTrue(ChunkStructures.generated(9, 0, List.of(), List.of(far)).isEmpty());
        assertFalse(ChunkStructures.generated(8, 0, List.of(), List.of(far)).isEmpty());
        // terrain_adaptation 이 있으면 12 늘린 상자로 판정: x 0..3 조각이 청크 -1 에도 걸친다
        StructureStart beard = start("minecraft:village_plains", 0, 0, "beard_thin", new Box(0, 60, 0, 3, 70, 3));
        assertFalse(ChunkStructures.generated(-1, 0, List.of(), List.of(beard)).isEmpty());
        assertTrue(ChunkStructures.generated(1, 0, List.of(), List.of(beard)).isEmpty());
    }

    @Test
    void query_boundariesAreInclusive() {
        StructureStart s = start("minecraft:swamp_hut", 0, 0, "none", new Box(2, 64, 3, 8, 70, 9), new Box(20, 64, 3, 22, 66, 4));
        ChunkStructures cs = ChunkStructures.generated(0, 0, List.of(s), List.of(s));
        ChunkStructures.Ref r = cs.referenced(null, 0, 0)[0];
        assertTrue(r.hasPieceAt(2, 64, 3));
        assertTrue(r.hasPieceAt(8, 70, 9));
        assertFalse(r.hasPieceAt(1, 64, 3));
        assertFalse(r.hasPieceAt(8, 71, 9));
        assertFalse(r.hasPieceAt(12, 65, 4));      // 조각 사이: 전체 상자 안이지만 조각 밖
        assertTrue(r.isInside(12, 65, 4));
        assertFalse(r.isInside(12, 63, 4));
        // 청크 0 기준으로 잘라 둔 조각만 들고 있다(x 20..22 조각은 청크 1)
        assertEquals(6, r.pieces().length);
        ChunkStructures c1 = ChunkStructures.generated(1, 0, List.of(), List.of(s));
        assertTrue(c1.referenced(null, 1, 0)[0].hasPieceAt(21, 65, 4));
    }

    @Test
    void nbtRoundTrip_throughChunkCodec() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        StructureStart s = start("minecraft:swamp_hut", 0, 0, "none", new Box(2, 64, 3, 8, 70, 9));
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);
        col.structures(ChunkStructures.generated(0, 0, List.of(s), List.of(s)));

        NbtCompound nbt = codec.toNbt(col, 0, 0);
        NbtCompound st = nbt.getCompound("structures");
        NbtCompound hut = st.getCompound("starts").getCompound("minecraft:swamp_hut");
        assertEquals("minecraft:swamp_hut", hut.getString("id"));
        assertEquals(0, hut.getInt("ChunkX"));
        assertArrayEquals(new int[]{2, 64, 3, 8, 70, 9}, (int[]) ((NbtCompound) hut.getList("Children").get(0)).tags().get("BB"));
        assertArrayEquals(new long[]{0L}, st.getCompound("References").getLongArray("minecraft:swamp_hut"));

        ChunkColumn back = codec.fromNbt(nbt);
        assertFalse(back.extraNbt() != null && back.extraNbt().contains("structures"));
        ChunkStructures bs = back.structures();
        assertEquals(1, bs.ownStarts().length);
        assertArrayEquals(new int[]{2, 64, 3, 8, 70, 9}, bs.ownStarts()[0].box());
        // 불러온 참조는 시작 청크에서 푼다
        ChunkStructures.Ref[] refs = bs.referenced(k -> k == 0L ? bs : null, 0, 0);
        assertEquals(1, refs.length);
        assertTrue(refs[0].hasPieceAt(5, 66, 5));
        // 다시 저장하면 같은 바이트
        assertArrayEquals(bytes(st), bytes(codec.toNbt(back, 0, 0).getCompound("structures")));
    }

    @Test
    void vanillaFormat_readAndPreservedLosslessly() {
        // 바닐라가 쓴 꼴: 조각 상세 키(O·GD·Width …)와 시작점이 없는 참조
        NbtCompound piece = new NbtCompound();
        piece.putString("id", "minecraft:tesh");
        piece.putIntArray("BB", new int[]{-100, 63, 200, -94, 70, 208});
        piece.putInt("O", 2);
        piece.putInt("GD", 0);
        piece.putInt("Width", 7);
        piece.putByte("Witch", (byte) 1);
        NbtList children = new NbtList(NbtIo.TAG_COMPOUND);
        children.add(piece);
        NbtCompound hut = new NbtCompound();
        hut.putString("id", "minecraft:swamp_hut");
        hut.putInt("ChunkX", -7);
        hut.putInt("ChunkZ", 12);
        hut.putInt("references", 3);
        hut.putList("Children", children);
        NbtCompound invalid = new NbtCompound();
        invalid.putString("id", "INVALID");
        NbtCompound starts = new NbtCompound();
        starts.putCompound("minecraft:swamp_hut", hut);
        starts.putCompound("minecraft:igloo", invalid);
        NbtCompound refs = new NbtCompound();
        refs.putLongArray("minecraft:swamp_hut", new long[]{ChunkStructures.chunkLong(-7, 12)});
        refs.putLongArray("minecraft:mineshaft", new long[]{ChunkStructures.chunkLong(-9, 10)});
        NbtCompound st = new NbtCompound();
        st.putCompound("starts", starts);
        st.putCompound("References", refs);
        byte[] before = bytes(st);

        ChunkStructures cs = ChunkStructures.fromNbt(st);
        assertEquals(1, cs.ownStarts().length);   // INVALID 은 시작점이 아니다
        ChunkStructures.Ref r = cs.ownStarts()[0];
        assertEquals(-7, r.chunkX());
        assertEquals(12, r.chunkZ());
        assertTrue(r.hasPieceAt(-100, 63, 200));
        assertFalse(r.hasPieceAt(-101, 63, 200));
        assertEquals(Map.of("minecraft:swamp_hut", 1, "minecraft:mineshaft", 1).keySet(), cs.references().keySet());
        // 폐광 시작 청크가 로드되지 않았으면 그것만 빠지고 기억하지 않는다
        long hutKey = ChunkStructures.chunkLong(-7, 12);
        assertEquals(1, cs.referenced(k -> k == hutKey ? cs : null, -7, 12).length);
        assertArrayEquals(before, bytes(cs.toNbt()));
        assertArrayEquals(before, bytes(st));   // 원본도 건드리지 않는다
    }

    @Test
    void chunkLevelQueries() {
        StructureStart s = start("minecraft:swamp_hut", 0, 0, "none", new Box(2, 64, 3, 8, 70, 9));
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);
        col.structures(ChunkStructures.generated(0, 0, List.of(s), List.of(s)));
        ChunkLevel level = new ChunkLevel(1000) {
            @Override protected ChunkColumn chunk(int x, int z) { return x == 0 && z == 0 ? col : null; }
            @Override protected void markDirty(int x, int z) {}
            @Override protected void broadcastToChunk(int x, int z, Packet p) {}
            @Override protected LevelServices services() { return LevelServices.DEFAULT; }
        };
        assertNotNull(level.structureWithPieceAt(new Pos(2, 64, 3), "minecraft:swamp_hut"));
        assertNull(level.structureWithPieceAt(new Pos(2, 63, 3), "minecraft:swamp_hut"));
        assertNull(level.structureWithPieceAt(new Pos(2, 64, 3), "minecraft:igloo"));
        assertNotNull(level.structureAt(new Pos(8, 70, 9), "minecraft:swamp_hut"));
        assertEquals(0, level.structuresAt(new Pos(40, 64, 3)).length);
    }

    private static byte[] bytes(NbtCompound c) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            NbtIo.write(new DataOutputStream(bo), c, "");
            return bo.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
