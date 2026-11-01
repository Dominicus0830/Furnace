// Furnace 전용 관리 명령 /furnace threads | regions [개수] | profile — 멀티스레드 서버의 스레드·region 현황과 틱 시간 요약.
// 바닐라에 없는 명령이라 BuiltinCommands(바닐라 트리 대조 대상)에 넣지 않고 FurnaceCommands 처럼 기동 때 따로 등록한다. 권한 3(/tick 과 같다).
// 값은 모두 읽기 전용 계측에서 온다: Regionizer 의 스케줄 계측(큐 지연 분포)·틱 시간 고리·region 관측 사본(자기 잠금 안에서 값만 복사),
// 전역 스레드의 틱 시간 고리와 대기 작업 수. region 이 소유한 상태(청크·엔티티·플레이어)는 만지지 않는다 — 전역 스레드에서 안전하다.
// 단계별 분해(개별 틱 구간)는 -Dfurnace.tickstats=true, 표본 추적은 /jfr start 가 맡는다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.sched.TickTimeRing;
import org.domi.furnace.server.world.Regionizer;
import org.domi.furnace.server.world.World;

public final class FurnaceAdminCommands {

    private FurnaceAdminCommands() {}

    public static final int DEFAULT_REGION_LIMIT = 10;
    public static final int MAX_REGION_LIMIT = 100;

    /** 전역 스레드 한 줄 분량의 관측 값. */
    public record GlobalView(String thread, int queued, TickTimeRing.Stats tick) {}

