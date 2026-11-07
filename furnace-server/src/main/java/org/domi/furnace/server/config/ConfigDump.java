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

        out.add("== furnace.properties (" + (s.existed() ? "시작 때 적용된 값" : "파일 없음 — 전부 기본값") + ") ==");
        out.add("파일: " + s.file() + (s.existed() ? ", 키 " + s.raw().size() + "개" : "") + ", 프리셋: " + preset);
        if (s.existed() && (currentSize != s.size() || currentModified != s.modified())) {
            out.add("※ 시작한 뒤 파일이 바뀌었습니다 — 아래는 시작 때 값이고, 고친 내용은 서버를 다시 켜야 적용됩니다");
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
        out.add(byName ? "'" + filter.strip() + "' 가 든 키 " + shown.size() + "개 (기본값과 다른 키 " + modified + "개 / 전체 " + total + "개)"
                : (all ? "전체 키 " + total + "개 중 기본값과 다른 키 " + modified + "개" : "기본값과 다른 키 " + modified + "개 (전체 " + total + "개, 전부 보려면 /furnace config all)"));
        out.addAll(shown);

        List<String> unknown = OptionKeys.unknownKeys(s.raw());
        if (!unknown.isEmpty() && !byName) {
            out.add("모르는 키 " + unknown.size() + "개 — 무시됩니다:");
            for (String k : unknown) {
                String near = OptionKeys.suggest(k);
                Integer ln = s.lines().get(k);
                out.add("  " + k + (ln == null ? "" : " (" + ln + "번째 줄)") + (near == null ? "" : " — '" + near + "' 를 말한 건가요?"));
            }
        }
        List<String> warnings = new ArrayList<>();
        OptionKeys.audit(s.effective(), s.lines(), w -> {
            if (!w.contains("는 모르는 키라 무시합니다")) warnings.add(w);
        });
        if (!warnings.isEmpty() && !byName) {
            out.add("경고 " + warnings.size() + "개:");
            for (String w : warnings) out.add("  ! " + w);
        }
        return out;
    }

    /** 현재 파일 상태와 함께 그린다(명령이 쓴다). 기동 때 찍은 값이 없으면 안내 한 줄. */
    public static List<String> render(String filter) {
        Snapshot s = snapshot;
        if (s == null) return List.of("설정을 아직 읽지 않았거나 읽지 못했습니다");
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
        String shownValue = eff == null ? "(없음 → 기본값 " + show(key, def) + ")" : show(key, eff);
        String raw = s.raw().getProperty(key);
        String source;
        if (raw == null) source = eff == null ? "기본값" : "프리셋 " + ConfigPresets.name(s.effective());
        else {
            Integer ln = s.lines().get(key);
            source = "파일" + (ln == null ? "" : " " + ln + "번째 줄") + (FurnaceProps.usesEnv(raw) ? ", 환경 변수에서" : "");
        }
        return "  " + (differs ? "* " : "  ") + key + " = " + shownValue + (differs && !def.isEmpty() ? "  (기본 " + show(key, def) + ")" : "") + "  [" + source + "]";
    }

    private static String show(String key, String value) {
        if (isSecret(key) && !value.isEmpty()) return "****(" + value.length() + "자)";
        return value.isEmpty() ? "\"\"" : value;
    }

    /** 알려진 비밀 키이거나 이름에 secret·token·password 가 든 키(앞으로 생길 키도 가린다). */
    static boolean isSecret(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return SECRET_KEYS.contains(key) || k.contains("secret") || k.contains("token") || k.contains("password");
    }

    private static boolean isFamily(String key) {
        return key.startsWith("plugins.log-level.") || key.startsWith("dimension-seed.");
    }

    /** 값이 기본값과 다른가. 기본값을 글자로 비교할 수 없는 키("CPU 수", "단계별")는 값이 있기만 하면 다르다고 본다. */
    static boolean differs(OptionKeys.Option o, String value) {
        if (value == null) return false;
        String v = value.strip();
        String d = o.def();
        if (d.isEmpty()) return !v.isEmpty();
        boolean comparable = o.type().equals("ON/OFF") || o.type().equals("수") || o.type().equals("글자") || o.type().equals("경로");
        if (!comparable || d.equals("CPU 수") || d.equals("단계별")) return true;
        if (o.type().equals("ON/OFF")) return !v.equalsIgnoreCase(d);
        if (o.type().equals("수")) {
            try {
                return Long.parseLong(v) != Long.parseLong(d);
            } catch (NumberFormatException e) {
                return !v.equals(d);
            }
        }
        return !v.equals(d);
    }
}
