package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.junit.jupiter.api.Test;

/** 월드젠 값 shim(영역 6): 바닐라(Paper) 1.21.1 의 동작과 같은 값을 낸다. 기준값은 바닐라 코드 읽기에서 온 것이다. */
class LevelgenValueShimTest {

    @Test
    void worldOptionsKeepsLegacyAndSeedRules() {
        WorldOptions o = new WorldOptions(42L, true, false);
        assertEquals(42L, o.withBonusChest(true).seed());
        assertTrue(o.withBonusChest(true).generateBonusChest());
        assertFalse(o.withStructures(false).generateStructures());
        assertEquals(7L, o.withSeed(OptionalLong.of(7L)).seed());
        assertFalse(o.isOldCustomizedWorld());
        assertEquals((long) "North Carolina".hashCode(), WorldOptions.DEMO_OPTIONS.seed());
        assertTrue(WorldOptions.DEMO_OPTIONS.generateStructures() && WorldOptions.DEMO_OPTIONS.generateBonusChest());
        WorldOptions d = WorldOptions.defaultWithRandomSeed();
        assertTrue(d.generateStructures());
        assertFalse(d.generateBonusChest());
    }

    @Test
    void parseSeedFollowsVanilla() {
        assertEquals(OptionalLong.empty(), WorldOptions.parseSeed("   "));
        assertEquals(OptionalLong.of(-12L), WorldOptions.parseSeed(" -12 "));
        assertEquals(OptionalLong.of((long) "hello".hashCode()), WorldOptions.parseSeed("hello"));
    }

    @Test
    void belowZeroRetrogenUsesBitSetAndVanillaNames() {
        BitSet bits = new BitSet();
        bits.set(4 * 16 + 3);
        BelowZeroRetrogen r = BelowZeroRetrogen.furnaceCreate(ChunkStatus.SPAWN, Optional.of(bits));
        assertTrue(r.hasBedrockHoles());
        assertTrue(r.hasBedrockHole(3, 4));
        assertTrue(r.hasBedrockHole(3 + 16, 4 + 32));   // 청크 안 칸으로 접는다
        assertFalse(r.hasBedrockHole(4, 3));
        assertFalse(BelowZeroRetrogen.furnaceCreate(ChunkStatus.SPAWN, Optional.empty()).hasBedrockHoles());
        assertFalse(BelowZeroRetrogen.furnaceCreate(ChunkStatus.SPAWN, Optional.of(new BitSet())).hasBedrockHoles());
    }

    /** 기대값은 Paper 1.21.1 서버 jar 의 같은 호출에서 뽑았다(씨앗 12345·777, 호출 순서 포함). */
    @Test
    void positionalRandomMatchesVanillaValues() {
        var f = new net.minecraft.world.level.levelgen.LegacyRandomSource(12345L).forkPositional();
        StringBuilder sb = new StringBuilder();
        f.parityConfigString(sb);
        assertEquals("LegacyPositionalRandomFactory{6674089274190705457}", sb.toString());
        var r = f.at(10, 64, -33);
        assertEquals(39, r.nextInt(100));
        assertEquals(-880906120772830161L, r.nextLong());
        assertEquals(0.6009447107729808, r.nextDouble());
        assertEquals(-1.525278903530697, r.nextGaussian());
        var h = f.fromHashOf("minecraft:test");
        assertEquals(712883558, h.nextInt());
        assertEquals(0.7488483F, h.nextFloat());
        assertEquals(-5119754439980850796L, f.fromSeed(99L).nextLong());
        var g = new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(777L).forkPositional();
        sb = new StringBuilder();
        g.parityConfigString(sb);
        assertEquals("LegacyPositionalRandomFactory{-5400676711371204147}", sb.toString());
        assertEquals(126, g.at(-5, 3, 8).nextInt(1000));
        assertEquals(60552680979424L, net.minecraft.util.Mth.getSeed(10, 64, -33));
        // 음수·큰 좌표(바닐라 jar 에서 뽑은 값)
        var far = f.at(-30000000, -64, 29999999);
        assertEquals(404, far.nextInt(1000));
        assertEquals(4665108934479365513L, far.nextLong());
        var edge = f.at(Integer.MAX_VALUE, 319, Integer.MIN_VALUE);
        assertEquals(1712030472, edge.nextInt());
        assertEquals(0.12612254894948727, edge.nextDouble());
    }

