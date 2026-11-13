package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T-050(O07-01·02·03) 결과 동일 시험 — 유체 퍼짐의 칸 기억을 HashMap 에서 평면 배열로, 면 가림에 꽉 찬 모양 빠른 경로를,
 * 상태별 유체를 표로 바꿔도 결과가 옛 구현과 같아야 한다.
 *
 * <p>네 가지를 본다. (1) 모든 상태의 {@code B.fluid} 표 == 블록 동작의 getFluidState. (2) 모든 고유 충돌 모양 쌍 × 6방향의
 * {@code Shape.mergedFaceOccludes} == 옛 구현(둘 다 꽉 찬 모양일 때만 빠른 경로). (3) 씨앗을 고정한 유체 장면 12개의 영역 블록 상태·레벨 난수
 * 지문이 옛 구현(HashMap 칸 기억, 커밋 07dec3da)에서 뽑은 금 값과 같다 — 지형·원천 배치·난수 시드가 모두 고정이라 지문이 바뀌면
 * 유체 흐름 결과가 달라진 것이다(의도한 유체 규칙 변경이 아니면 버그). (4) 지문은 칸 기억 색인 오류(거리 3~5 칸 합치기)나 찾는 거리 변이를
 * 못 잡으므로(검토 R1), 옛 HashMap 칸 기억 구현을 이 시험에 옮겨 둔 참조 구현과 {@code getSpread} 호출 단위로 대조한다(물·용암, 울트라웜 켬·끔).
 */
class FluidFlatArrayEquivalenceTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void fluidTableMatchesBehaviorForEveryState() {
        int n = Blocks.get().stateCount();
        for (int s = 0; s < n; s++) {
            assertEquals(B.behavior(s).getFluidState(s), B.fluid(s), Blocks.get().describe(s));
        }
    }

    /** 옛 구현: 둘 다 꽉 찬 모양일 때만 true 를 바로 돌려주고, 나머지는 일반 경로. */
    private static boolean legacyMergedFaceOccludes(Shape a, Shape b, int dir) {
        if (a == Shape.BLOCK && b == Shape.BLOCK) return true;
        return Shape.mergedFaceOccludesGeneral(a, b, dir);
    }

    @Test
    void mergedFaceOccludesMatchesLegacyForEveryShapePair() {
        var level = new BlockPlacementTest.TestLevel();
        Map<Shape, Boolean> shapes = new IdentityHashMap<>();
        int n = Blocks.get().stateCount();
        for (int s = 0; s < n; s++) {
            shapes.put(B.behavior(s).getCollisionShape(s, level, new Pos(0, -60, 0), null), Boolean.TRUE);
        }
        shapes.put(Shape.BLOCK, Boolean.TRUE);
        Shape[] all = shapes.keySet().toArray(new Shape[0]);
        assertTrue(all.length > 10, "고유 모양 수 " + all.length);
        int pairs = 0;
        for (Shape a : all) for (Shape b : all) for (int dir = 0; dir < 6; dir++) {
            assertEquals(legacyMergedFaceOccludes(a, b, dir), Shape.mergedFaceOccludes(a, b, dir), "dir=" + dir);
            pairs++;
        }
        assertTrue(pairs > 600);
    }

    /** 옛 구현(07dec3da)에서 뽑은 씨앗별 지문. */
    private static final long[] GOLDEN = {
            0xb0ed0adeaad44f72L, 0x0984bf6ea66272f5L,
            0x0351e5cc4209edc6L, 0x93e8af8b77f4892eL,
            0x6d89a1e0146e4b9cL, 0x34c1a42af8462ec3L,
            0xdc852d46c6697512L, 0xe622147f5cb433d7L, 0xa77eb0499a09fe9eL, 0x0c9826fbe6c9140eL,
            0x4582e07d4ed70ef6L, 0x0bdedaa463e65969L};   // total=8a61a355bf6c007a

    @Test
    void fluidScenesFingerprintMatchesLegacy() {
        String[] terrain = {"minecraft:stone", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:oak_slab", "minecraft:oak_fence",
                "minecraft:oak_sign", "minecraft:ladder", "minecraft:glass", "minecraft:oak_stairs", "minecraft:sugar_cane", "minecraft:oak_trapdoor",
                "minecraft:iron_bars", "minecraft:oak_leaves", "minecraft:snow", "minecraft:dirt_path"};
        long[] got = new long[12];
        for (int seed = 0; seed < got.length; seed++) {
            var level = new BlockPlacementTest.TestLevel();
            ((LegacyRandomSource) level.random()).setSeed(seed);
            java.util.Random r = new java.util.Random(seed);
            for (int x = -24; x < 24; x++) for (int z = -24; z < 24; z++) for (int y = -60; y < -54; y++) {
                if (y == -60 && r.nextInt(12) == 0) { level.setBlock(new Pos(x, y, z), Blocks.get().defaultState("minecraft:air"), 2); continue; }
                if (y > -60 && r.nextInt(3) == 0) level.setBlock(new Pos(x, y, z), Blocks.get().defaultState(terrain[r.nextInt(terrain.length)]), 2);
            }
            int water = Blocks.get().defaultState("minecraft:water"), lava = Blocks.get().defaultState("minecraft:lava"), air = Blocks.get().defaultState("minecraft:air");
            Pos[] src = new Pos[30];
            for (int i = 0; i < src.length; i++) src[i] = new Pos(r.nextInt(44) - 22, -54 + r.nextInt(3), r.nextInt(44) - 22);
            long h = 1469598103934665603L;
            for (int t = 0; t < 600; t++) {
                if (t % 150 == 0 || t % 150 == 100) {
                    boolean on = t % 150 == 0;
                    for (int i = 0; i < src.length; i++) level.setBlock(src[i], on ? (i % 4 == 0 ? lava : water) : air, 3);
                }
                level.runScheduledTicks();
                if (t % 50 == 49) {
                    for (int x = -26; x < 26; x++) for (int z = -26; z < 26; z++) for (int y = -61; y < -50; y++)
                        h = (h ^ level.getBlockState(new Pos(x, y, z))) * 1099511628211L;
                    h = (h ^ level.random().nextInt(1 << 30)) * 1099511628211L;   // 난수 소비가 같은지도 지문에
                }
            }
            got[seed] = h;
        }
        StringBuilder sb = new StringBuilder();
        long total = 0;
        for (long h : got) { sb.append(String.format("0x%016xL, ", h)); total = total * 31 + h; }
        System.out.println("T050 FINGERPRINT " + sb + String.format("total=%016x", total));
        assertArrayEquals(GOLDEN, got, "유체 장면 지문이 옛 구현과 다르다: " + sb);
    }

    // ------------------------------------------------------------------ (4) 옛 HashMap 칸 기억 참조 구현(커밋 07dec3da 의 getSpread·slopeDistance)

    private static short legacyKey(Pos origin, Pos p) {
        int dx = p.x() - origin.x(), dz = p.z() - origin.z();
        return (short) ((dx + 128 & 0xFF) << 8 | dz + 128 & 0xFF);
    }

    private static int[] legacyGetSpread(BlockLevel level, Pos pos, int state, boolean water) {
        int best = 1000;
        int[] out = {-1, -1, -1, -1, -1, -1};
        Map<Short, Integer> states = new HashMap<>();
        Map<Short, Boolean> holes = new HashMap<>();
        int flowing = FluidTicker.flowingType(water);
        for (int dir : Dir.HORIZONTAL) {
            Pos np = pos.relative(dir);
            short key = legacyKey(pos, np);
            int ns = states.computeIfAbsent(key, k -> level.getBlockState(np));
            int nf = B.fluid(ns);
            int newFluid = FluidTicker.getNewLiquid(level, np, ns, water);
            if (FluidTicker.canPassThrough(level, Fluids.type(newFluid), pos, state, dir, np, ns, nf, water)) {
                Pos below = np.below();
                boolean hole = holes.computeIfAbsent(key, k -> FluidTicker.isWaterHole(level, flowing, np, ns, below, level.getBlockState(below), water));
                int dist = hole ? 0 : legacySlope(level, np, 1, Dir.opposite(dir), ns, pos, states, holes, water);
                if (dist < best) java.util.Arrays.fill(out, -1);
                if (dist <= best) {
                    out[dir] = newFluid;
                    best = dist;
                }
            }
        }
        return out;
    }

    private static int legacySlope(BlockLevel level, Pos pos, int depth, int excludedDir, int state, Pos origin,
                                   Map<Short, Integer> states, Map<Short, Boolean> holes, boolean water) {
        int best = 1000;
        int flowing = FluidTicker.flowingType(water);
        for (int dir : Dir.HORIZONTAL) {
            if (dir == excludedDir) continue;
            Pos np = pos.relative(dir);
            short key = legacyKey(origin, np);
            int ns = states.computeIfAbsent(key, k -> level.getBlockState(np));
            int nf = B.fluid(ns);
            if (FluidTicker.canPassThrough(level, flowing, pos, state, dir, np, ns, nf, water)) {
                boolean hole = holes.computeIfAbsent(key, k -> {
                    Pos below = np.below();
                    return FluidTicker.isWaterHole(level, flowing, np, ns, below, level.getBlockState(below), water);
                });
                if (hole) return depth;
                if (depth < FluidTicker.slopeFindDistance(level, water)) {
                    int d = legacySlope(level, np, depth + 1, Dir.opposite(dir), ns, origin, states, holes, water);
                    if (d < best) best = d;
                }
            }
        }
        return best;
    }

    private static final String[] SPREAD_TERRAIN = {"minecraft:stone", "minecraft:air", "minecraft:air", "minecraft:oak_slab", "minecraft:oak_fence",
            "minecraft:oak_sign", "minecraft:ladder", "minecraft:glass", "minecraft:oak_stairs", "minecraft:sugar_cane", "minecraft:oak_trapdoor",
            "minecraft:iron_bars", "minecraft:oak_leaves", "minecraft:snow", "minecraft:dirt_path", "minecraft:water", "minecraft:lava", "minecraft:kelp",
            "minecraft:seagrass", "minecraft:cobweb", "minecraft:scaffolding", "minecraft:oak_door", "minecraft:bubble_column", "minecraft:cauldron",
            "minecraft:hopper", "minecraft:oak_fence_gate", "minecraft:cobblestone_wall", "minecraft:chest", "minecraft:white_carpet", "minecraft:stone_slab"};

    private static BlockPlacementTest.TestLevel level(boolean warm) {
        return new BlockPlacementTest.TestLevel() { @Override public boolean ultraWarm() { return warm; } };
    }

    /** 구멍·장애물 밀도가 다른 무작위 지형 위 모든 칸에서 getSpread == 참조 구현. */
    @Test
    void getSpreadMatchesLegacyHashMapOnRandomTerrain() {
        double[] holeP = {0.02, 0.08, 0.2, 0.5}, obsP = {0.1, 0.3, 0.5};
        long calls = 0, nontrivial = 0;
        for (boolean warm : new boolean[] {false, true}) for (int cfg = 0; cfg < 12; cfg++) for (int seed = 0; seed < 2; seed++) {
            var level = level(warm);
            java.util.Random r = new java.util.Random(cfg * 1000L + seed);
            double hp = holeP[cfg % 4], op = obsP[cfg / 4];
            for (int x = -26; x < 26; x++) for (int z = -26; z < 26; z++) {
                if (r.nextDouble() < hp) level.setBlock(new Pos(x, -60, z), Blocks.get().defaultState("minecraft:air"), 2);
                else if (r.nextInt(6) == 0) level.setBlock(new Pos(x, -60, z), Blocks.get().defaultState(SPREAD_TERRAIN[r.nextInt(SPREAD_TERRAIN.length)]), 2);
                for (int y = -59; y < -57; y++) if (r.nextDouble() < op) level.setBlock(new Pos(x, y, z), Blocks.get().defaultState(SPREAD_TERRAIN[r.nextInt(SPREAD_TERRAIN.length)]), 2);
            }
            for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) for (int y = -59; y <= -58; y++) {
                Pos p = new Pos(x, y, z);
                int bs = level.getBlockState(p);
                for (boolean water : new boolean[] {true, false}) {
                    int[] want = legacyGetSpread(level, p, bs, water);
                    assertArrayEquals(want, FluidTicker.getSpread(level, p, bs, water), "cfg=" + cfg + " seed=" + seed + " warm=" + warm + " water=" + water + " pos=" + p);
                    calls++;
                    for (int v : want) if (v >= 0) { nontrivial++; break; }
                }
            }
        }
        assertTrue(nontrivial > calls / 20, "의미 있는 호출이 너무 적다: " + nontrivial + "/" + calls);
    }

    /** 평평한 돌 바닥에 구멍이 드문(거리 4~5 에 하나뿐인) 장면 — 칸 기억의 바깥 고리와 찾는 거리 끝을 건드린다. */
    @Test
    void getSpreadMatchesLegacyWithSparseHolesAtSearchRim() {
        int stone = Blocks.get().defaultState("minecraft:stone"), air = Blocks.get().defaultState("minecraft:air");
        long ringHits = 0;
        for (boolean warm : new boolean[] {false, true}) for (int seed = 0; seed < 40; seed++) {
            var level = level(warm);
            java.util.Random r = new java.util.Random(7000 + seed);
            for (int x = -26; x < 26; x++) for (int z = -26; z < 26; z++) {
                level.setBlock(new Pos(x, -60, z), stone, 2);
                level.setBlock(new Pos(x, -59, z), air, 2);
                level.setBlock(new Pos(x, -58, z), air, 2);
            }
            int holes = 1 + r.nextInt(6);
            for (int i = 0; i < holes; i++) level.setBlock(new Pos(r.nextInt(25) - 12, -60, r.nextInt(25) - 12), air, 2);
            int walls = r.nextInt(30);
            for (int i = 0; i < walls; i++) level.setBlock(new Pos(r.nextInt(25) - 12, -59, r.nextInt(25) - 12), r.nextBoolean() ? stone : Blocks.get().defaultState("minecraft:oak_fence"), 2);
            for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) {
                Pos p = new Pos(x, -59, z);
                for (boolean water : new boolean[] {true, false}) {
                    int[] want = legacyGetSpread(level, p, air, water);
                    assertArrayEquals(want, FluidTicker.getSpread(level, p, air, water), "seed=" + seed + " warm=" + warm + " water=" + water + " " + p);
                    for (int v : want) if (v >= 0) { ringHits++; break; }
                }
            }
        }
        assertTrue(ringHits > 1000, "ringHits=" + ringHits);
    }
}
