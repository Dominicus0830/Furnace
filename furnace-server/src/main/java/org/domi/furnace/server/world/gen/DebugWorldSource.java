// 바닐라 DebugLevelSource(월드 프리셋 debug_all_block_states): 모든 블록 상태를 격자로 늘어놓는 월드.
// 청크마다 모든 (x, z) 칸의 y=60 에 방벽, y=70 에 getBlockStateFor(x, z) 의 상태를 둔다. 바이옴은 plains 하나(FixedBiomeSource).
// 격자: 상태가 N 개면 GRID_WIDTH = ceil(√N), GRID_HEIGHT = ceil(N / GRID_WIDTH). x, z 가 둘 다 양수이고 홀수인 칸만 /2 한 격자 칸 (gx, gz) 가
// 상태 번호 |gx × GRID_WIDTH + gz| 를 가진다. 바닐라 ALL_BLOCKS 는 블록 레지스트리 순서 × 블록 안 상태 순서인데, 전역 상태 id 가 같은 순서로
// 매겨져 있어 상태 id 가 곧 번호다. 구조물·지형지물·몹 생성 없음(DebugLevelSource 는 applyBiomeDecoration 이 이 두 층뿐).
// 바닐라 LevelChunk.getBlockState 는 디버그 월드에서 저장된 블록을 읽지 않고 같은 식으로 답한다(클라이언트도 login/respawn 의 is_debug 로 그렇게 한다).
// 서버가 만든 층이 그 식과 같으므로 처음 보이는 모습은 같고, 플레이어가 고친 블록은 서버 저장소에는 남는다(차이 — 클라이언트는 식대로만 그린다).
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

public final class DebugWorldSource implements WorldSource {

    /** 바닐라 DebugLevelSource 가 층을 두는 높이. */
    public static final int BARRIER_Y = 60, STATE_Y = 70;
    /** 바닐라 MinecraftServer.setInitialSpawn(debug): BlockPos.ZERO.above(80). */
    public static final int SPAWN_Y = 80;

    private final int totalStates, gridWidth, gridHeight;
    private final int barrier, air;
    private final int biomeId, minY, sectionCount, directBits;

    public DebugWorldSource(BlockRegistry reg, int biomeId) {
        this(reg, biomeId, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
    }

    /** minY..minY+16×sectionCount 가 60 과 70 을 품어야 한다(오버월드 높이). */
    public DebugWorldSource(BlockRegistry reg, int biomeId, int minY, int sectionCount) {
        if (minY > BARRIER_Y || minY + sectionCount * 16 <= STATE_Y) {
            throw new IllegalArgumentException("디버그 월드는 y=" + BARRIER_Y + ".." + STATE_Y + " 를 품은 높이가 필요하다: " + minY + ".." + (minY + sectionCount * 16));
        }
        this.totalStates = reg.totalStateCount();
        this.gridWidth = ceil((float) Math.sqrt((float) totalStates));
        this.gridHeight = ceil((float) totalStates / (float) gridWidth);
        this.barrier = reg.defaultStateId("minecraft:barrier");
        this.air = reg.defaultStateId("minecraft:air");
        this.biomeId = biomeId;
        this.minY = minY;
        this.sectionCount = sectionCount;
        this.directBits = reg.directBits();
    }

    private static int ceil(float f) {
        int i = (int) f;
        return f > (float) i ? i + 1 : i;
    }

    /** 모든 칸의 바이옴 번호(바닐라 디버그 월드는 평원 하나). */
    public int biomeId() { return biomeId; }

    public int gridWidth() { return gridWidth; }

    public int gridHeight() { return gridHeight; }

    /** 바닐라 DebugLevelSource.getBlockStateFor(x, z): 상태 id(공기면 air 의 상태 id). */
    public int stateFor(int x, int z) {
        if (x > 0 && z > 0 && x % 2 != 0 && z % 2 != 0) {
            x /= 2;
            z /= 2;
            if (x <= gridWidth && z <= gridHeight) {
                int i = Math.abs(x * gridWidth + z);
                if (i < totalStates) return i;
            }
        }
        return air;
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        int[] blocks = new int[sectionCount * 4096];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                blocks[(BARRIER_Y - minY) * 256 + lz * 16 + lx] = barrier;
                blocks[(STATE_Y - minY) * 256 + lz * 16 + lx] = stateFor(wx, wz);
            }
        }
        int[] biomes = new int[sectionCount * 64];
        java.util.Arrays.fill(biomes, biomeId);
        ChunkColumn c = minY == ChunkColumn.MIN_Y && sectionCount == ChunkColumn.SECTION_COUNT
                ? new ChunkColumn(biomeId)
                : new ChunkColumn(biomeId, directBits, ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, minY, sectionCount);
        c.loadGenerated(blocks, biomes);
        return c;
    }
}
