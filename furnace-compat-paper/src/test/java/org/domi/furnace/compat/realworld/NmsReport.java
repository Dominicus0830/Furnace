package org.domi.furnace.compat.realworld;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.compat.realworld.NmsScan.Group;
import org.domi.furnace.compat.realworld.NmsScan.JarScan;

/**
 * H5 호출 빈도 표를 마크다운으로 쓴다: {@code ./gradlew :furnace-compat-paper:nmsReport}.
 * {@code docs/feature-parity/compat-h5-nms-calls-2026-11-07.md} 는 이 도구가 만든 것이다(손으로 고치지 않는다).
 */
public final class NmsReport {

    private NmsReport() {}

    private static final int TOP = 40;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        List<JarScan> scans = NmsScan.scan(RealJars.list());
        Files.createDirectories(out.getParent());
        Files.writeString(out, render(scans), StandardCharsets.UTF_8);
        System.out.println("쓴 파일: " + out);
    }

    static String render(List<JarScan> scans) {
        StringBuilder sb = new StringBuilder();
        sb.append("# NMS·CraftBukkit 호출 빈도 표 (H5, 생성 파일)\n\n");
        sb.append("`./gradlew :furnace-compat-paper:nmsReport` 가 만든 파일이다. 직접 고치지 않는다. 해석과 설계는 [`compat-h5-2026-11-07.md`](compat-h5-2026-11-07.md).\n\n");
        sb.append("""
                실제 Bukkit·Paper 플러그인과 플러그인 라이브러리 jar(Maven Central 에 있는 것만)의 클래스 파일 상수 풀에서 `net.minecraft.*`,
                `org.bukkit.craftbukkit.*`, `com.mojang.*`, 그리고 NMS 접근에 따라붙는 리플렉션 호출·문자열 리터럴을 읽어 센 값이다.
                **정적 분석이라 요구 상한**이다(안 타는 코드 경로도 센다). "멤버 참조"는 jar 마다 서로 다른 `owner#이름`(오버로드는 합침)의 수이지 실행 시 호출 횟수가 아니다.
                버전 접미사 패키지(`v1_21_R1`)는 지우고 합쳤다(같은 클래스를 버전별 모듈이 되풀이해 부르는 것은 한 번으로 센다).
                리플렉션 문자열로 조립한 이름은 문자열 조각만 보이므로 **하한**이다. 이름 체계(Spigot 난독화 / Mojang)는 멤버 이름이 1~2글자인지,
                클래스 이름이 `$a` 꼴인지, 알려진 이름 모양인지로 어림한 값이다(어림 규칙은 `NmsScan` 소스).

                """);

        // 0. 표본
        sb.append("## 0. 표본 (jar 별 요약)\n\n");
        sb.append("| jar | 종류 | 클래스 | CraftBukkit(클래스/멤버) | NMS(클래스/멤버) | 구 NMS 패키지(클래스) | 버전 접미사 | 이름 체계 어림 | 리플렉션 호출 | NMS 이름 리터럴 | 매니페스트 매핑 |\n");
        sb.append("|---|---|---:|---:|---:|---:|---|---|---:|---:|---|\n");
        for (JarScan s : scans) {
            sb.append("| `").append(s.jar).append("` | ").append(s.plugin ? "플러그인" : "라이브러리").append(" | ").append(s.classCount)
                    .append(" | ").append(s.classes(Group.CRAFTBUKKIT).size()).append('/').append(s.members(Group.CRAFTBUKKIT).size())
                    .append(" | ").append(s.classes(Group.NMS).size()).append('/').append(s.members(Group.NMS).size())
                    .append(" | ").append(s.classes(Group.NMS_LEGACY).size())
                    .append(" | ").append(s.versions.isEmpty() ? "—" : versionRange(s.versions))
                    .append(" | ").append(s.namespaceGuess())
                    .append(" | ").append(s.reflectionCalls)
                    .append(" | ").append(s.nameLiterals.size() + s.fragmentLiterals.size())
                    .append(" | ").append(s.manifestNamespace == null ? "—" : "`" + s.manifestNamespace + "`").append(" |\n");
        }
        long touching = scans.stream().filter(JarScan::touchesServerInternals).count();
        sb.append("\n서버 내부(NMS·CraftBukkit)에 닿는 jar: ").append(touching).append(" / ").append(scans.size()).append("개.\n\n");

        // 1. 버전 접미사
        sb.append("## 1. 버전 접미사(`v1_X_RN`) 분포\n\n");
        Map<String, List<String>> byVersion = new TreeMap<>();
        for (JarScan s : scans) for (String v : s.versions) byVersion.computeIfAbsent(v, k -> new ArrayList<>()).add(s.jar);
        sb.append("| 접미사 | jar 수 | jar |\n|---|---:|---|\n");
        for (var e : byVersion.entrySet()) {
            sb.append("| `").append(e.getKey()).append("` | ").append(e.getValue().size()).append(" | ")
                    .append(String.join(", ", e.getValue().stream().map(x -> "`" + x + "`").toList())).append(" |\n");
        }
        long pkgVersion = scans.stream().filter(s -> s.usesPackageNameForVersion && s.touchesServerInternals()).count();
        sb.append("\n`Class#getPackage` + `Package#getName` 을 함께 부르고 서버 내부에 닿는 jar(패키지 이름에서 버전 접미사를 읽는 옛 방식의 후보): ")
                .append(pkgVersion).append("개.\n\n");

        // 2. CraftBukkit 클래스
        section(sb, "2. CraftBukkit 클래스 (접미사 제거, jar 수 → 멤버 참조 수)", scans, Group.CRAFTBUKKIT, false);
        members(sb, "3. CraftBukkit 멤버 참조 상위 (참조한 jar 수)", scans, Group.CRAFTBUKKIT, true);

        // 4. NMS 클래스
        section(sb, "4. net.minecraft 클래스 (jar 수 → 멤버 참조 수, 이름 모양)", scans, Group.NMS, true);
        members(sb, "5. net.minecraft 멤버 참조 상위 — 읽을 수 있는 이름 (참조한 jar 수)", scans, Group.NMS, false);
        obfSummary(sb, scans);
        section(sb, "6. 구 NMS 패키지 `net.minecraft.server.v1_X_RN.*` 클래스", scans, Group.NMS_LEGACY, true);

        // 7. 서버가 싣고 있는 Mojang 라이브러리
        sb.append("## 7. NMS 와 함께 부르는 Mojang·Netty 라이브러리\n\n");
        sb.append("| 갈래 | 서로 다른 클래스 | 참조한 jar 수(최대) | 많이 쓰는 클래스(jar 수) |\n|---|---:|---:|---|\n");
        for (Group g : new Group[] {Group.AUTHLIB, Group.SERIALIZATION, Group.BRIGADIER, Group.NETTY}) {
            Map<String, Integer> c = NmsScan.jarCounts(scans, g);
            int max = c.values().stream().max(Integer::compare).orElse(0);
            sb.append("| ").append(label(g)).append(" | ").append(c.size()).append(" | ").append(max).append(" | ")
                    .append(top(c, 4)).append(" |\n");
        }
        sb.append('\n');

        // 8. 리플렉션·리터럴
        sb.append("## 8. 리플렉션과 문자열로 가려진 접근\n\n");
        int reflJars = (int) scans.stream().filter(s -> s.touchesServerInternals() && s.reflectionCalls > 0).count();
        sb.append("서버 내부에 닿는 jar 중 리플렉션 호출(`Class#forName`·`getMethod`·`Method#invoke`·`MethodHandles.Lookup#find*` 등)을 가진 jar: ")
                .append(reflJars).append("개.\n\n");
        Map<String, Integer> bridge = new TreeMap<>();
        for (JarScan s : scans) if (s.touchesServerInternals()) for (String l : s.bridgeLiterals) bridge.merge(l, 1, Integer::sum);
        sb.append("서버 내부에 닿는 jar 가 문자열로 들고 있는 대표 식별자(이름이 일반적이라 과대 계상될 수 있음): ")
                .append(bridge.isEmpty() ? "없음" : top(bridge, 20)).append("\n\n");
        Set<String> names = NmsScan.literalUnion(scans, s -> s.nameLiterals);
        sb.append("NMS·CraftBukkit·Mojang 클래스 이름 리터럴(전체 이름이 문자열): ").append(names.size()).append("개. 앞 25개:\n\n");
        names.stream().limit(25).forEach(n -> sb.append("- `").append(n).append("`\n"));
        Set<String> frags = NmsScan.literalUnion(scans, s -> s.fragmentLiterals);
        sb.append("\n패키지 조각 리터럴(뒤에 버전·클래스 이름을 이어 붙이는 꼴, `.` 로 끝남): ").append(frags.size()).append("개.\n\n");
        frags.stream().limit(25).forEach(n -> sb.append("- `").append(n).append("`\n"));
        sb.append('\n');
        return sb.toString();
    }

    private static String versionRange(Set<String> versions) {
        List<String> l = new ArrayList<>(versions);
        return l.size() == 1 ? l.get(0) : l.get(0) + "…" + l.get(l.size() - 1) + " (" + l.size() + "개)";
    }

    private static String label(Group g) {
        return switch (g) {
            case AUTHLIB -> "`com.mojang.authlib`";
            case SERIALIZATION -> "`com.mojang.serialization`·`datafixers`";
            case BRIGADIER -> "`com.mojang.brigadier`";
            case NETTY -> "`io.netty`";
            default -> g.name();
        };
    }

    private static String top(Map<String, Integer> counts, int n) {
        return counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(n).map(e -> "`" + simple(e.getKey()) + "`(" + e.getValue() + ")").reduce((a, b) -> a + ", " + b).orElse("—");
    }

    private static String simple(String internal) {
        return internal.replace('/', '.');
    }

    private static void section(StringBuilder sb, String title, List<JarScan> scans, Group g, boolean style) {
        Map<String, Integer> jars = NmsScan.jarCounts(scans, g);
        Map<String, Integer> refs = NmsScan.memberRefsByOwner(scans, g);
        sb.append("## ").append(title).append("\n\n");
        sb.append("서로 다른 클래스 ").append(jars.size()).append("개 중 위 ").append(Math.min(TOP, jars.size())).append("개.\n\n");
        sb.append(style ? "| 클래스 | jar 수 | 멤버 참조 | 이름 모양 |\n|---|---:|---:|---|\n" : "| 클래스 | jar 수 | 멤버 참조 |\n|---|---:|---:|\n");
        jars.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -e.getValue())
                        .thenComparingInt(e -> -refs.getOrDefault(e.getKey(), 0)).thenComparing(Map.Entry::getKey))
                .limit(TOP).forEach(e -> {
                    sb.append("| `").append(simple(e.getKey())).append("` | ").append(e.getValue()).append(" | ").append(refs.getOrDefault(e.getKey(), 0));
                    if (style) sb.append(" | ").append(styleLabel(e.getKey()));
                    sb.append(" |\n");
                });
        sb.append('\n');
    }

    private static String styleLabel(String internal) {
        return switch (NmsScan.styleOf(internal)) {
            case SPIGOT -> "Spigot";
            case MOJANG -> "Mojang";
            default -> NmsScan.obfClass(internal) ? "난독화(`$a`)" : "공통·불명";
        };
    }

    private static void obfSummary(StringBuilder sb, List<JarScan> scans) {
        Map<String, Integer> byOwner = new TreeMap<>();
        int total = 0;
        for (JarScan s : scans) {
            for (String key : s.members(Group.NMS)) {
                int h = key.indexOf('#');
                if (key.startsWith("<", h + 1) || !NmsScan.obfMember(key.substring(h + 1))) continue;
                total++;
                byOwner.merge(key.substring(0, h), 1, Integer::sum);
            }
        }
        sb.append("1~2글자 난독화로 보이는 멤버 참조(Spigot 이름 체계, jar 별 서로 다른 것의 합): ").append(total).append("개. 소유 클래스별 상위 12개: ")
                .append(top(byOwner, 12)).append("\n\n");
    }

    private static void members(StringBuilder sb, String title, List<JarScan> scans, Group g, boolean all) {
        Map<String, Integer> counts = new TreeMap<>(NmsScan.memberJarCounts(scans, g));
        if (!all) counts.keySet().removeIf(k -> NmsScan.obfMember(k.substring(k.indexOf('#') + 1)));
        sb.append("## ").append(title).append("\n\n");
        sb.append("서로 다른 멤버 참조 ").append(counts.size()).append("개 중 위 ").append(Math.min(TOP, counts.size())).append("개.\n\n");
        sb.append("| 멤버 | jar 수 |\n|---|---:|\n");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP).forEach(e -> sb.append("| `").append(simple(e.getKey())).append("` | ").append(e.getValue()).append(" |\n"));
        sb.append('\n');
    }
}
