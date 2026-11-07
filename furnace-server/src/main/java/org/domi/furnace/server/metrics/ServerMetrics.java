// 서버 값을 메트릭으로 모은다 — /tps·/mspt(TpsCommands)와 같은 읽기 전용 계측만 읽는다(region 소유 상태는 만지지 않는다).
// 이름 규칙: furnace_ 접두사, 시간은 초 단위(Prometheus 관례).
package org.domi.furnace.server.metrics;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.builtin.TpsCommands;
import org.domi.furnace.server.sched.TickWindows;
import org.domi.furnace.server.world.Regionizer;
import org.domi.furnace.server.world.World;

public final class ServerMetrics implements MetricsSource {
    private static final String[] TPS_WINDOW_NAMES = {"5s", "1m", "5m", "15m"};
    private static final String[] MSPT_WINDOW_NAMES = {"5s", "10s", "1m"};

    private final ServerCore core;

    public ServerMetrics(ServerCore core) { this.core = core; }

    @Override
    public List<Sample> collect() {
        List<Sample> out = new ArrayList<>();
        long now = System.nanoTime();
        out.add(Sample.gauge("furnace_players_online", "접속 중인 플레이어 수", core.players().size()));
        out.add(Sample.gauge("furnace_tick_rate_target", "목표 틱 속도(/tick rate)", core.tickState().rate()));
        for (int i = 0; i < TpsCommands.TPS_WINDOWS.length; i++) {
            out.add(Sample.gauge("furnace_tps_global", "전역 박동의 초당 횟수(잴 수 없으면 생략)",
                    core.globalTickStarts().rate(now, TpsCommands.TPS_WINDOWS[i], TpsCommands.GLOBAL_MIN_HISTORY), "window", TPS_WINDOW_NAMES[i]));
        }
        for (int i = 0; i < TpsCommands.MSPT_WINDOWS.length; i++) {
            TickWindows.Window w = core.globalTickWindows().window(now, TpsCommands.MSPT_WINDOWS[i]);
            if (w.ticks() > 0) out.add(Sample.gauge("furnace_mspt_global_seconds", "전역 박동 평균 소요 시간(초)", w.avgNanos() / 1.0E9, "window", MSPT_WINDOW_NAMES[i]));
        }
        for (World w : core.worlds()) {
            Regionizer rz = w.regionizer();
            if (rz == null) continue;
            String dim = rz.dimension();
            out.add(Sample.gauge("furnace_loaded_chunks", "로드된 청크 수", w.loadedChunkCount(), "dimension", dim));
            out.add(Sample.gauge("furnace_regions", "region 수", rz.regionCount(), "dimension", dim));
            Regionizer.RegionRate slow = rz.slowestRegion(now, TpsCommands.REGION_WINDOW, TpsCommands.REGION_MIN_HISTORY);
            if (slow != null) out.add(Sample.gauge("furnace_tps_slowest_region", "차원에서 가장 느린 region 의 최근 5초 틱 속도", slow.tps(), "dimension", dim));
            for (int i = 0; i < TpsCommands.MSPT_WINDOWS.length; i++) {
                TickWindows.Window tw = rz.tickWindows().window(now, TpsCommands.MSPT_WINDOWS[i]);
                if (tw.ticks() > 0) out.add(Sample.gauge("furnace_mspt_region_seconds", "차원 region 틱 평균 소요 시간(초)", tw.avgNanos() / 1.0E9,
                        "dimension", dim, "window", MSPT_WINDOW_NAMES[i]));
            }
        }
        var mem = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        out.add(Sample.gauge("furnace_jvm_heap_used_bytes", "JVM 힙 사용량", mem.getUsed()));
        out.add(Sample.gauge("furnace_jvm_heap_max_bytes", "JVM 힙 최대", mem.getMax()));
        out.add(Sample.gauge("furnace_jvm_threads", "JVM 스레드 수", ManagementFactory.getThreadMXBean().getThreadCount()));
        return out;
    }
}
