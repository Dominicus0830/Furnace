// /furnace config — 시작 때 실제로 적용된 furnace.properties 값을 보여 준다: 기본값과 다른 키, 값이 어디서 왔는지(파일·프리셋·환경 변수),
// 모르는 키(오타, 줄 번호), 위험한 값 경고. 읽기 전용이고 비밀값(velocity-secret 등)은 가린다.
// 기동 때 {@link #capture} 가 값을 한 번 찍어 두므로 파일을 나중에 고쳐도 '지금 돌고 있는 값' 이 보인다(고쳤다는 것만 알린다).
package org.domi.furnace.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.domi.furnace.server.i18n.Lang;

public final class ConfigDump {

    private ConfigDump() {}

    /** 값을 가리는 키(덤프·로그에 그대로 보이면 안 된다). */
    public static final Set<String> SECRET_KEYS = Set.of("velocity-secret", "metrics.prometheus.token");

    /** 기동 때 찍은 모습. raw=파일 그대로, effective=환경 변수·프리셋 적용 뒤. */
    public record Snapshot(Path file, boolean existed, Properties raw, Properties effective, Map<String, Integer> lines,
                           long size, long modified) {}

    private static volatile Snapshot snapshot;

    /** 기동 때 한 번(마이그레이션·가져오기 뒤). 읽지 못하면 null 로 남는다. */
    public static void capture(Path file) {
        snapshot = take(file);
    }

    public static Snapshot snapshot() { return snapshot; }

    public static void clearForTest() { snapshot = null; }

