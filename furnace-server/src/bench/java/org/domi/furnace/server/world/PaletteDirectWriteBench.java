// T-015 벤치: 섹션 팔레트를 펼쳐서 쓰던 옛 경로와 묶인 팔레트에서 바로 쓰는 새 경로를 교대로 잰다(전송 섹션 부분·디스크 toNbt).
// 노이즈 오버월드 완성 청크 9개, 그 디스크 왕복 사본, 사본을 편집한 것(저장 순 ≠ 첫 등장 순 → 다시 매기는 갈래). 결과 바이트가 같은지도 매번 확인한다.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.junit.jupiter.api.Test;

class PaletteDirectWriteBench {

    static volatile long sink;

    private static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Test
    void oldVsNew() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var biomes = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
        var codec = new AnvilChunkCodec(reg, ItemRegistry.load("items/1.21.1.txt"), biomes,
                biomes.indexOf("minecraft:plains"), ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        // 노이즈 오버월드 3×3 완성 청크(생성 그대로), 디스크 왕복한 사본, 그 사본을 편집한 것
        var source = org.domi.furnace.server.world.gen.NoiseWorldSource.overworld(12345L, reg, RegistrySet.load("registries/1.21.1.txt"));
        List<ChunkColumn> plain = new ArrayList<>(), loaded = new ArrayList<>(), edited = new ArrayList<>();
        int[] extra = {reg.defaultStateId("minecraft:torch"), reg.defaultStateId("minecraft:oak_planks"),
                reg.defaultStateId("minecraft:glass"), reg.defaultStateId("minecraft:chest")};
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            ChunkColumn gen = source.generateWithFeatures(100 + x, -40 + z);
            plain.add(gen);
            loaded.add(codec.fromNbt(codec.toNbt(ChunkSnapshot.forSave(100 + x, -40 + z, gen))));
            ChunkColumn col = codec.fromNbt(codec.toNbt(ChunkSnapshot.forSave(100 + x, -40 + z, gen)));
            // 플레이어가 고친 땅 꼴: 섹션마다 칸 몇 개를 새 상태로(팔레트 끝에 붙어 첫 등장 순이 깨질 수 있다)
            SplittableRandom r = new SplittableRandom(x * 31L + z);
            for (int s = 0; s < ChunkColumn.SECTION_COUNT; s++) {
                if (col.sections()[s].hasOnlyAir()) continue;
                for (int k = 0; k < 6; k++) col.setBlock(r.nextInt(16), ChunkColumn.MIN_Y + s * 16 + r.nextInt(16), r.nextInt(16), extra[r.nextInt(extra.length)]);
            }
            edited.add(col);
        }
        int direct = reg.directBits();
        for (var set : List.of(plain, loaded, edited)) {
            String label = set == plain ? "생성 청크" : set == loaded ? "디스크 청크" : "편집한 청크";
            // 바이트 같음 확인
            for (ChunkColumn c : set) {
                ChunkSnapshot a = ChunkSnapshot.of(0, 0, c);
                if (!Arrays.equals(sections(a, direct, true), sections(a, direct, false))) throw new AssertionError("전송 바이트 다름");
                ChunkSnapshot s = ChunkSnapshot.forSave(0, 0, c);
                if (!Arrays.equals(NbtIo.toBytes(codec.toNbt(lazyFlattened(s)), ""), NbtIo.toBytes(codec.toNbt(s), ""))) throw new AssertionError("저장 바이트 다름");
            }
            for (int round = 0; round < 5; round++) {
                for (int mode = 0; mode < 2; mode++) {
                    boolean old = mode == 0;
                    Runnable wire = () -> {
                        for (ChunkColumn c : set) sink += sections(ChunkSnapshot.of(0, 0, c), direct, old).length;
                    };
                    Runnable save = () -> {
                        for (ChunkColumn c : set) {
                            ChunkSnapshot s = ChunkSnapshot.forSave(0, 0, c);
                            sink += codec.toNbt(old ? lazyFlattened(s) : s).tags().size();
                        }
                    };
                    report(label + " 전송(스냅샷+섹션)", old, round, wire, set.size());
                    report(label + " 저장(forSave+toNbt)", old, round, save, set.size());
                }
            }
        }
    }

    private static void report(String what, boolean old, int round, Runnable r, int chunks) {
        int iters = 400;
        for (int i = 0; i < iters; i++) r.run();
        long t0 = MX.getCurrentThreadAllocatedBytes(), n0 = System.nanoTime();
        for (int i = 0; i < iters; i++) r.run();
        long ns = System.nanoTime() - n0, bytes = MX.getCurrentThreadAllocatedBytes() - t0;
        System.out.printf("[BENCH] T-015 %s round=%d %s %.1f us/청크, %.1f KB/청크%n", what, round, old ? "옛" : "새",
                ns / 1000.0 / iters / chunks, bytes / 1024.0 / iters / chunks);
    }

    /** 옛 경로 그대로: 섹션마다 펼치고(Section.blocks) 펼친 배열을 쓴다. 새 경로: ChunkSerializer.writeSection. */
    static byte[] sections(ChunkSnapshot snap, int direct, boolean old) {
        int biomeDirect = ChunkColumn.DEFAULT_BIOME_DIRECT_BITS;
        ByteBuf data = Unpooled.buffer(16384);
        for (ChunkSnapshot.Section s : snap.sections()) {
            if (old) {
                data.writeShort(s.nonAirCount());
                Palette.write(data, s.blocks(), direct, 4, 8);
                Palette.write(data, s.biomes(), biomeDirect, 1, 3);
            } else {
                org.domi.furnace.server.net.protocol.play.ChunkSerializer.writeSection(data, s, direct, biomeDirect);   // 운영과 같은 메서드
            }
        }
        byte[] out = new byte[data.readableBytes()];
        data.readBytes(out);
        data.release();
        return out;
    }

    /** 옛 저장 경로와 같은 일: 섹션을 펼친 배열(복사 없음)만 가진 섹션으로 — toNbt 가 blockStatesFromArray 를 탄다. */
    static ChunkSnapshot lazyFlattened(ChunkSnapshot snap) {
        ChunkSnapshot.Section[] src = snap.sections(), out = new ChunkSnapshot.Section[src.length];
        for (int i = 0; i < src.length; i++) out[i] = new ChunkSnapshot.Section(src[i].nonAirCount(), src[i].blocks(), src[i].biomes());
        return new ChunkSnapshot(snap.chunkX(), snap.chunkZ(), snap.heightmap(), snap.worldSurfaceHeightmap(), out,
                snap.blockEntities(), snap.save(), snap.light());
    }
}
