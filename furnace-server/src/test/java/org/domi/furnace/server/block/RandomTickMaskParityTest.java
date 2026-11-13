// T-053: tickChunk 의 구역 무작위 틱 루프(마스크로 꺼진 구역 건너뛰기)가 옛 루프(LegacyRandomTickSections, 8dea6629)와
// 같은 결과를 내는지 — 같은 씨앗의 레벨 두 개에 같은 장면(무작위 틱 블록·구역 경계에서 위 구역으로 자라는 사탕수수·틱 사이 블록 바꾸기)을
// 놓고 틱을 돌린 뒤 블록 전부·레벨 난수·randValue 를 비교한다. 변이 점검: 틱 머리에 마스크를 굳혀 쓰면(같은 틱에 위 구역으로 자란 것을
// 그 틱에 못 돌림) 이 차분이 잡아야 한다.
package org.domi.furnace.server.block;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RandomTickMaskParityTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Blocks B = Blocks.get();
    private static int[] ticking;

    private static void init() {
        if (ticking != null) return;
        BlockBehaviors.ofBlock(0);
        List<Integer> t = new ArrayList<>();
        for (int s = 0; s < B.stateCount(); s += 5) if (BlockBehaviors.of(s).isRandomlyTicking(s)) t.add(s);
        ticking = t.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 구역 루프 하나: (레벨, 컬럼, x0, z0, 속도). */
    @FunctionalInterface
    interface SectionLoop {
        void run(ChunkLevel level, ChunkColumn col, int x0, int z0, int speed);
    }

    /** 장면 입력: 씨앗과 크기. 각 구현이 이것으로 자기 레벨을 새로 만든다. */
    private record Scene(long seed, int canes, int sprinkles, int speed, int ticks, int editsPerTick) {
        String summary() { return "seed=" + seed + " canes=" + canes + " sprinkle=" + sprinkles + " speed=" + speed + " ticks=" + ticks; }
    }

    private static Scene scene(SplittableRandom r) {
        init();
        return new Scene(r.nextLong(), 20 + r.nextInt(60), r.nextInt(80), 1 + r.nextInt(r.nextBoolean() ? 8 : 120), 10 + r.nextInt(20), r.nextInt(4));
    }

    private static void seedLevel(ChunkLevel level, long seed) throws Exception {
        ((LegacyRandomSource) level.random()).setSeed(seed);
        Field soundRandom = ChunkLevel.class.getDeclaredField("soundRandom");
        soundRandom.setAccessible(true);
        ((LegacyRandomSource) soundRandom.get(level)).setSeed(~seed);
        Field rv = ChunkLevel.class.getDeclaredField("randValue");
        rv.setAccessible(true);
        rv.setInt(level, (int) (seed >>> 17));
    }

    private static int randValue(ChunkLevel level) throws Exception {
        Field rv = ChunkLevel.class.getDeclaredField("randValue");
        rv.setAccessible(true);
        return rv.getInt(level);
    }

    /** 장면을 만들고 틱을 돌린 뒤 결과 지문(청크 -1..1 블록 해시들, 레벨 난수 다음 값, randValue). */
    private static List<Long> play(Scene sc, SectionLoop loop) throws Exception {
        BlockPlacementTest.TestLevel level = new BlockPlacementTest.TestLevel();
        seedLevel(level, sc.seed);
        SplittableRandom r = new SplittableRandom(sc.seed);
        int dirt = B.defaultState("minecraft:dirt"), water = B.defaultState("minecraft:water");
        int caneTop = B.prop("age").withInt(B.defaultState("minecraft:sugar_cane"), 15);
        // 구역 0(y -64..-49) 꼭대기에 다 자란 사탕수수: 흙 기둥(-60..-50) 위 -49. 위 칸 -48 은 구역 1(평지에선 공기뿐이라 비트가 꺼져 있다)
        for (int i = 0; i < sc.canes; i++) {
            int cx = r.nextInt(2), x = (cx << 4) + 1 + r.nextInt(14), z = 1 + r.nextInt(14);
            for (int y = -60; y <= -50; y++) level.setBlock(new Pos(x, y, z), dirt, 2 | 16);
            level.setBlock(new Pos(x + 1, -50, z), water, 2 | 16);
            level.setBlock(new Pos(x, -49, z), caneTop, 2 | 16);
        }
        for (int i = 0; i < sc.sprinkles; i++) {
            int x = r.nextInt(32), z = r.nextInt(16), y = -64 + r.nextInt(6 * 16);
            level.setBlock(new Pos(x, y, z), ticking[r.nextInt(ticking.length)], 2 | 16);
        }
        for (int t = 0; t < sc.ticks; t++) {
            for (int cx = 0; cx < 2; cx++) loop.run(level, level.column(cx, 0), cx << 4, 0, sc.speed);
            for (int e = 0; e < sc.editsPerTick; e++) {   // 틱 사이 블록 바꾸기: 무작위 틱 상태를 놓거나 지운다(비트 켜짐·꺼짐)
                int x = r.nextInt(32), z = r.nextInt(16), y = -64 + r.nextInt(6 * 16);
                int st = r.nextInt(3) == 0 ? ticking[r.nextInt(ticking.length)] : ChunkSection.AIR;
                level.setBlock(new Pos(x, y, z), st, 2 | 16);
            }
        }
        List<Long> out = new ArrayList<>();
        for (int cx = -1; cx <= 2; cx++) for (int cz = -1; cz <= 1; cz++) {
            ChunkColumn col = level.column(cx, cz);
            long h = IdentityHash.OFFSET;
            for (int y = col.minY(); y <= col.maxY(); y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) h = IdentityHash.fnv(h, col.getBlock(x, y, z));
            out.add(h);
        }
        out.add(level.random().nextLong());
        out.add((long) randValue(level));
        return out;
    }

    private static Differential<Scene, List<Long>> diff() {
        return Differential.<Scene, List<Long>>of("random-tick-section-loop", RandomTickMaskParityTest::scene)
                .seed(0x7053_0002L).iterations(24).describe(Scene::summary);
    }

    @Test
    void maskedLoopMatchesLegacyLoop() {
        diff().check(sc -> play(sc, LegacyRandomTickSections::run), sc -> play(sc, ChunkLevel::randomTickSections));
    }

    /** 변이: 틱 머리에 마스크를 굳혀 쓴다(같은 틱에 위 구역으로 자란 사탕수수 구역을 그 틱에 못 돌림). */
    @Test
    void detectsMaskFrozenAtTickStart() {
        diff().assertDetects(sc -> play(sc, LegacyRandomTickSections::run), sc -> play(sc, (level, col, x0, z0, speed) -> {
            ChunkSection[] sections = col.sections();
            long m = col.randomTickMask();
            for (int si = 0; si < sections.length; si++) {
                if (si < 64 && (m & (1L << si)) == 0) continue;
                ChunkSection sec = sections[si];
                if (!sec.isRandomlyTicking()) continue;
                int y0 = col.minY() + (si << 4);
                for (int n = 0; n < speed; n++) {
                    Pos p = level.blockRandomPos(x0, y0, z0);
                    int st = sec.getBlock(p.x() - x0, p.y() - y0, p.z() - z0);
                    BlockBehavior b = BlockBehaviors.of(st);
                    if (b.isRandomlyTicking(st)) b.randomTick(st, level, p, level.random());
                    int f = b.getFluidState(st);
                    if (Fluids.isLava(f)) FluidTicker.randomTick(level, p, f, level.random());
                }
            }
        }));
    }
}
