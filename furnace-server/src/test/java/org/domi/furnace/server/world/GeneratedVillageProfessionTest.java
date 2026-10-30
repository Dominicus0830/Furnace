// 새로 생성된 마을(seed 0, plains 272,944): 직업 블록·침대·종이 POI 로 올라오고, 구조물 주민이 직업 블록으로 걸어가 직업을 얻은 뒤
// 일과(2000틱~)에 WORK 활동으로 직업 블록 근처에 머문다. 엔티티는 /forceload 청크에서만 틱한다(바닐라와 같은 조건).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedVillageProfessionTest {
    @TempDir Path tmp;

    private static <T> T at(ServerCore core, java.util.function.Function<Region, T> f) throws Exception {
        var fut = new CompletableFuture<T>();
        core.world().runAt(272, 944, r -> {
            try { fut.complete(f.apply(r)); } catch (Throwable t) { fut.completeExceptionally(t); }
        });
        return fut.get(300, TimeUnit.SECONDS);
    }

    @Test
    void structureVillagersTakeJobsAndWork() throws Exception {
        var sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ServerCore core = TestServers.core(tmp, sched, reg -> {
            var src = new AnvilWorldSource(tmp, new RegionFileStore(tmp), new AnvilChunkCodec(reg, 0),
                    NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/1.21.1.txt")));
            src.entityStore(new RegionFileStore(tmp.resolve("entities")));
            return src;
        });
        try {
            for (int x = 14; x <= 20; x++) for (int z = 56; z <= 62; z++) core.world().forcedChunks().set(x, z, true);
            core.world().addTicket(17, 59, 4, 10_000_000L);
            for (int cx = 14; cx <= 20; cx++) for (int cz = 56; cz <= 62; cz++) {
                var cp = new ChunkPos(cx, cz);
                var loaded = new CompletableFuture<CompletableFuture<?>>();
                core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
                loaded.get(60, TimeUnit.SECONDS).get(300, TimeUnit.SECONDS);
            }
            WorldClock clock = core.world().clock();
            clock.setDayTime(1500);
            // (A) 월드젠 블록이 POI 로 복원됐다
            int[] poi = at(core, r -> {
                var pm = r.level().poiManager();
                Pos c = new Pos(272, 70, 944);
                return new int[] {
                        pm.getInSquare(PoiTypes.ACQUIRABLE_JOB_SITE, c, 60, PoiManager.Occupancy.ANY).size(),
                        pm.getInSquare(t -> t == PoiTypes.HOME, c, 60, PoiManager.Occupancy.ANY).size(),
                        pm.getInSquare(t -> t == PoiTypes.MEETING, c, 60, PoiManager.Occupancy.ANY).size()};
            });
            assertTrue(poi[0] >= 3 && poi[1] >= 3 && poi[2] >= 1, "직업 " + poi[0] + " 집 " + poi[1] + " 종 " + poi[2]);
            // (B) 600틱(낮 1500→2100): 직업을 얻고 일한다
            List<String> state = at(core, r -> {
                for (int i = 0; i < 600; i++) {
                    clock.advanceGameTime();
                    clock.setDayTime(clock.dayTime() + 1);
                    r.tick();
                }
                List<String> out = new ArrayList<>();
                for (var e : r.level().entities().tickSnapshot()) {
                    if (!(e instanceof Villager v)) continue;
                    var b = v.villagerBrain();
                    var job = b.getMemory(MemoryModuleType.JOB_SITE);
                    String near = job.map(g -> {
                        double dx = g.pos().x() + 0.5 - v.x(), dz = g.pos().z() + 0.5 - v.z();
                        return Math.sqrt(dx * dx + dz * dz) < 10 ? "near" : "far";
                    }).orElse("-");
                    out.add(v.profession() + "/" + (b.isActive(Activity.WORK) ? "work" : "other") + "/" + near);
                }
                return out;
            });
            long employed = state.stream().filter(s -> !s.startsWith("none") && !s.startsWith("nitwit")).count();
            assertTrue(employed >= 2, "직업을 얻은 주민 " + state);
            assertTrue(state.stream().anyMatch(s -> s.endsWith("/work/near")), "일과에 직업 블록 근처에서 WORK " + state);
        } finally {
            sched.close();
        }
    }
}
