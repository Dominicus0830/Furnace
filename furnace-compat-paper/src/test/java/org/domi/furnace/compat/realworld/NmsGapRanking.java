package org.domi.furnace.compat.realworld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.domi.furnace.compat.nms.NmsCompatReport;
import org.domi.furnace.compat.nms.NmsSymbols;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * T-234: NMS shim 점검({@link NmsShimAudit}) 결과에 인기 가중치를 입혀 "무엇부터 채울까" 순위를 만들고, 플러그인이 문자열로 가리키는 NMS 클래스
 * ({@code Class.forName("net.minecraft…")} 류 감지)를 따로 센다(설계 4.4: 감지 대상 클래스는 주요 멤버를 채운 뒤에만 연다 — 빈 클래스가 있으면 플러그인이
 * Bukkit API 대체 경로 대신 NMS 경로로 들어갔다가 첫 멤버에서 실패한다).
 *
 * <p>점수 = 그 구멍을 부르는 표본 플러그인마다 가중치 합. 가중치 = log10(내려받은 수 + 10) (매니페스트가 없으면 1). 운영자가 보내 준 덫 기록
 * ({@code /furnace compat gaps}, T-233)의 호출 수는 {@code --gaps} 로 같은 심볼에 더한다(점수 += 호출 수).
 */
public final class NmsGapRanking {

    private NmsGapRanking() {}

    /** 감지 문자열: 점으로 쓴 NMS·CraftBukkit 클래스 이름(리터럴). 버전 접미가 낀 이름은 다른 판 모듈이라 뺀다. */
    private static final Pattern DETECT = Pattern.compile("(net\\.minecraft\\.[\\w.$]+|org\\.bukkit\\.craftbukkit\\.[A-Z][\\w.$]*)");

    private static final Pattern OTHER_VERSION = Pattern.compile("\\.v\\d+_\\d+");

