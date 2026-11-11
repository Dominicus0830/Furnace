// A06-04 뒤쪽 검토(PR #225): 평지 요새 링 시드(createForFlat = 0), generator-settings 코덱의 resultOrPartial·lenient overrides.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.junit.jupiter.api.Test;

class FlatRingsAndSettingsTest {
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final RegistrySet REGS = RegistrySet.load("registries/1.21.1.txt");

    private static NoiseWorldSource flat(long seed) {
        return NoiseWorldSource.flat(seed, REG, REGS,
                new NoiseWorldSource.FlatSpec(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", false, false,
                        List.of("minecraft:strongholds", "minecraft:villages")));
    }

    /**
     * 바닐라 dva.a(jm, ekl)(generateRingPositions) + deh.a(findBiomeHorizontal) 를 바이트코드대로 옮긴 것.
     * ringsSeed = dva.e(concentricRingsSeed): createForFlat(dyt.a) 은 lconst_0, createForNormal 은 levelSeed.
     */
    private static long[] vanillaFixedRings(long ringsSeed, StructureRegistry.ConcentricRings p, boolean biomeMatches) {
        LegacyRandom random = new LegacyRandom(ringsSeed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int distance = p.distance(), count = p.count(), spread = p.spread();
        int inRing = 0, ring = 0;
        long[] out = new long[count];
        for (int n = 0; n < count; n++) {
            double dist = (double) (4 * distance + distance * ring * 6) + (random.nextDouble() - 0.5) * (double) distance * 2.5;
            int rx = (int) Math.round(Math.cos(angle) * dist), rz = (int) Math.round(Math.sin(angle) * dist);
            LegacyRandom fork = new LegacyRandom(random.nextLong());
            int cx = rx, cz = rz;
            if (biomeMatches) {
                int x = rx * 16 + 8, z = rz * 16 + 8, r = 112;
                int bx = x - r + fork.nextInt(r * 2 + 1);
                int bz = z - r + fork.nextInt(r * 2 + 1);
                cx = bx >> 4; cz = bz >> 4;
            }
            out[n] = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
            angle += (Math.PI * 2.0) / (double) spread;
            inRing++;
            if (inRing == spread) {
                ring++;
                inRing = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - n);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        return out;
    }

    private static StructureRegistry.SetDef strongholds(NoiseWorldSource g) {
        return g.structureEngine().registry().sets().get("minecraft:strongholds");
    }

    /** R1: 바닐라 평지는 요새 링 시드가 0 이라 월드 시드와 무관하다. */
    @Test
    void flatStrongholdRingsUseZeroSeed() {
        NoiseWorldSource g = flat(1234L);
        var set = strongholds(g);
        long[] expected = vanillaFixedRings(0L, set.rings(), true);
        long[] actual = g.structureEngine().ringPositions(set);
        assertEquals(Arrays.toString(Arrays.copyOf(expected, 3)), Arrays.toString(Arrays.copyOf(actual, 3)), "첫 링 세 개");
        assertArrayEquals(expected, actual);
    }

    @Test
    void flatStrongholdRingsSeedIndependent() {
        long[] a = flat(1L).structureEngine().ringPositions(strongholds(flat(1L)));
        long[] b = flat(987654321L).structureEngine().ringPositions(strongholds(flat(987654321L)));
        assertArrayEquals(a, b, "바닐라 평지 요새 위치는 시드와 무관");
    }

    /** 대조군: single_biome_surface(createForNormal)는 월드 시드를 쓴다 — PR 의 고정 바이옴 식 자체는 맞다. */
    @Test
    void singleBiomeRingsUseLevelSeed() {
        NoiseWorldSource g = NoiseWorldSource.overworld(1234L, REG, REGS, "minecraft:overworld", "minecraft:plains");
        var set = strongholds(g);
        assertArrayEquals(vanillaFixedRings(1234L, set.rings(), true), g.structureEngine().ringPositions(set));
    }

    private static NoiseWorldSource.FlatSpec spec(String json) {
        return SuperflatWorldSource.flatSpecOf(SuperflatWorldSource.settingsNbt(com.google.gson.JsonParser.parseString(json).getAsJsonObject()));
    }

    /** R2: 높이 합 > 4064 는 DataResult.error(msg, partial) — resultOrPartial 이 그 설정을 그대로 쓴다(기본 프리셋 아님). */
    @Test
    void tooTallSumKeepsPartial() {
        var s = spec("{\"layers\":[{\"block\":\"minecraft:stone\",\"height\":2000},{\"block\":\"minecraft:stone\",\"height\":2065}],\"biome\":\"minecraft:desert\"}");
        assertEquals("minecraft:desert", s.biome());
        assertEquals(List.of(new SuperflatWorldSource.Layer("minecraft:stone", 2000), new SuperflatWorldSource.Layer("minecraft:stone", 2065)), s.layers());
        assertNull(s.structureOverrides());
    }

    /** R2: 한 층 높이가 intRange(0,4064) 밖이면 그 층만 빠진다(부분 결과 없음 → ListCodec 이 건너뜀). */
    @Test
    void outOfRangeLayerDropped() {
        var s = spec("{\"layers\":[{\"block\":\"minecraft:stone\",\"height\":4065}]}");
        assertEquals(List.of(), s.layers());
        var neg = spec("{\"layers\":[{\"block\":\"minecraft:stone\",\"height\":-1},{\"block\":\"minecraft:bedrock\",\"height\":1}]}");
        assertEquals(List.of(new SuperflatWorldSource.Layer("minecraft:bedrock", 1)), neg.layers());
    }

    /** R2: height 없는 층은 빠지고(서버가 NPE 로 죽지 않는다), block 없는 층은 공기. */
    @Test
    void missingFieldsDoNotCrash() {
        var noHeight = assertDoesNotThrow(() -> spec("{\"layers\":[{\"block\":\"minecraft:stone\"},{\"block\":\"minecraft:bedrock\",\"height\":1}]}"));
        assertEquals(List.of(new SuperflatWorldSource.Layer("minecraft:bedrock", 1)), noHeight.layers());
        var noBlock = assertDoesNotThrow(() -> spec("{\"layers\":[{\"height\":2}]}"));
        assertEquals(List.of(new SuperflatWorldSource.Layer("minecraft:air", 2)), noBlock.layers());
    }

    /** 참고: structure_overrides 에 모르는 id 가 있으면 lenientOptionalFieldOf → Optional.empty = 레지스트리 전부. */
    @Test
    void unknownOverrideMeansAllSets() {
        var s = spec("{\"layers\":[{\"block\":\"minecraft:stone\",\"height\":3}],\"structure_overrides\":[\"minecraft:villages\",\"minecraft:nope\"]}");
        assertNull(s.structureOverrides());
    }
}
