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
}
