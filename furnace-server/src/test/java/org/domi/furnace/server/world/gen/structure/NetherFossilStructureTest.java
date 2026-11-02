// 네더 화석 시작점·조각 선택 — 합성 지형(netherrack / 용암 / 공기 기둥)으로 난수 호출 순서와 수직 탐색을 손계산과 대조한다.
// 바닐라 oracle 대조는 아니다(가정: oracle 미검증) — 기대값은 디컴파일 NetherFossilStructure/NetherFossilPieces 를 한 줄씩 옮겨 독립 계산했다.
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class NetherFossilStructureTest {

    private static final BlockRegistry BLOCKS = BlockRegistry.load("blocks/1.21.1.txt");
    private static final long SEED = 4242L;

    /** y 에 따라 블록 상태 id 를 돌려주는 합성 기둥(모든 x,z 동일). */
    private static TerrainAccess column(IntUnaryOperator byY) {
        return new TerrainAccess() {
            @Override public int seaLevel() { return 32; }
            @Override public int minY() { return 0; }
            @Override public int genDepth() { return 128; }
            @Override public int firstOccupiedHeight(int hm, int x, int z) { return 0; }
            @Override public int baseBlock(int x, int y, int z) { return byY.applyAsInt(y); }
            @Override public boolean coldEnoughToSnow(String b, int x, int y, int z) { return false; }
            @Override public String biomeAt(int x, int y, int z) { return "minecraft:soul_sand_valley"; }
        };
    }

    private static int id(String name) { return BLOCKS.defaultStateId(name); }

    private static GenContext ctx(StructureEngine e, TerrainAccess t, int cx, int cz) {
        return new GenContext(e, e.registry().structure("minecraft:nether_fossil"), SEED, cx, cz, t);
    }

    @Test
    void registered_and_surfaceSearchMatchesHandDerivation() {
        StructureEngine e = new StructureEngine(BLOCKS, SEED, Set.of());
        StructureRegistry.Def def = e.registry().structure("minecraft:nether_fossil");
        assertNotNull(e.typeOf(def), "nether_fossil 구현이 등록돼야 한다");

        int netherrack = id("minecraft:netherrack"), air = id("minecraft:air");
        // y <= 60 은 netherrack, 그 위는 공기
        TerrainAccess t = column(y -> y <= 60 ? netherrack : air);
        for (int[] c : new int[][] {{0, 0}, {5, -3}, {-7, 11}, {100, 100}}) {
            // 독립 계산: nextInt(16) 두 번, uniform[32, 125] = 32 + nextInt(94), 그 뒤 하강
            WorldgenRandom r = new WorldgenRandom();
            r.setLargeFeatureSeed(SEED, c[0], c[1]);
            int x = c[0] * 16 + r.nextInt(16);
            int z = c[1] * 16 + r.nextInt(16);
            int y0 = 32 + r.nextInt(125 - 32 + 1);
            Integer expectY = y0 >= 61 ? 60 : null;   // y0 <= 60 이면 위 칸이 단단해서 계속 내려가 32 이하로 끝난다

            GenContext g = ctx(e, t, c[0], c[1]);
            Optional<StructureType.Stub> stub = e.typeOf(def).findGenerationPoint(g);
            if (expectY == null) {
                assertTrue(stub.isEmpty(), "chunk " + c[0] + "," + c[1] + " y0=" + y0);
                continue;
            }
            assertTrue(stub.isPresent(), "chunk " + c[0] + "," + c[1] + " y0=" + y0);
            assertEquals(x, stub.get().x());
            assertEquals(expectY, stub.get().y());
            assertEquals(z, stub.get().z());

            // 조각: Rotation.getRandom(nextInt(4)) 가 템플릿(nextInt(14)) 보다 먼저
            int rot = r.nextInt(4);
            int tpl = r.nextInt(14);
            PiecesBuilder b = new PiecesBuilder();
            stub.get().generator().accept(b);
            assertEquals(1, b.pieces().size());
            NetherFossilStructure.Piece p = (NetherFossilStructure.Piece) b.pieces().get(0);
            assertEquals("minecraft:nether_fossils/fossil_" + (tpl + 1), p.templateName());
            assertEquals(rot, p.settings().rotation);
            assertEquals(x, p.tx());
            assertEquals(60, p.ty());
            assertEquals(z, p.tz());
        }
    }

    @Test
    void noSolidGroundAboveSeaLevel_isEmpty() {
        StructureEngine e = new StructureEngine(BLOCKS, SEED, Set.of());
        StructureType type = e.typeOf(e.registry().structure("minecraft:nether_fossil"));
        int lava = id("minecraft:lava"), air = id("minecraft:air");
        // 용암 바다(y <= 31) 위는 전부 공기 — 위 칸 공기 + 아래 칸 단단함 조건을 만족하는 곳이 없다
        TerrainAccess t = column(y -> y <= 31 ? lava : air);
        for (int cx = 0; cx < 8; cx++) assertTrue(type.findGenerationPoint(ctx(e, t, cx, 1)).isEmpty());
    }

    @Test
    void soulSandCountsAsGround_evenThoughNotFullSturdy() {
        StructureEngine e = new StructureEngine(BLOCKS, SEED, Set.of());
        StructureType type = e.typeOf(e.registry().structure("minecraft:nether_fossil"));
        int soul = id("minecraft:soul_sand"), air = id("minecraft:air"), lava = id("minecraft:lava");
        // 영혼 모래 층(y == 50) 위는 공기, 아래는 용암 — 영혼 모래는 윗면이 완전하지 않지만 땅으로 인정된다
        TerrainAccess t = column(y -> y == 50 ? soul : (y < 50 ? lava : air));
        int found = 0;
        for (int cx = 0; cx < 16; cx++) {
            Optional<StructureType.Stub> s = type.findGenerationPoint(ctx(e, t, cx, 0));
            if (s.isPresent()) {
                assertEquals(50, s.get().y());
                found++;
            }
        }
        // y0 가 51 이상일 확률이 74/94 라 16 청크에서 반드시 하나는 나온다(결정적)
        assertTrue(found > 0);
    }

    @Test
    void pieceWidensWritableBoxToWholeTemplate() {
        StructureEngine e = new StructureEngine(BLOCKS, SEED, Set.of());
        org.domi.furnace.server.world.ChunkColumn[] grid = new org.domi.furnace.server.world.ChunkColumn[9];
        int[][] biomes = new int[9][];
        for (int i = 0; i < 9; i++) {
            grid[i] = new org.domi.furnace.server.world.ChunkColumn(0, BLOCKS.directBits(),
                org.domi.furnace.server.world.ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, 0, 8);
            biomes[i] = new int[16 * 4 * 4 * 4];
        }
        org.domi.furnace.server.world.gen.feature.FeatureWorldView view =
            new org.domi.furnace.server.world.gen.feature.FeatureWorldView(grid, biomes, 0, 0, 0, 0, 1, false, SEED);
        // 청크 (0,0) 의 모서리(15, 60, 15)에 놓는다 — 템플릿 대부분이 청크 밖(x,z >= 16)이다
        NetherFossilStructure.Piece p = new NetherFossilStructure.Piece(e, "minecraft:nether_fossils/fossil_1", 15, 60, 15, 0);
        BoundingBox pb = p.boundingBox();
        assertTrue(pb.maxX() > 15 || pb.maxZ() > 15, "템플릿이 청크 밖으로 나가야 시험이 의미 있다");
        BoundingBox chunk = new BoundingBox(0, -1000, 0, 15, 1000, 15);
        PostCtx pc = new PostCtx(e, view, new WorldgenRandom(), new org.domi.furnace.server.world.gen.carver.LegacyRandom(0L),
            chunk, 0, 0, 15, 60, 15);
        p.postProcess(pc);
        assertEquals(Math.max(15, pb.maxX()), chunk.maxX(), "쓰기 상자가 템플릿 전체로 확장돼야 한다");
        assertEquals(Math.max(15, pb.maxZ()), chunk.maxZ());
        int outside = 0;
        org.domi.furnace.server.world.BlockAttrs a = org.domi.furnace.server.world.BlockAttrs.get();
        for (int x = pb.minX(); x <= pb.maxX(); x++)
            for (int y = pb.minY(); y <= pb.maxY(); y++)
                for (int z = pb.minZ(); z <= pb.maxZ(); z++)
                    if ((x > 15 || z > 15) && !a.isAir(view.getBlock(x, y, z))) outside++;
        assertTrue(outside > 0, "청크 밖 칸에도 뼈 블록이 놓여야 한다");
    }
}
