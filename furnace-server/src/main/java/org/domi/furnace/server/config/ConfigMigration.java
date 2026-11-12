// furnace.properties 버전 마이그레이션. 파일의 config-version(없으면 1)이 서버의 CURRENT 보다 낮으면, 단계(STEPS)를 차례로 적용해
// 키 이름 바꾸기·지우기·기본값 바뀐 줄 옮기기를 파일에 반영한다. 파일은 줄 단위로만 고쳐 주석·순서·빈 줄을 그대로 둔다(줄바꿈은 LF 로 통일된다). 고치기 전에 원본을 옆에 백업한다.
//
// 설정 키 이름을 바꾸거나 없앨 때는 CURRENT 를 1 올리고 STEPS 에 {from=옛 버전, renames, removes} 한 줄을 더한다.
// 새 키를 더하기만 할 때는 필요 없다(없으면 기본값). 파일이 서버보다 새 버전이면 아무것도 하지 않고 경고만 한다.
// 기본값을 바꿀 때: furnace.properties 는 처음 만들 때 모든 키를 기본값으로 적으므로, 옛 기본값이 적힌 줄이 새 기본값을 가린다.
// 그때도 CURRENT 를 올리고 defaults 에 {키, 옛 값, 새 값} 을 더한다 — 주석이 아닌 "키=옛 값" 줄만 새 값으로 바꾼다.
package org.domi.furnace.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigMigration {

    private ConfigMigration() {}

    public static final String KEY = "config-version";
    /**
     * 이 서버가 쓰는 설정 형식 버전.
     * 2: plugins.compat.nms.spigot-mappings 기본 off → download(2026-11-12 도미니코 결정 — Bukkit 플러그인 폴더에 넣으면 바로 돈다).
     */
    public static final int CURRENT = 2;

    /** 옛 기본값으로 적힌 줄을 새 기본값으로 바꾼다(옛 값과 정확히 같은 줄만 — 다른 값을 적은 사람은 그대로). */
    public record DefaultChange(String key, String oldValue, String newValue) {}

    /** from 버전의 파일을 from+1 로 올리는 단계. renames: 옛 키 → 새 키, removes: 없어진 키(주석 처리한다), defaults: 기본값이 바뀐 키. */
    public record Step(int from, Map<String, String> renames, List<String> removes, List<DefaultChange> defaults) {
        public Step(int from, Map<String, String> renames, List<String> removes) { this(from, renames, removes, List.of()); }
    }

    /** 키 이름 바꾸기·기본값 바꾸기가 생기면 여기에 더한다(CURRENT 도 함께 올린다). */
    static final List<Step> STEPS = List.of(
            new Step(1, Map.of(), List.of(), List.of(
                    new DefaultChange("plugins.compat.nms.spigot-mappings", "off", "download"))));

    /** changes: 파일에 한 일을 사람 말로, backup: 옛 파일을 복사해 둔 곳. */
    public record Result(int from, int to, List<String> changes, Path backup) {}

    private static final Pattern VERSION_LINE = Pattern.compile("^\\s*" + Pattern.quote(KEY) + "\\s*[=:]\\s*(.*?)\\s*$");

    /** 서버의 기본 단계 표로 마이그레이션. 바꾼 게 없으면 null. */
    public static Result run(Path file, Consumer<String> warn) {
        return run(file, CURRENT, STEPS, warn);
    }

    static Result run(Path file, int current, List<Step> steps, Consumer<String> warn) {
        if (!Files.exists(file)) return null;
        List<String> lines;
        try {
            // Properties 와 같은 ISO-8859-1 로 읽고 그대로 쓴다 — 바이트가 달라지지 않는다.
            lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new RuntimeException("설정 로드 실패: " + file, e);
        }
        int versionLine = -1;
        int version = 1;   // 버전 줄이 없는 파일은 1 형식이다
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = VERSION_LINE.matcher(lines.get(i));
            if (m.matches()) {
                versionLine = i;
                try {
                    version = Integer.parseInt(m.group(1).strip());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("설정 '" + KEY + "' 값 '" + m.group(1) + "' 이(가) 잘못됐습니다: 1 이상의 정수");
                }
                if (version < 1) throw new IllegalArgumentException("설정 '" + KEY + "' 값 " + version + " 이(가) 잘못됐습니다: 1 이상의 정수");
            }
        }
        if (version > current) {
            warn.accept(file.getFileName() + " 의 " + KEY + "=" + version + " 이(가) 이 서버가 아는 형식(" + current
                    + ")보다 새롭습니다: 더 새 서버에서 만든 파일입니다. 모르는 키는 무시됩니다");
            return null;
        }
        if (version == current) return null;

        List<String> changes = new ArrayList<>();
        for (int v = version; v < current; v++) {
            Step step = null;
            for (Step s : steps) if (s.from() == v) step = s;
            if (step == null) continue;   // 이 버전 사이에는 바꿀 키가 없었다
            for (Map.Entry<String, String> r : step.renames().entrySet()) rename(lines, r.getKey(), r.getValue(), changes, warn);
            for (String key : step.removes()) remove(lines, key, v, changes);
            for (DefaultChange d : step.defaults()) resetDefault(lines, d, changes);
        }
        String versionText = KEY + "=" + current;
        if (versionLine >= 0) lines.set(versionLine, versionText);
        else lines.add(0, latin("# 설정 형식 버전 — 서버가 키 이름을 바꿀 때 파일을 알아서 옮기는 데 쓴다. 지우지 마세요.") + "\n" + versionText);
        changes.add(KEY + " " + version + " → " + current);

        Path backup = backupPath(file, version);
        try {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new RuntimeException("설정 마이그레이션 실패(원본 " + file + " 을(를) 확인하세요): " + e.getMessage(), e);
        }
        return new Result(version, current, List.copyOf(changes), backup);
    }

    private static void rename(List<String> lines, String from, String to, List<String> changes, Consumer<String> warn) {
        Pattern p = keyLine(from);
        Pattern target = keyLine(to);
        boolean targetExists = lines.stream().anyMatch(l -> target.matcher(l).matches());
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = p.matcher(lines.get(i));
            if (!m.matches()) continue;
            if (targetExists) {
                // 새 키를 이미 적어 둔 사람의 값을 덮어쓰지 않는다 — 옛 줄만 주석으로 돌린다.
                lines.set(i, latin("# (옛 키 " + from + " 는 " + to + " 가 이미 있어 쓰지 않는다) ") + lines.get(i).strip());
                warn.accept("마이그레이션: '" + from + "' → '" + to + "' — 새 키가 이미 있어 옛 줄을 주석 처리만 했습니다");
                changes.add(from + " 줄을 주석 처리(새 키 " + to + " 가 이미 있음)");
            } else {
                lines.set(i, m.group(1) + to + m.group(2));
                changes.add(from + " → " + to);
                targetExists = true;   // 같은 키가 두 줄이어도 첫 줄만 옮긴다
            }
        }
    }

    private static void remove(List<String> lines, String key, int fromVersion, List<String> changes) {
        Pattern p = keyLine(key);
        for (int i = 0; i < lines.size(); i++) {
            if (p.matcher(lines.get(i)).matches()) {
                lines.set(i, latin("# (형식 " + fromVersion + " 의 키라 더는 쓰지 않는다) ") + lines.get(i).strip());
                changes.add(key + " 줄을 주석 처리(없어진 키)");
            }
        }
    }

    private static void resetDefault(List<String> lines, DefaultChange d, List<String> changes) {
        Pattern p = Pattern.compile("^(\\s*" + Pattern.quote(d.key()) + "\\s*[=:]\\s*)" + Pattern.quote(d.oldValue()) + "\\s*$");
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = p.matcher(lines.get(i));
            if (!m.matches()) continue;
            lines.set(i, m.group(1) + d.newValue());
            changes.add(d.key() + " 기본값이 " + d.oldValue() + " → " + d.newValue() + " 로 바뀌어 적힌 " + d.oldValue() + " 를 " + d.newValue()
                    + " 로 옮겼다(그대로 두려면 다시 " + d.oldValue() + " 로 적으라)");
        }
    }

    /** 파일은 ISO-8859-1 로 읽고 쓰므로(바이트 보존), 우리가 넣는 한글 주석은 UTF-8 바이트를 그대로 싣는 글자열로 바꿔 넣는다. */
    private static String latin(String s) {
        return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    /** 키 이름 뒤가 =·: 나 공백인 줄(앞 공백 허용). group(1)=앞 공백, group(2)=키 뒤 나머지. */
    private static Pattern keyLine(String key) {
        return Pattern.compile("^(\\s*)" + Pattern.quote(key) + "(\\s*[=:\\s].*|)$");
    }

    private static Path backupPath(Path file, int version) {
        String base = file.getFileName() + ".v" + version + ".bak";
        Path dir = file.toAbsolutePath().getParent();
        Path p = dir.resolve(base);
        for (int n = 2; Files.exists(p); n++) p = dir.resolve(base + n);
        return p;
    }
}
