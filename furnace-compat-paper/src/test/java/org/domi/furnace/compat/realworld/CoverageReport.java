package org.domi.furnace.compat.realworld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 실제 jar 대조 결과를 마크다운으로 쓴다: {@code ./gradlew :furnace-compat-paper:compatReport}.
 * 문서 {@code docs/feature-parity/compat-h4-coverage-2026-11-07.md} 는 이 도구가 만든 것이다(손으로 고치지 않는다).
 */
public final class CoverageReport {

    private CoverageReport() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        List<Path> jars = RealJars.list();
        ApiIndex index = new ApiIndex(CoverageReport.class.getClassLoader());
        List<Coverage.JarResult> results = Coverage.scan(jars, index);
        Map<String, LivePluginProbe.Result> live = new TreeMap<>();
        for (Path jar : jars) {
            String name = RealJars.pluginName(jar);
            if (name == null) continue;
            live.put(jar.getFileName().toString(), LivePluginProbe.run(jar, name, 5, Files.createTempDirectory("h4-live")));
        }
        Files.createDirectories(out.getParent());
        Files.writeString(out, render(results, live), StandardCharsets.UTF_8);
        if (args.length > 1) {
            Path baseline = Path.of(args[1]);
            Baseline.write(baseline, results, live);
            System.out.println("쓴 파일: " + out + ", " + baseline);
        } else {
            System.out.println("쓴 파일(기준표는 건드리지 않음): " + out);
        }
        System.exit(0); // 헤드리스 서버가 남긴 스레드를 기다리지 않는다
    }

    static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }

    static String render(List<Coverage.JarResult> results, Map<String, LivePluginProbe.Result> live) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 실제 jar 대 호환 계층 대조표 (H4, 생성 파일)\n\n");
        sb.append("`./gradlew :furnace-compat-paper:compatReport` 가 만든 파일이다. 직접 고치지 않는다.\n\n");
        sb.append("""
                실제 Bukkit·Paper API 로 컴파일된 jar(Maven Central 의 실제 플러그인과 플러그인 라이브러리)의 클래스 파일에서
                `org.bukkit.*` 등 서버가 줘야 하는 심볼(클래스·필드·메서드, 반환형까지 정확한 서명)을 읽어, `furnace-compat-paper` 가
                내놓는 클래스와 JVM 심볼 해석 규칙대로 견준 결과다. **정적 분석이라 요구 상한**이다 — 그 코드 경로가 실제로 실행되는지는 보지 않는다.
                "충족"은 요구한 서로 다른 심볼(클래스 + 멤버) 가운데 호환 계층에 있는 비율이다.

                """);

        sb.append("## 0. 실제 플러그인 jar 를 헤드리스 서버에 올린 결과\n\n");
        sb.append("`plugin.yml` 이 있는 실제 jar 를 `FurnaceTestServer` 에 올려 5틱 돌렸다. 실패는 서버 로그의 가장 안쪽 예외다.\n\n");
        sb.append("| jar | 상태 | 처음 만난 오류 |\n|---|---|---|\n");
        for (var e : live.entrySet()) {
            var r = e.getValue();
            sb.append("| `").append(e.getKey()).append("` | ").append(r.status()).append(" | ")
                    .append(r.firstError() == null ? "—" : "`" + r.firstError().replace("|", "\\|") + "`").append(" |\n");
        }
        sb.append("\n상태: `NOT_LOADED`(로드 자체 실패) < `LOADED_NOT_ENABLED`(onLoad·onEnable 이 던짐) < `ENABLED_THEN_CRASHED` < `ENABLED`.\n\n");
        sb.append("## 1. jar 별 Bukkit 계열 API(Bukkit·Paper·Spigot) 충족\n\n");
        sb.append("| jar | 종류 | 클래스 수 | 요구 심볼 | 없음 | 충족 |\n|---|---|---:|---:|---:|---:|\n");
        for (Coverage.JarResult r : results) {
            sb.append("| `").append(r.jar).append("` | ").append(r.plugin ? "플러그인" : "라이브러리").append(" | ").append(r.classCount)
                    .append(" | ").append(r.apiNeeded()).append(" | ").append(r.apiMissing()).append(" | ").append(pct(r.apiCoverage())).append(" |\n");
        }

        sb.append("\n## 2. 종류별 요구와 빠짐 (전체 jar 합집합)\n\n");
        sb.append("| 종류 | 요구 클래스 | 없는 클래스 | 요구 멤버(있는 클래스 기준) | 없는 멤버 | 없는 클래스의 멤버 호출 |\n|---|---:|---:|---:|---:|---:|\n");
        for (Coverage.Kind k : Coverage.Kind.values()) {
            Set<String> nc = new TreeSet<>(), mc = new TreeSet<>();
            Set<ClassRefs.Member> nm = new java.util.LinkedHashSet<>(), mm = new java.util.LinkedHashSet<>();
            int blocked = 0;
            for (Coverage.JarResult r : results) {
                nc.addAll(r.neededClasses.getOrDefault(k, Set.of()));
                mc.addAll(r.missingClasses.getOrDefault(k, Set.of()));
                nm.addAll(r.neededMembers.getOrDefault(k, Set.of()));
                mm.addAll(r.missingMembers.getOrDefault(k, Set.of()));
                blocked += r.blockedMembers.getOrDefault(k, 0);
            }
            sb.append("| ").append(k.label).append(" | ").append(nc.size()).append(" | ").append(mc.size()).append(" | ").append(nm.size())
                    .append(" | ").append(mm.size()).append(" | ").append(blocked).append(" |\n");
        }

        // 순위: 없는 Bukkit 계열 클래스
        Map<String, int[]> classRank = new TreeMap<>(); // [jar 수, 멤버 호출 수]
        for (Coverage.JarResult r : results) {
            for (Coverage.Kind k : Coverage.Kind.values()) {
                if (!k.isApi()) continue;
                for (String c : r.missingClasses.getOrDefault(k, Set.of())) {
                    int[] v = classRank.computeIfAbsent(c, x -> new int[2]);
                    v[0]++;
                    v[1] += r.blockedByClass.getOrDefault(c, 0);
                }
            }
        }
        List<Map.Entry<String, int[]>> byClass = new ArrayList<>(classRank.entrySet());
        byClass.sort(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> -e.getValue()[0]).thenComparingInt(e -> -e.getValue()[1])
                .thenComparing(Map.Entry::getKey));
        sb.append("\n## 3. 없는 Bukkit 계열 클래스 순위 (요구하는 jar 수 → 멤버 호출 수)\n\n");
        sb.append("전체 ").append(byClass.size()).append("개 중 위 80개. 이 순서가 다음 묶음의 우선순위 후보다.\n\n");
        sb.append("| 클래스 | jar 수 | 멤버 호출(합) |\n|---|---:|---:|\n");
        for (int i = 0; i < Math.min(80, byClass.size()); i++) {
            var e = byClass.get(i);
            sb.append("| `").append(e.getKey().replace('/', '.')).append("` | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");
        }

        // 패키지별 집계
        Map<String, int[]> pkg = new TreeMap<>();
        for (var e : byClass) {
            String c = e.getKey();
            String p = c.substring(0, c.lastIndexOf('/')).replace('/', '.');
            int[] v = pkg.computeIfAbsent(p, x -> new int[2]);
            v[0]++;
            v[1] += e.getValue()[1];
        }
        List<Map.Entry<String, int[]>> byPkg = new ArrayList<>(pkg.entrySet());
        byPkg.sort(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> -e.getValue()[1]).thenComparing(Map.Entry::getKey));
        sb.append("\n### 패키지별 (없는 클래스 수, 그 클래스들의 멤버 호출 합)\n\n| 패키지 | 없는 클래스 | 멤버 호출 |\n|---|---:|---:|\n");
        for (var e : byPkg) sb.append("| `").append(e.getKey()).append("` | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");

        // 순위: 있는 클래스의 없는 멤버
        Map<String, Integer> memberRank = new TreeMap<>();
        for (Coverage.JarResult r : results) for (ClassRefs.Member m : r.missingOnPresentClass) memberRank.merge(m.toString(), 1, Integer::sum);
        List<Map.Entry<String, Integer>> byMember = new ArrayList<>(memberRank.entrySet());
        byMember.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -e.getValue()).thenComparing(Map.Entry::getKey));
        sb.append("\n## 4. 클래스는 있는데 없는 멤버 순위\n\n");
        sb.append("전체 ").append(byMember.size()).append("개 중 위 120개 (요구하는 jar 수 순).\n\n| 멤버 | jar 수 |\n|---|---:|\n");
        for (int i = 0; i < Math.min(120, byMember.size()); i++) {
            sb.append("| `").append(byMember.get(i).getKey()).append("` | ").append(byMember.get(i).getValue()).append(" |\n");
        }

        // CraftBukkit·NMS
        sb.append("\n## 5. CraftBukkit·net.minecraft 요구 (H5~H7 의 수요)\n\n");
        Map<String, Integer> nms = new TreeMap<>();
        for (Coverage.JarResult r : results) {
            for (Coverage.Kind k : new Coverage.Kind[] {Coverage.Kind.CRAFTBUKKIT, Coverage.Kind.NMS}) {
                for (String c : r.neededClasses.getOrDefault(k, Set.of())) nms.merge(c, 1, Integer::sum);
            }
        }
        if (nms.isEmpty()) sb.append("정적으로 보이는 요구 없음.\n");
        else {
            sb.append("`org.bukkit.craftbukkit.*`·`net.minecraft.*` 를 상수 풀에 직접 이름으로 가진 클래스. 대부분 리플렉션 문자열로 가려져 정적으로는 일부만 보인다(그래서 실제 플러그인 기동이 필요하다).\n\n");
            sb.append("| 클래스 | jar 수 |\n|---|---:|\n");
            int shown = 0;
            List<Map.Entry<String, Integer>> l = new ArrayList<>(nms.entrySet());
            l.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -e.getValue()).thenComparing(Map.Entry::getKey));
            for (var e : l) {
                if (shown++ >= 60) break;
                sb.append("| `").append(e.getKey().replace('/', '.')).append("` | ").append(e.getValue()).append(" |\n");
            }
            sb.append("\n전체 ").append(l.size()).append("개.\n");
        }

        // 서버 제공 라이브러리
        sb.append("\n## 6. 서버가 줘야 하는 라이브러리(Paper 는 싣고 있음)\n\n");
        Map<String, Set<String>> libs = new TreeMap<>();
        Map<String, Boolean> libPresent = new TreeMap<>();
        ApiIndex idx = new ApiIndex(CoverageReport.class.getClassLoader());
        for (Coverage.JarResult r : results) {
            for (Coverage.Kind k : new Coverage.Kind[] {Coverage.Kind.SERVER_LIB, Coverage.Kind.ADVENTURE}) {
                for (String c : r.neededClasses.getOrDefault(k, Set.of())) {
                    String top = c.startsWith("net/kyori/") ? "net.kyori(Adventure)" : c.startsWith("com/google/common/") ? "com.google.common(Guava)"
                            : c.startsWith("com/google/gson/") ? "com.google.gson(Gson)" : c.startsWith("org/yaml/snakeyaml/") ? "org.yaml.snakeyaml(SnakeYAML)"
                            : c.startsWith("org/apache/commons/lang3/") ? "org.apache.commons.lang3" : c.startsWith("org/apache/logging/") ? "org.apache.logging(log4j)"
                            : c.startsWith("org/slf4j/") ? "org.slf4j" : c.startsWith("it/unimi/") ? "it.unimi.dsi.fastutil" : "com.mojang.brigadier";
                    libs.computeIfAbsent(top, x -> new TreeSet<>()).add(c);
                    libPresent.merge(top, idx.find(c) != null, (a, b) -> a && b);
                }
            }
        }
        sb.append("| 라이브러리 | 요구 클래스(서로 다른 것) | 서버 클래스패스에 전부 있나 |\n|---|---:|---|\n");
        for (var e : libs.entrySet()) sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue().size()).append(" | ").append(libPresent.get(e.getKey()) ? "예" : "아니오(일부 또는 전부 없음)").append(" |\n");
        sb.append("\n(\"서버 클래스패스\"는 호환 모듈 시험 클래스패스 기준이다. 플러그인이 `libraries:` 로 선언한 것은 자동 로딩(R1)이 받는다.)\n");

        sb.append("\n## 7. jar 별 상세 (없는 클래스, 위 30개)\n\n");
        for (Coverage.JarResult r : results) {
            Set<String> miss = new TreeSet<>();
            for (Coverage.Kind k : Coverage.Kind.values()) if (k.isApi()) miss.addAll(r.missingClasses.getOrDefault(k, Set.of()));
            sb.append("### `").append(r.jar).append("`\n\n");
            if (miss.isEmpty()) {
                sb.append("없는 클래스 없음.\n\n");
                continue;
            }
            List<String> sorted = new ArrayList<>(miss);
            sorted.sort(Comparator.<String>comparingInt(c -> -r.blockedByClass.getOrDefault(c, 0)).thenComparing(c -> c));
            sb.append("없는 클래스 ").append(miss.size()).append("개: ");
            for (int i = 0; i < Math.min(30, sorted.size()); i++) {
                if (i > 0) sb.append(", ");
                String c = sorted.get(i);
                sb.append('`').append(c.substring(c.lastIndexOf('/') + 1)).append('`');
                int n = r.blockedByClass.getOrDefault(c, 0);
                if (n > 0) sb.append('(').append(n).append(')');
            }
            if (sorted.size() > 30) sb.append(" …");
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
