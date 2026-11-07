package org.domi.furnace.compat.realworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * 실제 jar 대조의 기준표(후퇴 방지). {@code realworld-baseline.properties}: 지금 없는 심볼 수와 실제 플러그인 기동 상태.
 * 호환 계층이 좋아지면 {@code ./gradlew :furnace-compat-paper:compatReport} 가 새 값으로 다시 쓴다 — 나빠지면 시험이 막는다.
 */
public final class Baseline {

    public static final String RESOURCE = "/realworld-baseline.properties";

    private Baseline() {}

    public static Properties load() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Baseline.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("기준표가 없다: " + RESOURCE + " — compatReport 를 돌려 만든다");
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    public static void write(Path file, List<Coverage.JarResult> coverage, Map<String, LivePluginProbe.Result> live) throws IOException {
        Map<String, String> lines = new TreeMap<>();
        for (Coverage.JarResult r : coverage) lines.put("missing." + r.jar, Integer.toString(r.apiMissing()));
        for (var e : live.entrySet()) lines.put("live." + e.getKey(), e.getValue().status().name());
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("# H4 기준표 (생성 파일: ./gradlew :furnace-compat-paper:compatReport). 손으로 고치지 않는다.\n");
            w.write("# missing.<jar>: Bukkit 계열 API 에서 없는 심볼 수(늘면 시험 실패). live.<jar>: 실제 플러그인 기동 상태(나빠지면 실패).\n");
            for (var e : lines.entrySet()) w.write(e.getKey() + "=" + e.getValue() + "\n");
        }
    }
}