    /** 매니페스트(sample-manifest.tsv, tools/compat/fetch-sample-plugins.py)에서 jar 이름 → 내려받은 수. 파일이 없으면 빈 표. */
    static Map<String, Long> downloads(Path manifest) throws IOException {
        Map<String, Long> out = new HashMap<>();
        if (manifest == null || !Files.isRegularFile(manifest)) return out;
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] f = lines.get(i).split("\t", -1);
            if (f.length >= 4) out.put(f[3], Long.parseLong(f[2]));
        }
        return out;
    }

    static double weight(Map<String, Long> downloads, String jar) {
        Long d = downloads.get(jar);
        return d == null ? 1.0 : Math.log10(d + 10.0);
    }

    /** jar 마다, 문자열 상수로만 가리킨 NMS 클래스 이름들(메서드 안 LDC·필드 상수). */
    static Map<String, Set<String>> stringTargets(List<Path> jars) throws IOException {
        Map<String, Set<String>> byClass = new TreeMap<>();
        for (Path jar : jars) {
            String name = jar.getFileName().toString();
            NmsCompatReport.Read read = NmsCompatReport.read(jar);
            for (byte[] b : read.candidates()) {
                try {
                    new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public FieldVisitor visitField(int access, String n, String d, String s, Object value) {
                            note(value);
                            return null;
                        }

                        @Override
                        public MethodVisitor visitMethod(int access, String n, String d, String s, String[] ex) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitLdcInsn(Object value) {
                                    note(value);
                                }
                            };
                        }

                        private void note(Object v) {
                            if (v instanceof String s && DETECT.matcher(s).matches() && !OTHER_VERSION.matcher(s).find()) {
                                byClass.computeIfAbsent(s, k -> new TreeSet<>()).add(name);
                            }
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (RuntimeException ignored) {
                    // 읽지 못하는 클래스는 센 것에서 뺀다
                }
            }
        }
        return byClass;
    }

    /** 순위 문서(마크다운). trapCalls 는 {@code 소유|이름} → 실제 덫 호출 수(없으면 빈 표). */
    static String render(NmsShimAudit.Result r, Map<String, Long> downloads, Map<String, Set<String>> targets, String when) {
        record Scored(NmsShimAudit.Row row, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (NmsShimAudit.Row x : r.rows()) {
            if (x.category() == NmsShimAudit.Category.NOT_PAPER || x.category() == NmsShimAudit.Category.UNKNOWN) continue;   // 구멍이 아니다
            double s = 0;
            for (String j : x.jars()) s += weight(downloads, j);
            scored.add(new Scored(x, s));
        }
        scored.sort(Comparator.<Scored>comparingDouble(x -> -x.score()).thenComparing(x -> x.row().miss().key()));

        long analyzed = r.jars().stream().filter(j -> j.symbols() > 0).count();
        long skipped = r.jars().stream().filter(NmsShimAudit.JarLine::skipped).count();
        long noNms = r.jars().stream().filter(j -> !j.skipped() && j.symbols() == 0).count();
        long symbols = r.jars().stream().mapToLong(NmsShimAudit.JarLine::symbols).sum();
        long present = r.jars().stream().mapToLong(NmsShimAudit.JarLine::present).sum();
        double wSym = 0, wPresent = 0;
        for (NmsShimAudit.JarLine j : r.jars()) {
            double w = weight(downloads, j.jar());
            wSym += w * j.symbols();
            wPresent += w * j.present();
        }

        StringBuilder b = new StringBuilder();
        b.append("# 호환 구멍 순위 (").append(when).append(", NmsGapRanking 자동 생성)\n\n");
        b.append("표본 jar ").append(r.jars().size()).append("개 중 Mojang 이름으로 NMS 를 직접 부르는 것 ").append(analyzed).append("개, NMS 를 안 부르는 것 ")
                .append(noNms).append("개, Spigot 이름이라 건너뜀(이름 대응표가 있는 PC 에서 다시) ").append(skipped).append("개. 설계 `broad-support-version-scaling-2026-11-20.md` 4.4·5.4, T-234.\n\n");
        b.append("## 지표 — 부르는 심볼 중 지원 비율 (목표 95% 이상)\n\n");
        b.append("| 기준 | 지원 | 전체 | 비율 |\n|---|--:|--:|--:|\n");
        b.append(String.format(Locale.ROOT, "| 심볼 단순 합 | %d | %d | %.1f%% |\n", present, symbols, symbols == 0 ? 0.0 : 100.0 * present / symbols));
        b.append(String.format(Locale.ROOT, "| 인기 가중 합 | %.0f | %.0f | %.1f%% |\n", wPresent, wSym, wSym == 0 ? 0.0 : 100.0 * wPresent / wSym));
        b.append("\n(심볼은 플러그인마다 서로 다른 것만 센다. 플러그인 하나가 같은 심볼을 여러 번 불러도 한 번.)\n\n");
        b.append("## 채울 순서 — 점수 순 (점수 = 부르는 플러그인마다 log10(내려받은 수 + 10) 의 합)\n\n");
        b.append("| # | 점수 | 플러그인 수 | 분류 | 구멍 | 쓰는 jar |\n|--:|--:|--:|---|---|---|\n");
        int n = 0;
        for (Scored s : scored) {
            if (n++ >= 80) break;
            String jars = s.row().jars().size() <= 3 ? String.join(", ", s.row().jars()) : String.join(", ", s.row().jars().stream().limit(3).toList()) + " 외";
            b.append(String.format(Locale.ROOT, "| %d | %.1f | %d | %s | `%s` | %s |\n", n, s.score(), s.row().jars().size(),
                    s.row().category().label, NmsShimAudit.readable(s.row().miss()), jars));
        }
        b.append("\n읽는 법: 여러 판을 한 jar 에 담는 플러그인(예: Geyser·orebfuscator)은 `WorldServer`·`IBlockData` 같은 Spigot 이름 코드 길도 함께 가져서 \"클래스 없음\"으로 잡힌다 — ")
                .append("그 길은 Mojang 이름 서버에서 안 쓰이므로 진짜 구멍이 아니다. 이름 대응표가 있는 PC 에서 같은 표본을 Spigot 대응표를 켜고 다시 돌리면 가려진다.\n");
        b.append("\n구멍 총 ").append(scored.size()).append("건(위 80건만 — 전체는 같은 실행의 .tsv). \"Paper 에도 없음\"·\"표 모름\"은 구멍이 아니라 뺐다.\n");

        b.append("\n## 문자열로 가리키는 NMS 클래스 — `Class.forName` 감지 대상\n\n");
        b.append("플러그인이 클래스 이름 문자열로 \"이 서버가 Mojang 이름 Paper 인가\"를 찾아보는 것들이다. **shim 에 빈 클래스를 만들면 이 감지가 참이 되어 플러그인이 NMS 경로로 들어갔다가 첫 멤버에서 실패한다** — 아래 클래스는 주요 멤버를 채운 뒤에만 연다(설계 4.4).\n\n");
        b.append("| 플러그인 수 | 클래스 | shim 에 지금 있음 |\n|--:|---|---|\n");
        NmsSymbols idx = NmsSymbols.RUNTIME;
        targets.entrySet().stream().sorted((x, y) -> y.getValue().size() - x.getValue().size()).limit(60).forEach(e ->
                b.append("| ").append(e.getValue().size()).append(" | `").append(e.getKey()).append("` | ")
                        .append(idx.hasClass(e.getKey().replace('.', '/')) ? "예" : "아니오").append(" |\n"));
        if (targets.isEmpty()) b.append("| 0 | (없음) | |\n");
        b.append("\n전체 ").append(targets.size()).append("개.\n");
        return b.toString();
    }
}