    /** 기대값은 Paper 1.21.1 서버 jar 에서 같은 호출 순서로 뽑았다. */
    @Test
    void xoroshiroMatchesVanillaValues() {
        var x = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(4242L);
        assertEquals(-432114983, x.nextInt());
        assertEquals(-6283982576133760119L, x.nextLong());
        assertFalse(x.nextBoolean());
        assertEquals(0.90182537F, x.nextFloat());
        assertEquals(0.7480286890924016, x.nextDouble());
        assertEquals(1.1248003528371084, x.nextGaussian());
        assertEquals(-2.470761187631881, x.nextGaussian());

        var y = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(-99L);
        int[] expected = {1184397722, 2, 1326755291, 4, 1201722659, 4, 1392881024, 6, 1081608965, 2, 448309291, 0, 1035999086, 6, 485275790, 3};
        for (int i = 0; i < expected.length; i += 2) {   // 큰 범위(거절 표집이 일어날 수 있는 bound)와 작은 범위를 번갈아
            assertEquals(expected[i], y.nextInt(2000000011));
            assertEquals(expected[i + 1], y.nextInt(7));
        }
        var z = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(-99L);
        for (int i = 0; i < 80; i++) z.nextInt(i % 2 == 0 ? 2000000011 : 7);   // 같은 40쌍을 소비한 뒤
        z.consumeCount(5);
        assertEquals(2016221783619074093L, z.nextLong());
        z.setSeed(1L);
        assertEquals(-1033667707219518978L, z.nextLong());
        var f = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(5L);
        var fk = f.fork();
        assertEquals(2176809087315743652L, fk.nextLong());
        assertEquals(-3440957986124107217L, f.nextLong());
        var pf = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(7L).forkPositional();
        StringBuilder sb = new StringBuilder();
        pf.parityConfigString(sb);
        assertEquals("seedLo: -427546222996335712, seedHi: -6591573495626427012", sb.toString());
        assertEquals(611195606777952842L, pf.at(-3, 70, 12).nextLong());
        assertEquals(-6051231260954320192L, pf.fromHashOf("minecraft:ore").nextLong());
        assertEquals(2426626644426068205L, pf.fromSeed(31L).nextLong());
        assertEquals(6807859099481836695L, new net.minecraft.world.level.levelgen.XoroshiroRandomSource(0L, 0L).nextLong());   // 전부 0 은 상수로 바뀐다
        var u = net.minecraft.world.level.levelgen.RandomSupport.upgradeSeedTo128bit(123L);
        assertEquals(1592121443578202627L, u.seedLo());
        assertEquals(3495113926647197287L, u.seedHi());
        var h = net.minecraft.world.level.levelgen.RandomSupport.seedFromHashOf("minecraft:test");
        assertEquals(1885711875966874721L, h.seedLo());
        assertEquals(3357099975631595687L, h.seedHi());
    }

    @Test
    void threadSafeLegacyMatchesVanillaValues() {
        var t = new net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource(2024L);
        assertEquals(-1638729195, t.nextInt());
        assertEquals(26, t.nextInt(100));
        assertEquals(3473487682246065932L, t.nextLong());
        assertEquals(0.9309788145598492, t.nextDouble());
        assertEquals(-0.8513514528383382, t.nextGaussian());
        StringBuilder sb = new StringBuilder();
        t.forkPositional().parityConfigString(sb);
        assertEquals("LegacyPositionalRandomFactory{9114417708509829518}", sb.toString());
        assertEquals(-1258329677, t.fork().nextInt());
    }

    /** 여러 스레드가 동시에 불러도 갱신이 사라지지 않는다: 값 모음이 한 스레드로 같은 횟수 부른 것과 같고, 끝 상태도 같다. */
    @Test
    void threadSafeLegacyLosesNoUpdatesUnderContention() throws Exception {
        int threads = 8;
        int perThread = 20_000;
        var shared = new net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource(31337L);
        var seen = new java.util.concurrent.ConcurrentLinkedQueue<Integer>();
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) seen.add(shared.nextInt());
                    return null;
                }));
            }
            start.countDown();
            for (var f : futures) f.get();
        } finally {
            pool.shutdownNow();
        }
        var single = new net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource(31337L);
        var expected = new java.util.ArrayList<Integer>();
        for (int i = 0; i < threads * perThread; i++) expected.add(single.nextInt());
        var actual = new java.util.ArrayList<>(seen);
        java.util.Collections.sort(expected);
        java.util.Collections.sort(actual);
        assertEquals(expected, actual);
        assertEquals(single.nextLong(), shared.nextLong());
    }
}