    /** 차원 하나(그 Regionizer 와 워커 풀)의 관측 값. */
    public record DimView(String dimension, int regionCount, int queuedRegions, Regionizer.SchedStats sched,
                          TickTimeRing.Stats tick, List<Regionizer.RegionInfo> regions, List<String[]> workers) {}

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("furnace").requires(s -> s.hasPermission(3))
                .then(literal("threads").executes(c -> send(c.getSource(), threadsReport(global(c.getSource().getServer()), dims(c.getSource().getServer())))))
                .then(literal("regions").executes(c -> regions(c.getSource(), DEFAULT_REGION_LIMIT))
                        .then(argument("limit", Arguments.integer(1, MAX_REGION_LIMIT))
                                .executes(c -> regions(c.getSource(), c.getArgument("limit", Integer.class)))))
                .then(literal("profile").executes(c -> send(c.getSource(),
                        profileReport(global(c.getSource().getServer()), dims(c.getSource().getServer()), c.getSource().getServer().tickState().rate())))));
    }

    private static int regions(CommandSource source, int limit) {
        return send(source, regionsReport(dims(source.getServer()), limit));
    }

    private static int send(CommandSource source, List<String> lines) {
        for (String l : lines) source.sendSuccess(() -> Text.text(l), false);
        return lines.size();
    }

    // ------------------------------------------------------------------ 수집

    static GlobalView global(ServerCore core) {
        return new GlobalView(core.globalThreadName(), core.globalQueueLength(), core.globalTickTimes());
    }

    static List<DimView> dims(ServerCore core) {
        List<DimView> out = new ArrayList<>();
        for (World w : core.worlds()) {
            Regionizer rz = w.regionizer();
            if (rz == null) continue;
            Regionizer.SchedStats st = rz.schedStats();
            out.add(new DimView(rz.dimension(), st.regions().size(), rz.queuedRegions(), st, rz.tickTimes(), rz.regionInfos(), rz.workerStates()));
        }
        return out;
    }

    // ------------------------------------------------------------------ 서식(순수 — 시험은 합성 값으로)

    public static String ms(long nanos) { return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0); }

    public static String tickLine(TickTimeRing.Stats t) {
        if (t.samples() == 0) return "틱 표본 없음";
        return "틱 p50 " + ms(t.p50Nanos()) + " / p95 " + ms(t.p95Nanos()) + " / 최근 최대 " + ms(t.maxRecentNanos()) + " / 가동 후 최대 "
                + ms(t.maxEverNanos()) + " ms (표본 " + t.samples() + ")";
    }

    public static List<String> threadsReport(GlobalView g, List<DimView> dims) {
        List<String> out = new ArrayList<>();
        out.add("== 스레드 현황 ==");
        out.add("전역 " + g.thread() + ": 대기 작업 " + g.queued() + ", " + tickLine(g.tick()));
        for (DimView d : dims) {
            Regionizer.SchedStats s = d.sched();
            out.add(d.dimension() + ": region " + d.regionCount() + "개(대기열 " + d.queuedRegions() + "), 워커 " + s.threads() + "개 중 "
                    + s.activeWorkers() + "개 틱 중(최대 동시 " + s.peakActiveWorkers() + ")");
            out.add("  " + tickLine(d.tick()));
            out.add("  큐 지연 p50 " + ms(s.queueDelayP50Nanos()) + " / p95 " + ms(s.queueDelayP95Nanos()) + " / p99 " + ms(s.queueDelayP99Nanos())
                    + " / 최대 " + ms(s.queueDelayMaxNanos()) + " ms (틱 " + s.ticks() + "회)");
            for (String[] w : d.workers()) {
                String running = null;
                for (Regionizer.RegionInfo r : d.regions()) if (w[0].equals(r.thread())) running = "region #" + r.id() + " (" + ms(r.runningNanos()) + " ms째)";
                out.add("  워커 " + w[0] + " [" + w[1] + "] " + (running != null ? running : "대기"));
            }
        }
        return out;
    }

    public static List<String> regionsReport(List<DimView> dims, int limit) {
        List<String> out = new ArrayList<>();
        out.add("== region 목록 (직전 틱이 긴 순, 차원마다 최대 " + limit + "개) ==");
        for (DimView d : dims) {
            List<Regionizer.RegionInfo> sorted = new ArrayList<>(d.regions());
            sorted.sort(Comparator.comparingLong(Regionizer.RegionInfo::lastTickNanos).reversed().thenComparingInt(Regionizer.RegionInfo::id));
            out.add(d.dimension() + ": region " + sorted.size() + "개");
            int n = 0;
            for (Regionizer.RegionInfo r : sorted) {
                if (n++ >= limit) {
                    out.add("  … " + (sorted.size() - limit) + "개 더");
                    break;
                }
                out.add("  #" + r.id() + " 청크 " + r.chunks() + " / 구역 " + r.sections() + " / 인박스 " + r.inbox() + " / 범위 청크 ("
                        + r.minChunkX() + "," + r.minChunkZ() + ")~(" + r.maxChunkX() + "," + r.maxChunkZ() + ") / 직전 " + ms(r.lastTickNanos())
                        + " 최대 " + ms(r.maxTickNanos()) + " ms / " + (r.thread() == null ? "대기" : "워커 " + r.thread()));
            }
        }
        return out;
    }

    public static List<String> profileReport(GlobalView g, List<DimView> dims, float rate) {
        List<String> out = new ArrayList<>();
        out.add("== 틱 프로파일 요약 (목표 " + String.format(Locale.ROOT, "%.1f", rate) + " TPS, 틱 예산 " + ms((long) (1.0E9 / rate)) + " ms) ==");
        out.add("전역: " + tickLine(g.tick()));
        for (DimView d : dims) {
            out.add(d.dimension() + ": " + tickLine(d.tick()) + ", 큐 지연 p95 " + ms(d.sched().queueDelayP95Nanos()) + " ms");
            List<Regionizer.RegionInfo> top = new ArrayList<>(d.regions());
            top.sort(Comparator.comparingLong(Regionizer.RegionInfo::maxTickNanos).reversed().thenComparingInt(Regionizer.RegionInfo::id));
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                Regionizer.RegionInfo r = top.get(i);
                if (r.maxTickNanos() == 0) break;
                out.add("  느린 region #" + r.id() + ": 최대 " + ms(r.maxTickNanos()) + " ms, 청크 " + r.chunks() + ", 범위 ("
                        + r.minChunkX() + "," + r.minChunkZ() + ")~(" + r.maxChunkX() + "," + r.maxChunkZ() + ")");
            }
        }
        out.add("단계별 분해는 -Dfurnace.tickstats=true, 표본 추적은 /jfr start.");
        return out;
    }
}
