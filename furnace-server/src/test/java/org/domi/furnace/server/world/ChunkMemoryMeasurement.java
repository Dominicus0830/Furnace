// 청크 palette 전환의 실 메모리 절감 측정(보관 retained heap/청크). live-AFK OOM의 정량 프록시.
package org.domi.furnace.server.world;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ChunkMemoryMeasurement {

    // 느린 전체생성 측정 — 평소 suite 부담 방지 위해 명시 실행 전용:
    //   ./gradlew :furnace-server:test --tests *ChunkMemoryMeasurement
    @Test
    void measureRetainedPerColumn() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(0L, blocks, registries);

        int N = 64; // 대표 표본(전체생성 비용 때문에 축소). bytes/column은 vd로 외삽.
        // warm-up + JIT
        for (int i = 0; i < 4; i++) source.generateWithFeatures(i, -i);

        long before = usedHeap();
        List<ChunkColumn> held = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            int cx = (i % 8) + 100;
            int cz = (i / 8) + 100;
            held.add(source.generateWithFeatures(cx, cz)); // 전체 파이프라인 → compact된 palette 컬럼
        }
        long after = usedHeap();

        long perColumn = (after - before) / N;
        long oldPerColumn = 24L * (4096 * 4 + 64 * 4); // int[4096]+int[64] × 24섹션 = ~399KB(객체헤더 제외)

        System.out.println("=== Chunk palette 메모리 측정 (n=" + N + " columns, generateWithFeatures) ===");
        System.out.printf("  palette retained:     %,d bytes/column (~%d KB)%n", perColumn, perColumn / 1024);
        System.out.printf("  구(int[]) 이론치:      %,d bytes/column (~%d KB)%n", oldPerColumn, oldPerColumn / 1024);
        System.out.printf("  절감 배수:            %.1f×%n", (double) oldPerColumn / Math.max(perColumn, 1));
        System.out.printf("  vd=16(1089청크) palette: ~%,d MB  vs  구: ~%,d MB%n",
                perColumn * 1089 / 1_048_576, oldPerColumn * 1089 / 1_048_576);
        System.out.printf("  vd=30(3721청크) palette: ~%,d MB  vs  구: ~%,d MB (구는 -Xmx3G 초과→OOM)%n",
                perColumn * 3721 / 1_048_576, oldPerColumn * 3721 / 1_048_576);

        // held 참조 유지(측정 후 GC 방지)
        if (held.size() != N) throw new IllegalStateException();
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 4; i++) { System.gc(); try { Thread.sleep(50); } catch (InterruptedException ignored) {} }
        return rt.totalMemory() - rt.freeMemory();
    }
}
