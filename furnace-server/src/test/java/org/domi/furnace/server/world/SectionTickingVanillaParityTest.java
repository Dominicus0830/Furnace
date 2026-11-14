// 양동이로 놓은 물이 구역을 무작위 틱 대상으로 만든다(바닐라 LevelChunkSection.setBlockState 의 tickingFluidCount ±1 은 물도 센다) —
// 바닐라 1.21.1 서버 클래스(LevelChunkSection·recalcBlockCounts)를 직접 돌려 뽑은 정답 열(resources/vanilla/section-ticking-golden.txt)을
// 칸 쓰기·재계수·전체 비우기 열로 되풀이해 구역 isRandomlyTicking 이 모든 줄에서 같은지 본다. 바닐라 특이점도 그대로다:
// 적재(recalc)로 만든 구역은 용암 유체만 세므로 물이 있어도 대상이 아니고, 그 구역의 물을 깨면 센 값이 음수가 되어 다시 놓아도 한동안 대상이 아니다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectionTickingVanillaParityTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        BlockBehaviors.ofBlock(0);   // 무작위 틱·유체 표를 구역에 건다
    }

    private static final int AIR = 0;
    private static final int SEC = 5;   // 컬럼 안에서 쓰는 구역 번호

    private record Line(char op, int[] args, boolean ticking, String text) {}

    private static List<Line> golden(int[][] poolOut) throws Exception {
        List<Line> out = new ArrayList<>();
        try (var in = SectionTickingVanillaParityTest.class.getResourceAsStream("/vanilla/section-ticking-golden.txt")) {
            assertNotNull(in, "정답 열 없음");
            var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String l; (l = br.readLine()) != null; ) {
                if (l.startsWith("#") || l.isBlank()) continue;
                String[] p = l.split(" ");
                if (p[0].equals("P")) {
                    int[] pool = new int[p.length - 1];
                    for (int i = 0; i < pool.length; i++) pool[i] = Integer.parseInt(p[i + 1]);
                    poolOut[0] = pool;
                    continue;
                }
                int arrow = l.indexOf("->");
                boolean t = l.substring(arrow + 2).trim().equals("1");
                String[] a = l.substring(0, arrow).trim().split(" ");
                int[] args = new int[a.length - 1];
                for (int i = 0; i < args.length; i++) args[i] = (int) Long.parseLong(a[i + 1]);
                out.add(new Line(p[0].charAt(0), args, t, l));
            }
        }
        return out;
    }

    private static int[] fill(int[] pool, long seed, int mask) {
        int c = Integer.bitCount(mask);
        int[] sub = new int[c];
        for (int i = 0, k = 0; i < pool.length; i++) if ((mask >> i & 1) != 0) sub[k++] = pool[i];
        SplittableRandom rr = new SplittableRandom(seed);
        int[] arr = new int[4096];
        for (int i = 0; i < 4096; i++) arr[i] = sub[rr.nextInt(sub.length)];
        return arr;
    }

    /** 구역 하나(컬럼 없이) — 마스크 없이 센 값만. */
    @Test
    void bareSection_matchesVanillaSequence() throws Exception {
        int[][] pool = new int[1][];
        List<Line> lines = golden(pool);
        ChunkSection sec = null;
        for (Line l : lines) {
            switch (l.op) {
                case 'N' -> sec = new ChunkSection();
                case 'S' -> sec.setBlock(l.args[0], l.args[1], l.args[2], l.args[3]);
                case 'C' -> {
                    for (int i = 0; i < 4096; i++) sec.setBlock(i & 15, i >> 8, i >> 4 & 15, AIR);
                }
                case 'R' -> sec.replaceBlocks(fill(pool[0], l.args[0], l.args[1]), 0);
                default -> fail(l.text);
            }
            assertEquals(l.ticking, sec.isRandomlyTicking(), l.text);
        }
    }

    /** 컬럼 안의 구역 — 마스크 비트도 "대상이면 켜짐" 이어야 한다(꺼진 비트는 확실히 대상 아님). 판정은 센 값을 읽지 않는 쓰기 직후에도 맞아야 한다. */
    @Test
    void columnSection_matchesVanillaSequence_andMaskCovers() throws Exception {
        int[][] pool = new int[1][];
        List<Line> lines = golden(pool);
        ChunkColumn col = null;
        for (Line l : lines) {
            if (l.op == 'N' || col == null) col = new ChunkColumn(0);
            int baseY = col.minY() + (SEC << 4);
            switch (l.op) {
                case 'N' -> {}
                case 'S' -> col.setBlock(l.args[0], baseY + l.args[1], l.args[2], l.args[3]);
                case 'C' -> {
                    for (int i = 0; i < 4096; i++) col.setBlock(i & 15, baseY + (i >> 8), i >> 4 & 15, AIR);
                }
                case 'R' -> col.sections()[SEC].replaceBlocks(fill(pool[0], l.args[0], l.args[1]), 0);
                default -> fail(l.text);
            }
            // 마스크로 훑는 쪽(tickChunk 와 같은 길)이 이 구역을 놓치면 안 된다
            assertTrue(col.randomTickMaskCovers(), "마스크가 대상 구역을 놓침: " + l.text);
            boolean viaMask = col.nextRandomTickingSection(SEC) == SEC && col.sections()[SEC].isRandomlyTicking();
            assertEquals(l.ticking, viaMask, l.text);
            if (!l.ticking) assertTrue(col.nextRandomTickingSection(SEC) > SEC || !col.sections()[SEC].isRandomlyTicking(), l.text);
        }
    }

    /** 양동이 한 번: 빈 구역에 물을 놓으면 대상이 되고, 줍거나 흘러 사라지면 다시 대상이 아니다. */
    @Test
    void bucketWater_makesSectionRandomlyTicking() {
        var B = org.domi.furnace.server.block.Blocks.get();
        int water = B.defaultState("minecraft:water");
        ChunkSection sec = new ChunkSection();
        assertFalse(sec.isRandomlyTicking());
        sec.setBlock(3, 4, 5, water);
        assertTrue(sec.isRandomlyTicking(), "바닐라: 물을 놓은 구역은 isRandomlyTicking");
        sec.setBlock(3, 4, 5, AIR);
        assertFalse(sec.isRandomlyTicking());
    }
}
