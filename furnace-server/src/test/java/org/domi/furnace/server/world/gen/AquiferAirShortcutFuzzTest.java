// 감사 W-10 fuzz 게이트 — NoiseBasedAquifer 공기 빠른 길(y ≥ 후보 12칸 최대 수위 + 5 → 공기)이 잡은 모든 블록에서
// 바닐라 이식 본문(computeSubstanceSlow)도 공기(0)를 내는지.
// 기본: seed 1개 × 청크 20개. 전체(seed 10개 × 청크 100개 = 1000청크, 약 37 s)는 -Dfurnace.slowTests=true 일 때만.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class AquiferAirShortcutFuzzTest {

    @Test
    void quick() throws Exception {
        run(1, 20, 500_000);
    }

    @Test
    @EnabledIfSystemProperty(named = "furnace.slowTests", matches = "true")
    void full() throws Exception {
        run(10, 100, 10_000_000);
    }

    private static void run(int seeds, int chunks, long minHits) throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        Random rng = new Random(0xA9F1L);
        NoiseBasedAquifer.shortcutHits.set(0);
        NoiseBasedAquifer.shortcutMismatches.set(0);
        NoiseBasedAquifer.verifyShortcut = true;
        try {
            for (int s = 0; s < seeds; s++) {
                NoiseWorldSource source = NoiseWorldSource.overworld(rng.nextLong(), blocks, registries);
                for (int c = 0; c < chunks; c++) {
                    source.generate(rng.nextInt(20000) - 10000, rng.nextInt(20000) - 10000);
                }
            }
        } finally {
            NoiseBasedAquifer.verifyShortcut = false;
        }
        long hits = NoiseBasedAquifer.shortcutHits.get();
        assertTrue(hits > minHits, "빠른 길 적중이 너무 적다: " + hits);
        assertEquals(0, NoiseBasedAquifer.shortcutMismatches.get(),
            "빠른 길이 공기라고 한 블록 중 바닐라 이식 본문이 공기가 아닌 것이 있다 (적중 " + hits + ")");
        System.out.println("AQ_SHORTCUT_FUZZ seeds=" + seeds + " chunks=" + chunks + " hits=" + hits);
    }
}
