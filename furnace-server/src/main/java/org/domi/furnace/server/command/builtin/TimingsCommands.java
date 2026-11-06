// /timings on | off | reset | report [플러그인] | export — 플러그인별 시간 프로파일러(Bukkit timings 에 해당)를 켜고 끄고 본다.
// 바닐라에 없는 명령이라 TpsCommands 처럼 기동 때 따로 등록한다(권한 3).
// 값은 모두 자기 시간(그 안에서 잰 다른 플러그인 구간을 뺀 값)이고, "틱당"은 측정 시간을 50ms 로 나눈 횟수로 나눈 값이다 —
// region 이 여럿이면 여러 스레드의 시간을 더한 값이라 50ms 를 넘을 수 있다. 스레드 종류별(region·전역·그 밖) 몫을 같이 보여 준다.
// 기동 때부터 재려면 -Dfurnace.profiler=true.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.PluginProfiler.PluginStats;
import org.domi.furnace.api.profiler.PluginProfiler.Report;
import org.domi.furnace.api.profiler.PluginProfiler.SectionStats;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;

public final class TimingsCommands {

    private TimingsCommands() {}

    /** 요약에 보일 플러그인 수와 플러그인마다 보일 구간 수. */
    public static final int SUMMARY_PLUGINS = 10;
    public static final int SUMMARY_SECTIONS = 3;
    /** 플러그인 하나를 볼 때 보일 구간 수. */
    public static final int DETAIL_SECTIONS = 15;
    /** export 가 쓰는 폴더(서버 작업 폴더 기준). */
    public static final Path EXPORT_DIR = Path.of("timings");

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("timings").requires(s -> s.hasPermission(3))
                .executes(c -> send(c.getSource(), usage(profiler(c.getSource()))))
                .then(literal("on").executes(c -> {
                    PluginProfiler p = profiler(c.getSource());
                    boolean was = p.isRunning();
                    p.start();
                    return send(c.getSource(), List.of(Text.text(was ? "플러그인 시간 측정이 이미 켜져 있다" : "플러그인 시간 측정을 켰다", NamedTextColor.GOLD)));
                }))
                .then(literal("off").executes(c -> {
                    PluginProfiler p = profiler(c.getSource());
                    p.stop();
                    return send(c.getSource(), List.of(Text.text("플러그인 시간 측정을 껐다 — 모은 값은 /timings report 로 볼 수 있다", NamedTextColor.GOLD)));
                }))
                .then(literal("reset").executes(c -> {
                    profiler(c.getSource()).reset();
                    return send(c.getSource(), List.of(Text.text("모은 플러그인 시간을 지웠다", NamedTextColor.GOLD)));
                }))
                .then(literal("report").executes(c -> send(c.getSource(), summary(profiler(c.getSource()).report())))
                        .then(argument("plugin", Arguments.greedyString())
                                .suggests((c, b) -> SharedSuggestions.suggest(pluginNames(c.getSource()), b))
                                .executes(c -> send(c.getSource(), detail(profiler(c.getSource()).report(), c.getArgument("plugin", String.class))))))
                .then(literal("export").executes(c -> export(c.getSource()))));
    }

    private static PluginProfiler profiler(CommandSource s) { return s.getServer().plugins().profiler(); }

    private static List<String> pluginNames(CommandSource s) {
        List<String> out = new ArrayList<>();
        for (var p : s.getServer().plugins().plugins().plugins()) out.add(p.name());
        return out;
    }

    private static int send(CommandSource source, List<Text> lines) {
        for (Text l : lines) source.sendSuccess(() -> l, false);
        return lines.size();
    }

    private static int export(CommandSource source) {
        Report r = profiler(source).report();
        Path file = EXPORT_DIR.resolve("plugins-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)) + ".txt");
        try {
            Files.createDirectories(EXPORT_DIR);
            Files.writeString(file, String.join("\n", fullText(r)) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            source.sendFailure(Text.text("보고서를 쓰지 못했다: " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Text.text("보고서를 썼다: " + file.toAbsolutePath(), NamedTextColor.GOLD), false);
        return 1;
    }

    // ------------------------------------------------------------------ 서식(순수 — 시험은 합성 값으로)

    static String ms(long ns) { return String.format(Locale.ROOT, "%.2f", ns / 1e6); }

    static String us(double ns) { return String.format(Locale.ROOT, "%.1f", ns / 1e3); }

    /** 측정 시간 동안 틱(50ms) 하나에 평균 몇 ms 를 썼나. 측정 시간이 틱 하나보다 짧으면 쓴 시간 그대로. */
    static double perTickMs(long selfNs, Report r) {
        double ticks = Math.max(1.0, (double) r.measuredNanos() / r.tickNanos());
        return selfNs / ticks / 1e6;
    }

    static String kindName(PluginProfiler.Kind k) {
        return switch (k) {
            case LISTENER -> "리스너";
            case TASK -> "작업";
            case ASYNC -> "비동기";
            case COMMAND -> "명령";
            case LIFECYCLE -> "수명";
            case CUSTOM -> "사용자";
        };
    }

    static List<Text> usage(PluginProfiler p) {
        return List.of(Text.text("플러그인 시간 측정: " + (p.isRunning() ? "켜짐" : "꺼짐"), NamedTextColor.GOLD),
                Text.text("/timings on | off | reset | report [플러그인] | export", NamedTextColor.GRAY));
    }

    static Text header(Report r) {
        return Text.text("플러그인 시간 (측정 " + String.format(Locale.ROOT, "%.1f", r.measuredNanos() / 1e9) + "초, "
                + (r.running() ? "측정 중" : "멈춤") + ") — 자기 시간, 틱당 = 50ms 마다 모든 스레드에서 쓴 시간", NamedTextColor.GOLD);
    }

    static String pluginLine(int rank, PluginStats p, Report r) {
        return rank + ". " + p.plugin() + ": " + ms(p.selfNanos()) + " ms (틱당 " + String.format(Locale.ROOT, "%.3f", perTickMs(p.selfNanos(), r))
                + " ms), 호출 " + p.calls() + " — region " + ms(p.regionNanos()) + " / 전역 " + ms(p.globalNanos()) + " / 그 밖 " + ms(p.otherNanos());
    }

    static String sectionLine(SectionStats s) {
        String line = kindName(s.kind()) + " " + s.name() + ": 호출 " + s.calls() + ", 자기 " + ms(s.selfNanos()) + " ms";
        if (s.totalNanos() != s.selfNanos()) line += " (안쪽 포함 " + ms(s.totalNanos()) + ")";
        line += ", 평균 " + us((double) s.selfNanos() / s.calls()) + " µs, 최대 " + ms(s.maxNanos()) + " ms";
        if (s.overBudget() > 0) line += ", 50ms 넘음 " + s.overBudget() + "번";
        return line;
    }

    /** 자기 시간이 큰 플러그인 SUMMARY_PLUGINS 개, 각각 큰 구간 SUMMARY_SECTIONS 개. */
    public static List<Text> summary(Report r) {
        List<Text> out = new ArrayList<>();
        out.add(header(r));
        if (r.plugins().isEmpty()) {
            out.add(Text.text(r.running() || r.measuredNanos() > 0 ? "아직 잰 플러그인 코드가 없다" : "측정이 꺼져 있다 — /timings on", NamedTextColor.GRAY));
            return out;
        }
        int rank = 0;
        for (PluginStats p : r.plugins()) {
            if (rank == SUMMARY_PLUGINS) {
                out.add(Text.text("… 플러그인 " + (r.plugins().size() - rank) + "개 더 (/timings export)", NamedTextColor.GRAY));
                break;
            }
            out.add(Text.text(pluginLine(++rank, p, r), NamedTextColor.YELLOW));
            for (int i = 0; i < Math.min(SUMMARY_SECTIONS, p.sections().size()); i++) {
                out.add(Text.text("   " + sectionLine(p.sections().get(i)), NamedTextColor.GRAY));
            }
        }
        return out;
    }

    /** 플러그인 하나(이름은 대소문자 무시)의 구간 DETAIL_SECTIONS 개. */
    public static List<Text> detail(Report r, String plugin) {
        List<Text> out = new ArrayList<>();
        out.add(header(r));
        for (int i = 0; i < r.plugins().size(); i++) {
            PluginStats p = r.plugins().get(i);
            if (!p.plugin().equalsIgnoreCase(plugin.trim())) continue;
            out.add(Text.text(pluginLine(i + 1, p, r), NamedTextColor.YELLOW));
            for (int j = 0; j < Math.min(DETAIL_SECTIONS, p.sections().size()); j++) {
                out.add(Text.text("   " + sectionLine(p.sections().get(j)), NamedTextColor.GRAY));
            }
            if (p.sections().size() > DETAIL_SECTIONS) {
                out.add(Text.text("   … 구간 " + (p.sections().size() - DETAIL_SECTIONS) + "개 더 (/timings export)", NamedTextColor.GRAY));
            }
            return out;
        }
        out.add(Text.text("'" + plugin.trim() + "' 의 기록이 없다", NamedTextColor.GRAY));
        return out;
    }

    /** export 용 전체 보고서(모든 플러그인·모든 구간). */
    public static List<String> fullText(Report r) {
        List<String> out = new ArrayList<>();
        out.add(header(r).getString());
        int rank = 0;
        for (PluginStats p : r.plugins()) {
            out.add("");
            out.add(pluginLine(++rank, p, r));
            for (SectionStats s : p.sections()) out.add("   " + sectionLine(s));
        }
        return out;
    }
}
