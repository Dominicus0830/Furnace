// T-209: 주인이 아닌 스레드의 첫 섹션 data 읽기(FAWE PaperweightGetBlocks.update 꼴 — 섹션 배열 사본 → getStates → data 리플렉션 장벽) 시간. 수동 실행:
//   ./gradlew -PskipOracle=true :furnace-compat-paper:compatSceneBench --tests '*FirstDataReadBench*' [-Dfurnace.bench.t209.rounds=400]
// 결과: furnace-compat-paper/build/bench/FirstDataRead-<indirect|direct|burst>.json (first_p50_ms 등) + 표준 출력 FIRSTDATAREAD 줄.
//   indirect·direct: 틱 하나 뒤 섹션 하나를 처음 읽는다(캐시가 식은 최악 꼴 — 서버 섹션이 간접 저장 / 직접 저장으로 커진 뒤).
//   burst: 틱 하나 뒤 막 바뀐 섹션 수십 개를 잇달아 처음 읽는다(FAWE GET 이 편집 범위의 청크를 훑는 꼴) — 섹션 하나당 시간.
// 결과 해석: context-notes/2026-11-17-t209-first-section-read.md(FAWE 첫 읽기 경쟁 재현 도구 tools/compat/fawe-paper/charblocks-race 에 p50 을 넣어 볼 수도 있다).
// 절대 시간으로 CI 를 막지 않는다 — 같은 기계에서 옛·새 커밋을 번갈아 재서 비교한다(이 파일은 공개 API 만 써서 옛 커밋에도 그대로 넣어 돌릴 수 있다).
package org.domi.furnace.compat.nms;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.bench.BenchReport;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;

class FirstDataReadBench {

