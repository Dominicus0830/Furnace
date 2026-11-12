package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

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
 * <p>세 가지를 본다. (1) 모든 상태의 {@code B.fluid} 표 == 블록 동작의 getFluidState. (2) 모든 고유 충돌 모양 쌍 × 6방향의
 * {@code Shape.mergedFaceOccludes} == 옛 구현(둘 다 꽉 찬 모양일 때만 빠른 경로). (3) 씨앗을 고정한 유체 장면 12개의 영역 블록 상태·레벨 난수
 * 지문이 옛 구현(HashMap 칸 기억, 커밋 07dec3da)에서 뽑은 금 값과 같다 — 지형·원천 배치·난수 시드가 모두 고정이라 지문이 바뀌면
 * 유체 흐름 결과가 달라진 것이다(의도한 유체 규칙 변경이 아니면 버그).
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
}