    static Snapshot take(Path file) {
        try {
            boolean existed = Files.exists(file);
            return new Snapshot(file, existed, FurnaceProps.readRaw(file), FurnaceProps.read(file), lineNumbers(file),
                    existed ? Files.size(file) : 0, existed ? Files.getLastModifiedTime(file).toMillis() : 0);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 파일의 키 → 줄 번호(1부터, 같은 키가 또 나오면 마지막 줄 — Properties 가 마지막 값을 쓰기 때문). 파일이 없거나 못 읽으면 빈 표. */
    public static Map<String, Integer> lineNumbers(Path file) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!Files.exists(file)) return out;
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            return out;
        }
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).stripLeading();
            if (l.isEmpty() || l.charAt(0) == '#' || l.charAt(0) == '!') continue;
            int end = 0;
            while (end < l.length() && "=: \t".indexOf(l.charAt(end)) < 0) end++;
            if (end > 0) out.put(l.substring(0, end), i + 1);
        }
        return out;
    }

    /**
     * 덤프 본문(줄 목록). filter: null/빈 값 = 기본값과 다른 키만, "all" = 전부, 그 밖 = 키에 그 글자가 든 항목(전부 중에서).
     */
    public static List<String> render(Snapshot s, String filter, long currentSize, long currentModified) {
        List<String> out = new ArrayList<>();
        String f = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        boolean all = f.equals("all");
        boolean byName = !f.isEmpty() && !all;
        String preset = ConfigPresets.name(s.effective());

        out.add(Lang.t("cfg.dump.header", Lang.t(s.existed() ? "cfg.dump.applied" : "cfg.dump.no-file")));
        out.add(Lang.t("cfg.dump.file", s.file(), s.existed() ? Lang.t("cfg.dump.file-keys", s.raw().size()) : "", preset));
        if (s.existed() && (currentSize != s.size() || currentModified != s.modified())) {
            out.add(Lang.t("cfg.dump.changed-note"));
        }

        List<String> shown = new ArrayList<>();
        int modified = 0;
        int total = 0;
        for (OptionKeys.Option o : OptionKeys.all()) {
            total++;
            String eff = s.effective().getProperty(o.key());
            boolean differs = differs(o, eff);
            if (differs) modified++;
            if (byName ? !o.key().toLowerCase(Locale.ROOT).contains(f) : (!all && !differs)) continue;
            shown.add(line(s, o.key(), eff, o.def(), differs));
        }
        // 접두 가족(플러그인별 로그 수준, 차원별 시드)은 이름이 열려 있어 파일에 있는 것만 보인다
        for (String k : new TreeSet<>(s.effective().stringPropertyNames())) {
            if (OptionKeys.isKnown(k) && isFamily(k)) {
                modified++;
                if (byName && !k.toLowerCase(Locale.ROOT).contains(f)) continue;
                shown.add(line(s, k, s.effective().getProperty(k), "", true));
            }
        }
        out.add(byName ? Lang.t("cfg.dump.count-name", filter.strip(), shown.size(), modified, total)
                : (all ? Lang.t("cfg.dump.count-all", total, modified) : Lang.t("cfg.dump.count-default", modified, total)));
        out.addAll(shown);

        List<String> unknown = OptionKeys.unknownKeys(s.raw());
        if (!unknown.isEmpty() && !byName) {
            out.add(Lang.t("cfg.dump.unknown-head", unknown.size()));
            for (String k : unknown) {
                String near = OptionKeys.suggest(k);
                Integer ln = s.lines().get(k);
                out.add("  " + k + (ln == null ? "" : Lang.t("cfg.dump.at-line", ln)) + (near == null ? "" : Lang.t("cfg.dump.did-you-mean", near)));
            }
        }
        List<String> warnings = new ArrayList<>();
        OptionKeys.auditValues(s.effective(), warnings::add);   // 모르는 키는 위에서 따로 보여 준다
        if (!warnings.isEmpty() && !byName) {
            out.add(Lang.t("cfg.dump.warnings-head", warnings.size()));
            for (String w : warnings) out.add("  ! " + w);
        }
        return out;
    }

    /** 현재 파일 상태와 함께 그린다(명령이 쓴다). 기동 때 찍은 값이 없으면 안내 한 줄. */
    public static List<String> render(String filter) {
        Snapshot s = snapshot;
        if (s == null) return List.of(Lang.t("cfg.dump.not-read"));
        long size = 0;
        long mod = 0;
        try {
            if (Files.exists(s.file())) {
                size = Files.size(s.file());
                mod = Files.getLastModifiedTime(s.file()).toMillis();
            }
        } catch (IOException ignored) {
            // 상태를 모르면 바뀌지 않은 것으로 본다
            size = s.size();
            mod = s.modified();
        }
        return render(s, filter, size, mod);
    }

    private static String line(Snapshot s, String key, String eff, String def, boolean differs) {
        String shownValue = eff == null ? Lang.t("cfg.dump.not-set", show(key, def)) : show(key, eff);
        String raw = s.raw().getProperty(key);
        String source;
        if (raw == null) source = eff == null ? Lang.t("cfg.dump.src-default") : Lang.t("cfg.dump.src-preset", ConfigPresets.name(s.effective()));
        else {
            Integer ln = s.lines().get(key);
            source = (ln == null ? Lang.t("cfg.dump.src-file") : Lang.t("cfg.dump.src-file-line", ln)) + (FurnaceProps.usesEnv(raw) ? Lang.t("cfg.dump.src-env") : "");
        }
        return "  " + (differs ? "* " : "  ") + key + " = " + shownValue + (differs && !def.isEmpty() ? "  " + Lang.t("cfg.dump.default-part", show(key, def)) : "") + "  [" + source + "]";
    }

    private static String show(String key, String value) {
        if (isSecret(key) && !value.isEmpty()) return Lang.t("cfg.dump.secret", value.length());
        if (value.equals(OptionKeys.DEF_CPU_COUNT)) return Lang.t("cfg.dump.def-cpu-count");
        if (value.equals(OptionKeys.DEF_PER_STAGE)) return Lang.t("cfg.dump.def-per-stage");
        return value.isEmpty() ? "\"\"" : value;
    }

    /** 알려진 비밀 키이거나 이름에 secret·token·password 가 든 키(앞으로 생길 키도 가린다). */
    static boolean isSecret(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return SECRET_KEYS.contains(key) || k.contains("secret") || k.contains("token") || k.contains("password");
    }

    private static boolean isFamily(String key) {
        return key.startsWith("plugins.log-level.") || key.startsWith("dimension-seed.") || key.startsWith("spawn.limit.") || key.startsWith("spawn.player-limit.");
    }

    /** 값이 기본값과 다른가. 기본값을 글자로 비교할 수 없는 키({@link OptionKeys#DEF_CPU_COUNT}·{@link OptionKeys#DEF_PER_STAGE})는 값이 있기만 하면 다르다고 본다. */
    static boolean differs(OptionKeys.Option o, String value) {
        if (value == null) return false;
        String v = value.strip();
        String d = o.def();
        if (d.isEmpty()) return !v.isEmpty();
        boolean comparable = o.type().equals("ON/OFF") || o.type().equals("number") || o.type().equals("text") || o.type().equals("path");
        if (!comparable || d.equals(OptionKeys.DEF_CPU_COUNT) || d.equals(OptionKeys.DEF_PER_STAGE)) return true;
        if (o.type().equals("ON/OFF")) return !v.equalsIgnoreCase(d);
        if (o.type().equals("number")) {
            try {
                return Long.parseLong(v) != Long.parseLong(d);
            } catch (NumberFormatException e) {
                return !v.equals(d);
            }
        }
        return !v.equals(d);
    }
}
