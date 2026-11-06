// /tps·/mspt — Paper 의 같은 이름 명령에 해당하는 Furnace 관측 명령(바닐라에 없어 FurnaceAdminCommands 처럼 기동 때 따로 등록, 권한 3).
// Furnace 는 region 마다 따로 틱하므로 "서버 TPS"가 하나가 아니다. 정의(docs/feature-parity/tps-mspt-2026-11-03.md):
//   - 전역 TPS: 전역 스레드 박동(scheduledTick — 시간·날씨·명령 큐·보더)의 초당 횟수. 목표는 늘 20(박동은 /tick rate 와 무관하게 50ms 마다,
//     rate 는 박동 하나에 몇 틱을 낼지로 건다). 창은 Paper 와 같은 5초·1분·5분·15분.
//   - 가장 느린 region: 차원마다 최근 5초 틱 속도가 가장 낮은 region 과 그 값. 목표는 /tick rate. 막 생긴(기록 1초 미만) region 은 뺀다.
//   - /mspt: 전역 박동과 차원별 region 틱 전부의 소요 시간 평균/최소/최대, 창은 Paper 와 같은 5초·10초·1분.
// 표시: Paper 처럼 목표를 넘는 값은 목표로 자르고 5% 넘게 빠르면 앞에 * 를 붙인다(sprint). 색은 목표 대비 90%↑ 초록, 80%↑ 노랑, 아래는 빨강.
// 값은 모두 읽기 전용 계측(TickStartRing·TickWindows)에서 오고 region 소유 상태는 만지지 않는다 — 전역 스레드에서 안전하다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.sched.TickWindows;
import org.domi.furnace.server.world.Regionizer;
import org.domi.furnace.server.world.TickState;
import org.domi.furnace.server.world.World;

public final class TpsCommands {

    private TpsCommands() {}

    static final long SEC = 1_000_000_000L;
    /** /tps 창: 5초·1분·5분·15분(Paper 와 같다). */
    public static final long[] TPS_WINDOWS = {5 * SEC, 60 * SEC, 300 * SEC, 900 * SEC};
    /** /mspt 창: 5초·10초·1분(Paper 와 같다). */
    public static final long[] MSPT_WINDOWS = {5 * SEC, 10 * SEC, 60 * SEC};
    /** region 틱 속도 창(5초)과, 잴 수 있으려면 필요한 기록 길이(1초). */
    public static final long REGION_WINDOW = 5 * SEC;
    public static final long REGION_MIN_HISTORY = SEC;
    /** 전역 박동은 기록 1초부터 잰다. */
    public static final long GLOBAL_MIN_HISTORY = SEC;

    /** 차원 하나의 /tps 값: region 수와 가장 느린 region(잴 수 있는 것이 없으면 null). */
    public record DimRate(String dimension, int regions, Regionizer.RegionRate slowest) {}