    @Test
    void bench() throws Exception {
        int rounds = Integer.getInteger("furnace.bench.t209.rounds", 400);
        int skip = rounds / 4;   // 앞 구간(JIT 예열)은 통계에서 뺀다
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            Bukkit.setServer(CraftServer.INSTANCE);
            TestPlayer tp = server.join("Bench");
            server.tick(3);
            var loc = tp.player().location();
            int cx = Math.floorDiv((int) Math.floor(loc.x()), 16), cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) server.loadChunk(cx + dx, cz + dz);
            server.tick(2);
            ServerLevel level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
            Field data = PalettedContainer.class.getDeclaredField("data");
            data.setAccessible(true);
            int qx = cx + 1, qz = cz + 1, si = 16;
            LevelChunk c = level.getChunk(qx, qz);   // 이 스레드 = 주인이 아닌 스레드
            int y0 = level.getMinBuildHeight() + si * 16;
            BlockState[] simple = {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
            for (String variant : new String[] {"indirect", "direct"}) {
                if (variant.equals("direct")) {   // 서로 다른 상태 수백 개를 지나가게 해 서버 팔레트를 직접 저장으로 키운다(살아 있는 팔레트는 줄지 않는다)
                    int wire = net.minecraft.world.level.block.Block.getId(Blocks.REDSTONE_WIRE.defaultBlockState());
                    onRegion(server, qx, qz, () -> {
                        List<BlockState> wires = new ArrayList<>();
                        for (int id = wire - 1300; id <= wire + 1300; id++) {
                            BlockState s = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(id);
                            if (s != null && s.getBlock() == Blocks.REDSTONE_WIRE) wires.add(s);
                        }
                        for (int cell = 0; cell < 4096; cell++) level.setBlock(at(qx, y0, qz, cell), wires.get(cell % wires.size()), 2 | 16);
                    });
                }
                long[] first = new long[rounds], cached = new long[rounds], detached = new long[rounds];
                for (int r = 0; r < rounds; r++) {
                    int rr = r;
                    onRegion(server, qx, qz, () -> {   // 칸 600개를 바꿔 내용 판을 올린다(FAWE 가 막 바뀐 섹션을 처음 읽는 장면)
                        var rnd = new java.util.Random(rr);
                        for (int k = 0; k < 600; k++) level.setBlock(at(qx, y0, qz, rnd.nextInt(4096)), simple[rnd.nextInt(simple.length)], 2 | 16);
                    });
                    server.tick(1);   // 공개
                    long t0 = System.nanoTime();
                    LevelChunkSection[] secs = c.getSections().clone();
                    PalettedContainer<BlockState> st = secs[si].getStates();
                    NmsReflect.beforeFieldGet(data, st);   // 변환기가 플러그인의 Field.get 앞에 넣는 장벽
                    Object d = data.get(st);
                    long t1 = System.nanoTime();
                    NmsReflect.beforeFieldGet(data, st);
                    Object d2 = data.get(st);
                    long t2 = System.nanoTime();
                    if (d == null || d2 == null) throw new AssertionError("data null");
                    PalettedContainer<BlockState> det = st.copy();   // Paper 꼴: 떨어진 컨테이너의 필드 그대로
                    long t3 = System.nanoTime();
                    NmsReflect.beforeFieldGet(data, det);
                    if (data.get(det) == null) throw new AssertionError("data null");
                    long t4 = System.nanoTime();
                    first[r] = t1 - t0;
                    cached[r] = t2 - t1;
                    detached[r] = t4 - t3;
                }
                BenchReport rep = BenchReport.of("FirstDataRead").variant(variant).put("rounds", rounds).put("skip", skip);
                rep.timesNs("first_", first, skip, rounds).timesNs("cached_", cached, skip, rounds).timesNs("detached_", detached, skip, rounds);
                rep.write();
                System.out.printf("FIRSTDATAREAD %s first p50=%.1fus p90=%.1fus | cached p50=%.1fus | detached(Paper 꼴) p50=%.1fus%n", variant,
                        pct(first, skip, 50), pct(first, skip, 90), pct(cached, skip, 50), pct(detached, skip, 50));
            }
            burst(server, level, data, cx, cz, simple, rounds / 4, skip / 4);
        }
    }

    /** 3×3 청크 × 섹션 4개 = 36개를 바꾸고 틱 하나 뒤 잇달아 처음 읽는다. 표본 = 섹션 하나의 첫 읽기. */
    private static void burst(FurnaceTestServer server, ServerLevel level, Field data, int cx, int cz, BlockState[] simple, int rounds, int skip) throws Exception {
        int[] secIdx = {4, 5, 6, 7};
        int per = 9 * secIdx.length;
        long[] first = new long[rounds * per];
        int k = 0;
        for (int r = 0; r < rounds; r++) {
            int rr = r;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int qx = cx + dx, qz = cz + dz;
                onRegion(server, qx, qz, () -> {
                    var rnd = new java.util.Random(rr * 31L + qx * 7L + qz);
                    for (int si : secIdx) {
                        int y0 = level.getMinBuildHeight() + si * 16;
                        for (int w = 0; w < 64; w++) level.setBlock(at(qx, y0, qz, rnd.nextInt(4096)), simple[rnd.nextInt(simple.length)], 2 | 16);
                    }
                });
            }
            server.tick(1);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                LevelChunk c = level.getChunk(cx + dx, cz + dz);
                for (int si : secIdx) {
                    long t0 = System.nanoTime();
                    LevelChunkSection[] secs = c.getSections().clone();
                    PalettedContainer<BlockState> st = secs[si].getStates();
                    NmsReflect.beforeFieldGet(data, st);
                    if (data.get(st) == null) throw new AssertionError("data null");
                    first[k++] = System.nanoTime() - t0;
                }
            }
        }
        BenchReport rep = BenchReport.of("FirstDataRead").variant("burst").put("rounds", rounds).put("sectionsPerRound", per).put("skip", skip * per);
        rep.timesNs("first_", first, skip * per, k).write();
        System.out.printf("FIRSTDATAREAD burst(%d sections/tick) first p50=%.1fus p90=%.1fus%n", per, pct(first, skip * per, 50), pct(first, skip * per, 90));
    }

    private static BlockPos at(int qx, int y0, int qz, int cell) {
        return new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15));
    }

    private static void onRegion(FurnaceTestServer server, int qx, int qz, Runnable r) {
        server.onRegion(new org.domi.furnace.api.world.BlockPos(qx * 16, 64, qz * 16), () -> {
            r.run();
            return null;
        });
    }

    private static double pct(long[] a, int from, int p) {
        long[] s = Arrays.copyOfRange(a, from, a.length);
        Arrays.sort(s);
        return s[Math.min(s.length - 1, s.length * p / 100)] / 1e3;
    }
}
