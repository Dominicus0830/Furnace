// T-012 측정(:furnace-server:bench 에서만 돈다): 노이즈 오버월드 실제 청크(지형 장식 포함)로
//  1) 빛 층 메모리 — 청크마다 가진(공유 아닌) 층 수. 예전에는 늘 52층(하늘·블록 26층씩, 2048 바이트) = 104 KiB.
//  2) 디스크 청크 올리기의 생성 풀 몫 — fromNbt + precomputeLight: 저장 빛 읽기(하늘빛 원천만 셈) vs 표시 없는 청크(다시 계산).
// 한 스레드 CPU·할당을 청크당으로 적는다. 라운드 5회, 앞 2회는 예열. 틱 스레드의 가장자리 맞추기(attachPrecomputed)는 두 경로가 같다.
package org.domi.furnace.server.light;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class LightLoadBench {

    static volatile long sink;
    static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Test
    void bench() {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        var biomes = registries.entries("minecraft:worldgen/biome");
        AnvilChunkCodec codec = new AnvilChunkCodec(blocks, ItemRegistry.load("items/1.21.1.txt"), biomes, biomes.indexOf("minecraft:plains"),
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        NoiseWorldSource source = NoiseWorldSource.overworld(12345L, blocks, registries);
        int side = Integer.getInteger("furnace.bench.side", 6);
        int bedrock = org.domi.furnace.server.block.Blocks.get().defaultState("minecraft:bedrock");
        List<NbtCompound> stored = new ArrayList<>(), unmarked = new ArrayList<>();
        List<int[]> pos = new ArrayList<>();
        long owned = 0;
        for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) {
            int cx = 100 + x, cz = -40 + z;
            ChunkColumn c = source.generateWithFeatures(cx, cz);
            ChunkLevel.precomputeLight(c, cx, cz, bedrock, true);
            owned += c.light().ownedLayers();
            NbtCompound n = codec.toNbt(c, cx, cz);
            stored.add(n);
            NbtCompound u = n.copy();
            u.tags().remove(AnvilChunkCodec.LIGHT_VERSION_KEY);
            unmarked.add(u);
            pos.add(new int[]{cx, cz});
        }
        int n = stored.size();
        double avgOwned = (double) owned / n;
        System.out.printf("LIGHT_BENCH chunks=%d ownedLayers/chunk=%.1f of %d (%.1f KiB vs %.1f KiB)%n", n, avgOwned, 2 * ChunkLight.SECTIONS,
                avgOwned * 2, 2 * ChunkLight.SECTIONS * 2.0);
        long tid = Thread.currentThread().getId();
        for (int round = 0; round < 5; round++) {
            double[][] r = new double[2][];
            for (int mode = 0; mode < 2; mode++) {
                List<NbtCompound> src = mode == 0 ? stored : unmarked;
                long a0 = MX.getThreadAllocatedBytes(tid), c0 = MX.getCurrentThreadCpuTime();
                for (int i = 0; i < n; i++) {
                    ChunkColumn col = codec.fromNbt(src.get(i));
                    ChunkLevel.precomputeLight(col, pos.get(i)[0], pos.get(i)[1], bedrock, true);
                    sink += col.light().ownedLayers();
                }
                long c1 = MX.getCurrentThreadCpuTime(), a1 = MX.getThreadAllocatedBytes(tid);
                r[mode] = new double[]{(c1 - c0) / 1000.0 / n, (a1 - a0) / 1024.0 / n};
            }
            System.out.printf("LIGHT_BENCH round %d%s: stored %.0f us %.0f KiB | recompute %.0f us %.0f KiB (per chunk, fromNbt+precomputeLight)%n",
                    round, round < 2 ? " (warmup)" : "", r[0][0], r[0][1], r[1][0], r[1][1]);
        }
    }
}
