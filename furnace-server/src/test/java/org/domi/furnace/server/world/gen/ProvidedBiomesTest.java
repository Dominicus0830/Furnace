// T-203 3단계: 플러그인 바이옴 공급자(CraftBukkit CustomWorldChunkManager) — 청크 채우기 순서·좌표·기후 값, 나올 수 있는 바이옴, 틀린 바이옴 거절.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.worldgen.BiomeProvider;
import org.domi.furnace.api.worldgen.Climate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class ProvidedBiomesTest {

    static final BlockRegistry REG = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
    static final RegistrySet REGS = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");

    record Call(int x, int y, int z, double temperature, double minT, double maxT) {}

    /** 칸마다 기록하고, 온도로 두 바이옴 중 하나를 고른다. */
    static final class Recording implements BiomeProvider {
        final List<Call> calls = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public String biome(int x, int y, int z, Climate c) {
            double t = c.value(Climate.Parameter.TEMPERATURE);
            calls.add(new Call(x, y, z, t, c.min(Climate.Parameter.TEMPERATURE), c.max(Climate.Parameter.TEMPERATURE)));
            return t < 0 ? "minecraft:snowy_plains" : "minecraft:desert";
        }

        @Override
        public List<String> biomes() { return List.of("minecraft:desert", "minecraft:snowy_plains", "minecraft:desert"); }
    }

    @Test
    void chunkFillCallsTheProviderInVanillaOrderWithVanillaClimate() {
        NoiseWorldSource base = NoiseWorldSource.create(NoiseWorldSource.Dimension.OVERWORLD, 77L, REG, REGS);
        Recording p = new Recording();
        NoiseWorldSource n = base.freshCopy(77L, p);
        assertSame(p, n.biomeProvider());
        int desert = REGS.indexOf("minecraft:worldgen/biome", "minecraft:desert");
        int snowy = REGS.indexOf("minecraft:worldgen/biome", "minecraft:snowy_plains");
        assertEquals(List.of(desert, snowy), List.copyOf(n.possibleBiomeIds()), "공급자 순서, 겹침 없음(바닐라 possibleBiomes)");
        assertNull(n.fixedBiome());

        int cx = 3, cz = -2;
        int[] ids = n.sampleBiomes(cx, cz);
        assertEquals(24 * 64, p.calls.size());
        // 섹션 아래부터, 섹션 안에서 x 바깥 · y 가운데 · z 안쪽 — 좌표는 quart 모서리(블록)
        int i = 0;
        for (int section = 0; section < 24; section++)
            for (int qx = 0; qx < 4; qx++)
                for (int dy = 0; dy < 4; dy++)
                    for (int qz = 0; qz < 4; qz++) {
                        Call c = p.calls.get(i++);
                        int qy = section * 4 + dy;
                        assertEquals(cx * 16 + qx * 4, c.x());
                        assertEquals(-64 + qy * 4, c.y());
                        assertEquals(cz * 16 + qz * 4, c.z());
                        long[] t = base.climateTarget((cx * 16 + qx * 4) >> 2, (-64 + qy * 4) >> 2, (cz * 16 + qz * 4) >> 2);
                        assertEquals((float) t[0] / 10000.0F, c.temperature(), "바닐라 기후 값(Climate#unquantizeCoord)");
                        assertEquals(c.temperature() < 0 ? snowy : desert, ids[qy * 16 + qz * 4 + qx]);
                    }
        Call first = p.calls.get(0);
        assertTrue(first.minT() < 0 && first.maxT() > 0 && first.minT() < first.maxT(), "온도 밀도 함수의 범위");

        // 한 칸 질의(/locate·구조물 바이옴 확인)도 공급자를 그 블록 좌표로 부른다
        p.calls.clear();
        int id = n.noiseBiomeIdAtQuart(5, 10, -7);
        assertEquals(1, p.calls.size());
        assertEquals(20, p.calls.get(0).x());
        assertEquals(40, p.calls.get(0).y());
        assertEquals(-28, p.calls.get(0).z());
        assertTrue(id == desert || id == snowy);
    }

    @Test
    void unknownOrMissingBiomesAreRejectedLikeVanilla() {
        NoiseWorldSource base = NoiseWorldSource.create(NoiseWorldSource.Dimension.OVERWORLD, 1L, REG, REGS);
        var bad = assertThrows(IllegalArgumentException.class, () -> base.freshCopy(1L, new BiomeProvider() {
            @Override public String biome(int x, int y, int z, Climate c) { return "minecraft:plains"; }
            @Override public List<String> biomes() { return List.of("minecraft:plains", "nope:nothing"); }
        }));
        assertEquals("Cannot use the biome nope:nothing", bad.getMessage());
        NoiseWorldSource n = base.freshCopy(1L, new BiomeProvider() {
            @Override public String biome(int x, int y, int z, Climate c) { return null; }
            @Override public List<String> biomes() { return List.of("minecraft:plains"); }
        });
        var e = assertThrows(IllegalArgumentException.class, () -> n.sampleBiomes(0, 0));
        assertEquals("Cannot use the biome null", e.getMessage());
    }

    @Test
    void flatGeneratorsDoNotTakeABiomeProvider() {
        BiomeProvider p = new BiomeProvider() {
            @Override public String biome(int x, int y, int z, Climate c) { return "minecraft:plains"; }
            @Override public List<String> biomes() { return List.of("minecraft:plains"); }
        };
        NoiseWorldSource base = NoiseWorldSource.create(NoiseWorldSource.Dimension.OVERWORLD, 1L, REG, REGS);
        assertSame(base.biomeProvider(), base.freshCopy(2L).biomeProvider());
        NoiseWorldSource withP = base.freshCopy(2L, p);
        assertSame(p, withP.freshCopy(3L).biomeProvider(), "다시 복사해도 공급자를 옮긴다");
        assertThrows(UnsupportedOperationException.class, () -> new DebugWorldSource(REG, 0, -64, 24).freshCopy(1L, p));
    }
}
