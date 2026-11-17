// N2b-2 고정 하이트맵 저장·읽기: 바닐라는 저장된 하이트맵을 그대로 쓴다(블록과 어긋난 낡은 값도). 길이가 틀린 종류만 경고 뒤 블록에서 다시 계산.
package org.domi.furnace.server.world.anvil;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.HeightmapType;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class FixedHeightmapCodecTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");

    private static ChunkColumn flat() { return new SuperflatWorldSource(REG, 0).generate(2, -3); }

    /** 하이트맵 raw 의 칸 i 값(9비트, long 당 7개). */
    private static int at(long[] raw, int i) { return (int) (raw[i / 7] >>> ((i % 7) * 9)) & 511; }

    @Test
    void normalChunkLoadsUnfixed() {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        ChunkColumn back = codec.fromNbt(codec.toNbt(flat(), 2, -3));
        assertFalse(back.heightmapsFixed(), "Furnace 가 저장한 보통 청크는 저장값 = 블록에서 센 값 — 고정 모드가 아니다");
    }

    @Test
    void staleHeightmapAfterRawWriteSurvivesSaveAndLoad() {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        ChunkColumn col = flat();
        int top = col.surfaceBlockY(0, 0);   // 평지 잔디(-61)
        int local = top - col.minY();
        // 바닐라 LevelChunkSection.setBlockState: 하이트맵을 안 고친다
        col.setSectionStateRaw(local >> 4, (local & 15) * 256, ChunkSection.AIR, true);
        assertEquals(ChunkSection.AIR, col.getBlock(0, top, 0));
        assertTrue(col.heightmapsFixed());
        assertEquals(top, col.surfaceBlockY(0, 0), "원시 쓰기 뒤 WORLD_SURFACE 는 낡은 값");
        long[] ws = col.worldSurfaceHeightmap().clone(), mb = col.motionBlockingHeightmap().clone();

        ChunkColumn back = codec.fromNbt(codec.toNbt(col, 2, -3));
        assertTrue(back.heightmapsFixed(), "저장된 값이 블록과 다르면 바닐라처럼 그 값을 든다");
        assertArrayEquals(ws, back.worldSurfaceHeightmap());
        assertArrayEquals(mb, back.motionBlockingHeightmap());
        assertEquals(top, back.surfaceBlockY(0, 0));
        // 그 뒤 보통 쓰기는 바닐라 Heightmap.update 만 따른다: 낡은 꼭대기보다 아래 칸을 공기로 바꿔도 값은 그대로
        back.setBlock(0, top - 1, 0, ChunkSection.AIR);
        assertEquals(top, back.surfaceBlockY(0, 0));
        // 낡은 꼭대기 칸에 블록을 놓으면 update 가 위를 맞춘다(같은 값), 그 칸을 다시 공기로 하면 아래로 훑어 내려간다
        int stone = REG.defaultStateId("minecraft:stone");
        back.setBlock(0, top, 0, stone);
        back.setBlock(0, top, 0, ChunkSection.AIR);
        assertEquals(top - 2, back.surfaceBlockY(0, 0), "바닐라 update: 꼭대기를 지우면 아래 첫 블록까지 내려간다");
    }

    @Test
    void storedValueDifferentFromBlocksIsKept() {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        NbtCompound nbt = codec.toNbt(flat(), 2, -3);
        NbtCompound hm = nbt.getCompound("Heightmaps");
        long[] ws = ((long[]) hm.tags().get("WORLD_SURFACE")).clone();
        int i = 5 * 16 + 7;   // x=7, z=5
        ws[i / 7] = (ws[i / 7] & ~(511L << ((i % 7) * 9))) | (200L << ((i % 7) * 9));
        hm.putLongArray("WORLD_SURFACE", ws);
        ChunkColumn back = codec.fromNbt(nbt);
        assertTrue(back.heightmapsFixed());
        assertEquals(200, at(back.worldSurfaceHeightmap(), i));
        assertEquals(200 + back.minY() - 1, back.surfaceBlockY(7, 5));
    }

    @Test
    void wrongLengthIsRecomputedLikeSetRawData() {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        ChunkColumn src = flat();
        long[] want = src.motionBlockingHeightmap().clone();
        NbtCompound nbt = codec.toNbt(src, 2, -3);
        nbt.getCompound("Heightmaps").putLongArray("MOTION_BLOCKING", new long[36]);
        ChunkColumn back = codec.fromNbt(nbt);
        assertFalse(back.heightmapsFixed(), "틀린 길이는 버리고 블록에서 — 나머지가 블록과 같으면 고정할 것이 없다");
        assertArrayEquals(want, back.motionBlockingHeightmap());
    }

    @Test
    void adoptRejectsBadLengthAndReportsIt() {
        ChunkColumn col = flat();
        long[][] stored = new long[HeightmapType.values().length][];
        stored[HeightmapType.OCEAN_FLOOR.ordinal()] = new long[38];
        var bad = col.adoptStoredHeightmaps(stored);
        assertEquals(java.util.List.of(HeightmapType.OCEAN_FLOOR), bad);
        assertFalse(col.heightmapsFixed());
    }
}
