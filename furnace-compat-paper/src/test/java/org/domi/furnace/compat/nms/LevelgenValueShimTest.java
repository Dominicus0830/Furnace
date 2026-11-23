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
}
