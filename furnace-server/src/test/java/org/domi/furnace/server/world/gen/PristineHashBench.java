package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.identity.Golden;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

// 최적화 전후 동일성 확인용: pristine(지형+표면+카버) 블록·바이옴 해시와 청크당 시간. -Dfurnace.profile=true 일 때만 돈다.
// 해시는 금 파일(identity/worldgen-pristine.txt, identity/worldgen-structure-starts.txt)과 비교한다(IdentityKit) — 예전처럼 출력만 하지 않는다.
class PristineHashBench {
    @Test
    void hashAndTime() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.profile"));
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(12345L, blocks, registries);
        for (int i = 0; i < 40; i++) { var g = source.generateWorldChunk(-300 + i, 300); source.applyCarvers(g, -300 + i, 300); }
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int round = 0; round < 2; round++) {
            long hash = 1469598103934665603L;
            long t = System.nanoTime();
            int n = 0;
            for (int x = -6; x < 6; x++) for (int z = -6; z < 6; z++) {
                int cx = x * 7 + round * 1000, cz = z * 5;
                var g = source.generateWorldChunk(cx, cz);
                source.applyCarvers(g, cx, cz);
                ChunkColumn col = g.col();
                for (int y = col.minY(); y <= col.maxY(); y++)
                    for (int bz = 0; bz < 16; bz++) for (int bx = 0; bx < 16; bx++)
                        hash = (hash ^ col.getBlock(bx, y, bz)) * 1099511628211L;
                for (int b : g.biomeIds()) hash = (hash ^ b) * 1099511628211L;
                n++;
            }
            System.out.printf("PRISTINE_HASH round=%d %016x chunks=%d %.2f ms/chunk%n", round, hash, n, (System.nanoTime() - t) / 1e6 / n);
            lines.add(String.format("pristine overworld 12345 round=%d chunks=%d %016x", round, n, hash));
        }
        Golden.assertMatches("worldgen-pristine", lines);
    }

    @Test
    void structureStarts() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.profile"));
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (long seed : new long[] {12345L, 51532L}) {
            NoiseWorldSource source = NoiseWorldSource.overworld(seed, blocks, registries);
            long t = System.nanoTime();
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (int cx = -40; cx < 40; cx++) for (int cz = -40; cz < 40; cz++) {
                for (var s : source.structureStartsFresh(cx, cz)) {
                    var b = s.boundingBox();
                    sb.append(s.structureId()).append(cx).append(',').append(cz).append(b.minX()).append(b.minY()).append(b.minZ())
                        .append(b.maxX()).append(b.maxY()).append(b.maxZ()).append(s.pieces().size()).append(';');
                    count++;
                }
            }
            System.out.printf("STRUCT_HASH seed=%d %08x starts=%d %.0f ms%n", seed, sb.toString().hashCode(), count, (System.nanoTime() - t) / 1e6);
            // String.hashCode 는 내용으로 정해진다(JVM 무관). 줄 목록 해시도 같이 남겨 충돌을 줄인다.
            lines.add(String.format("structures overworld %d starts=%d %08x %s", seed, count, sb.toString().hashCode(),
                    new org.domi.furnace.server.identity.IdentityHash().add(sb.toString()).hex()));
        }
        Golden.assertMatches("worldgen-structure-starts", lines);
    }
}