    /** 차원 하나의 /mspt 값: MSPT_WINDOWS 순서의 창 요약. */
    public record DimTimes(String dimension, List<TickWindows.Window> windows) {}

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("tps").requires(s -> s.hasPermission(3)).executes(c -> send(c.getSource(), tps(c.getSource().getServer()))));
        d.register(literal("mspt").requires(s -> s.hasPermission(3)).executes(c -> send(c.getSource(), mspt(c.getSource().getServer()))));
    }

    private static int send(CommandSource source, List<Text> lines) {
        for (Text l : lines) source.sendSuccess(() -> l, false);
        return lines.size();
    }

    // ------------------------------------------------------------------ 수집

    static List<Text> tps(ServerCore core) {
        long now = System.nanoTime();
        double[] global = new double[TPS_WINDOWS.length];
        for (int i = 0; i < global.length; i++) global[i] = core.globalTickStarts().rate(now, TPS_WINDOWS[i], GLOBAL_MIN_HISTORY);
        List<DimRate> dims = new ArrayList<>();
        for (World w : core.worlds()) {
            Regionizer rz = w.regionizer();
            if (rz == null) continue;
            dims.add(new DimRate(rz.dimension(), rz.regionCount(), rz.slowestRegion(now, REGION_WINDOW, REGION_MIN_HISTORY)));
        }
        return tpsReport(global, core.tickState().rate(), dims);
    }

    static List<Text> mspt(ServerCore core) {
        long now = System.nanoTime();
        List<TickWindows.Window> global = windows(core.globalTickWindows(), now);
        List<DimTimes> dims = new ArrayList<>();
        for (World w : core.worlds()) {
            Regionizer rz = w.regionizer();
            if (rz == null) continue;
            dims.add(new DimTimes(rz.dimension(), windows(rz.tickWindows(), now)));
        }
        return msptReport(global, core.tickState().rate(), dims);
    }

    private static List<TickWindows.Window> windows(TickWindows tw, long now) {
        List<TickWindows.Window> out = new ArrayList<>();
        for (long w : MSPT_WINDOWS) out.add(tw.window(now, w));
        return out;
    }

    // ------------------------------------------------------------------ 서식(순수 — 시험은 합성 값으로)

    static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    /** 목표 대비 비율로 색: 90% 이상 초록, 80% 이상 노랑, 아래는 빨강. */
    static TextColor rateColor(double tps, double target) {
        if (tps >= target * 0.9) return NamedTextColor.GREEN;
        if (tps >= target * 0.8) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    /** TPS 한 값: 잴 수 없으면 회색 "-", 목표보다 5% 넘게 빠르면 "*목표", 아니면 min(값, 목표) 를 소수 둘째 자리까지. */
    public static Text tpsValue(double tps, double target) {
        if (Double.isNaN(tps)) return Text.text("-", NamedTextColor.GRAY);
        String s = (tps > target * 1.05 ? "*" : "") + f2(Math.min(tps, target));
        return Text.text(s, rateColor(tps, target));
    }

    /** 틱 예산 대비 색: 80% 미만 초록, 100% 미만 노랑, 넘으면 빨강. */
    static TextColor msColor(double nanos, double budgetNanos) {
        if (nanos < budgetNanos * 0.8) return NamedTextColor.GREEN;
        if (nanos < budgetNanos) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    /** 창 하나의 "평균/최소/최대"(ms). 틱이 없으면 회색 "-". */
    public static Text msptValue(TickWindows.Window w, double budgetNanos) {
        if (w.ticks() == 0) return Text.text("-", NamedTextColor.GRAY);
        return Text.empty()
                .append(Text.text(f2(w.avgNanos() / 1.0E6), msColor(w.avgNanos(), budgetNanos)))
                .append(Text.text("/", NamedTextColor.GRAY))
                .append(Text.text(f2(w.minNanos() / 1.0E6), msColor(w.minNanos(), budgetNanos)))
                .append(Text.text("/", NamedTextColor.GRAY))
                .append(Text.text(f2(w.maxNanos() / 1.0E6), msColor(w.maxNanos(), budgetNanos)));
    }

    private static Text joined(List<Text> parts) {
        Text t = Text.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) t = t.append(Text.text(", "));
            t = t.append(parts.get(i));
        }
        return t;
    }

    public static List<Text> tpsReport(double[] global, float rate, List<DimRate> dims) {
        List<Text> out = new ArrayList<>();
        List<Text> g = new ArrayList<>();
        for (double v : global) g.add(tpsValue(v, TickState.DEFAULT_RATE));
        out.add(Text.text("전역 TPS (최근 5초, 1분, 5분, 15분): ", NamedTextColor.GOLD).append(joined(g))
                .append(Text.text(" (목표 " + f1(TickState.DEFAULT_RATE) + ")", NamedTextColor.GRAY)));
        for (DimRate d : dims) {
            Text line = Text.text(d.dimension() + " 가장 느린 region (최근 5초): ", NamedTextColor.GOLD);
            Regionizer.RegionRate s = d.slowest();
            if (s == null) {
                out.add(line.append(Text.text("잴 수 있는 region 없음 (region " + d.regions() + "개)", NamedTextColor.GRAY)));
                continue;
            }
            Regionizer.RegionInfo r = s.region();
            out.add(line.append(tpsValue(s.tps(), rate))
                    .append(Text.text(" — #" + r.id() + ", 청크 " + r.chunks() + "개, 범위 청크 (" + r.minChunkX() + "," + r.minChunkZ() + ")~("
                            + r.maxChunkX() + "," + r.maxChunkZ() + "), region " + d.regions() + "개 (목표 " + f1(rate) + ")", NamedTextColor.GRAY)));
        }
        return out;
    }

    public static List<Text> msptReport(List<TickWindows.Window> global, float rate, List<DimTimes> dims) {
        double budget = 1.0E9 / rate;
        List<Text> out = new ArrayList<>();
        out.add(Text.text("틱 소요 시간 ms, 평균/최소/최대 (최근 5초, 10초, 1분 — 틱 예산 " + f2(budget / 1.0E6) + " ms):", NamedTextColor.GOLD));
        // 전역 박동의 예산은 늘 50ms(박동 주기)
        double globalBudget = 1.0E9 / TickState.DEFAULT_RATE;
        List<Text> g = new ArrayList<>();
        for (TickWindows.Window w : global) g.add(msptValue(w, globalBudget));
        out.add(Text.text("전역: ", NamedTextColor.GOLD).append(joined(g)));
        for (DimTimes d : dims) {
            List<Text> parts = new ArrayList<>();
            for (TickWindows.Window w : d.windows()) parts.add(msptValue(w, budget));
            out.add(Text.text(d.dimension() + " region: ", NamedTextColor.GOLD).append(joined(parts)));
        }
        return out;
    }
}
