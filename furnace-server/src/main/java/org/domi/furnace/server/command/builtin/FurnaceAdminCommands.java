// Furnace 전용 관리 명령 /furnace threads | regions [개수] | profile | config [all|키] | compat gaps — 멀티스레드 서버의 스레드·region 현황과 틱 시간 요약.
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
import org.domi.furnace.server.i18n.Lang;
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
                .then(literal("config").executes(c -> send(c.getSource(), org.domi.furnace.server.config.ConfigDump.render(null)))
                        .then(argument("filter", Arguments.greedyString())
                                .suggests((c, b) -> org.domi.furnace.server.command.SharedSuggestions.suggest(configFilters(), b))
                                .executes(c -> send(c.getSource(), org.domi.furnace.server.config.ConfigDump.render(c.getArgument("filter", String.class))))))
                .then(literal("compat").then(literal("gaps").executes(c -> compatGaps(c.getSource()))))
                .then(literal("profile").executes(c -> send(c.getSource(),
                        profileReport(global(c.getSource().getServer()), dims(c.getSource().getServer()), c.getSource().getServer().tickState().rate())))));
    }

    /**
     * /furnace compat gaps — 호환 어댑터가 모은 "서버에 없는 것" 표를 파일로 내보낸다(T-233, 결정 D2). 서버는 이것을 밖으로 보내지 않는다 —
     * 운영자가 원할 때 파일을 직접 보내 준다. 파일에는 플러그인 이름·NMS 심볼 이름·횟수만 있고 플레이어·월드·경로 정보는 없다.
     */
    private static int compatGaps(CommandSource source) {
        List<org.domi.furnace.api.plugin.CompatGap> rows = source.getServer().plugins().pluginsImpl().compatGaps();
        if (rows.isEmpty()) return send(source, List.of(Lang.t("cmd.compat.gaps-none")));
        java.time.Instant now = java.time.Instant.now();
        java.nio.file.Path file = java.nio.file.Path.of("compat-gaps-" + GAPS_FILE_TIME.format(now) + ".tsv");
        try {
            java.nio.file.Files.writeString(file, gapsTsv(rows, org.domi.furnace.api.GameVersion.MINECRAFT, now), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            source.sendFailure(Text.text(Lang.t("cmd.compat.gaps-write-failed", file.toString(), e.toString())));
            return 0;
        }
        return send(source, gapsReport(rows, file.toString()));
    }

    private static final java.time.format.DateTimeFormatter GAPS_FILE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC);

    /** 내보내는 표(탭 구분, UTF-8). 첫 줄은 형식·판·시각, 둘째 줄은 열 이름. 횟수 많은 순으로 이미 정렬돼 들어온다. */
    public static String gapsTsv(List<org.domi.furnace.api.plugin.CompatGap> rows, String mc, java.time.Instant at) {
        StringBuilder sb = new StringBuilder();
        sb.append("#furnace-compat-gaps\t1\t").append(mc).append('\t').append(at.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)).append('\n');
        sb.append("plugin\tkind\ttarget\treferenced\tcalls\n");
        for (var r : rows) {
            sb.append(tsv(r.plugin())).append('\t').append(r.kind()).append('\t').append(tsv(r.target())).append('\t')
                    .append(r.referenced() ? "yes" : "no").append('\t').append(r.calls()).append('\n');
        }
        return sb.toString();
    }

    private static String tsv(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    public static List<String> gapsReport(List<org.domi.furnace.api.plugin.CompatGap> rows, String file) {
        List<String> out = new ArrayList<>();
        long called = rows.stream().filter(r -> r.calls() > 0).count();
        out.add(Lang.t("cmd.compat.gaps-header", rows.size(), called, file));
        int n = 0;
        for (var r : rows) {
            if (n++ >= GAPS_PREVIEW) break;
            out.add("  " + Lang.t("cmd.compat.gaps-line", r.plugin(), r.kind(), r.target(), r.calls()));
        }
        out.add(Lang.t("cmd.compat.gaps-footer"));
        return out;
    }

    private static final int GAPS_PREVIEW = 10;

    /** /furnace config 의 자동완성 후보: all 과 모든 키. */
    private static List<String> configFilters() {
        List<String> out = new ArrayList<>();
        out.add("all");
        for (var o : org.domi.furnace.server.config.OptionKeys.all()) out.add(o.key());
        return out;
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
        if (t.samples() == 0) return Lang.t("cmd.admin.no-tick-samples");
        return Lang.t("cmd.admin.tick-line", ms(t.p50Nanos()), ms(t.p95Nanos()), ms(t.maxRecentNanos()), ms(t.maxEverNanos()), t.samples());
    }

    public static List<String> threadsReport(GlobalView g, List<DimView> dims) {
        List<String> out = new ArrayList<>();
        out.add(Lang.t("cmd.admin.threads-header"));
        out.add(Lang.t("cmd.admin.global-line", g.thread(), g.queued(), tickLine(g.tick())));
        for (DimView d : dims) {
            Regionizer.SchedStats s = d.sched();
            out.add(Lang.t("cmd.admin.dim-line", d.dimension(), d.regionCount(), d.queuedRegions(), s.threads(), s.activeWorkers(), s.peakActiveWorkers()));
            out.add("  " + tickLine(d.tick()));
            out.add("  " + Lang.t("cmd.admin.queue-delay", ms(s.queueDelayP50Nanos()), ms(s.queueDelayP95Nanos()), ms(s.queueDelayP99Nanos()),
                    ms(s.queueDelayMaxNanos()), s.ticks()));
            for (String[] w : d.workers()) {
                String running = null;
                for (Regionizer.RegionInfo r : d.regions()) if (w[0].equals(r.thread())) running = Lang.t("cmd.admin.worker-running", r.id(), ms(r.runningNanos()));
                out.add("  " + Lang.t("cmd.admin.worker", w[0], w[1], running != null ? running : Lang.t("cmd.admin.worker-idle")));
            }
        }
        return out;
    }

    public static List<String> regionsReport(List<DimView> dims, int limit) {
        List<String> out = new ArrayList<>();
        out.add(Lang.t("cmd.admin.regions-header", limit));
        for (DimView d : dims) {
            List<Regionizer.RegionInfo> sorted = new ArrayList<>(d.regions());
            sorted.sort(Comparator.comparingLong(Regionizer.RegionInfo::lastTickNanos).reversed().thenComparingInt(Regionizer.RegionInfo::id));
            out.add(Lang.t("cmd.admin.regions-dim", d.dimension(), sorted.size()));
            int n = 0;
            for (Regionizer.RegionInfo r : sorted) {
                if (n++ >= limit) {
                    out.add("  " + Lang.t("cmd.admin.regions-more", sorted.size() - limit));
                    break;
                }
                out.add("  " + Lang.t("cmd.admin.region-line", r.id(), r.chunks(), r.sections(), r.inbox(), r.minChunkX(), r.minChunkZ(), r.maxChunkX(), r.maxChunkZ(),
                        ms(r.lastTickNanos()), ms(r.maxTickNanos()), r.thread() == null ? Lang.t("cmd.admin.worker-idle") : Lang.t("cmd.admin.worker-name", r.thread())));
            }
        }
        return out;
    }

    public static List<String> profileReport(GlobalView g, List<DimView> dims, float rate) {
        List<String> out = new ArrayList<>();
        out.add(Lang.t("cmd.admin.profile-header", String.format(Locale.ROOT, "%.1f", rate), ms((long) (1.0E9 / rate))));
        out.add(Lang.t("cmd.admin.profile-global", tickLine(g.tick())));
        for (DimView d : dims) {
            out.add(Lang.t("cmd.admin.profile-dim", d.dimension(), tickLine(d.tick()), ms(d.sched().queueDelayP95Nanos())));
            List<Regionizer.RegionInfo> top = new ArrayList<>(d.regions());
            top.sort(Comparator.comparingLong(Regionizer.RegionInfo::maxTickNanos).reversed().thenComparingInt(Regionizer.RegionInfo::id));
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                Regionizer.RegionInfo r = top.get(i);
                if (r.maxTickNanos() == 0) break;
                out.add("  " + Lang.t("cmd.admin.profile-slow", r.id(), ms(r.maxTickNanos()), r.chunks(), r.minChunkX(), r.minChunkZ(), r.maxChunkX(), r.maxChunkZ()));
            }
        }
        out.add(Lang.t("cmd.admin.profile-footer"));
        return out;
    }
}
