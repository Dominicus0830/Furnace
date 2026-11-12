// 레거시 오라클(옛 구현 사본) 수명 규칙 검사 — LegacyOracle 머리 주석의 1·3·4번(docs/testing/identitykit.md "옛 구현 보관").
// 시험 소스를 읽어 '// LEGACY-ORACLE' 머리나 @LegacyOracle 이 있는 파일을 모두 찾는다(위치는 identity/legacy/** 또는 운영 패키지).
package org.domi.furnace.server.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LegacyOracleRulesTest {

    private static final Pattern HEADER = Pattern.compile("^// LEGACY-ORACLE since ([0-9a-f]{7,40}): \\S.*");

    static Path testSourceRoot() {
        Path resources = Golden.sourceDir().getParent();   // src/test/resources
        return resources.getParent().resolve("java");
    }

    private static Map<Path, String> sources(Path root) throws IOException {
        Map<Path, String> out = new LinkedHashMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".java")).sorted()::iterator) {
                out.put(p, Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @Test
    void legacyOraclesFollowLifetimeRules() throws Exception {
        Path root = testSourceRoot();
        Map<Path, String> src = sources(root);
        assertTrue(src.keySet().stream().anyMatch(p -> p.endsWith("identity/LegacyOracleRulesTest.java")), "시험 소스 위치를 잘못 찾았다: " + root);
        Path self = root.resolve("org/domi/furnace/server/identity/LegacyOracle.java");
        List<String> problems = new ArrayList<>();
        int found = 0;
        for (var e : src.entrySet()) {
            Path file = e.getKey();
            String text = e.getValue();
            if (file.equals(self) || file.endsWith("identity/LegacyOracleRulesTest.java")) continue;
            boolean header = text.startsWith("// LEGACY-ORACLE");
            boolean annotated = text.contains("@LegacyOracle(");
            if (!header && !annotated) continue;
            found++;
            String rel = root.relativize(file).toString().replace('\\', '/');
            String className = rel.substring(0, rel.length() - ".java".length()).replace('/', '.');
            String simple = className.substring(className.lastIndexOf('.') + 1);
            String firstLine = text.lines().findFirst().orElse("");
            Matcher m = HEADER.matcher(firstLine);
            if (!m.matches()) {
                problems.add(rel + ": 첫 줄이 '// LEGACY-ORACLE since <커밋>: <무엇의 사본>' 꼴이 아니다");
                continue;
            }
            LegacyOracle ann;
            try {
                ann = Class.forName(className).getAnnotation(LegacyOracle.class);
            } catch (ClassNotFoundException ex) {
                problems.add(rel + ": 클래스 " + className + " 를 못 읽었다");
                continue;
            }
            if (ann == null) {
                problems.add(rel + ": 바깥 클래스에 @LegacyOracle 이 없다");
                continue;
            }
            if (!ann.since().equals(m.group(1))) problems.add(rel + ": 머리 since " + m.group(1) + " != @LegacyOracle since " + ann.since());
            if (ann.copyOf().isBlank()) problems.add(rel + ": copyOf 가 비었다");
            // 3. 쓰는 차분 시험이 있어야 한다
            Pattern use = Pattern.compile("\\b" + Pattern.quote(simple) + "\\b");
            boolean used = src.entrySet().stream().anyMatch(o -> !o.getKey().equals(file) && use.matcher(o.getValue()).find());
            if (!used) problems.add(rel + ": 아무 시험도 쓰지 않는다 — 지운다");
            // 4. 금 파일이 덮으면 지운다
            if (!ann.coveredBy().isBlank() && Golden.expected(ann.coveredBy()) != null) {
                problems.add(rel + ": 장면 금 파일 identity/" + ann.coveredBy() + ".txt 가 생겼다 — 이 오라클을 지운다");
            }
        }
        assertTrue(problems.isEmpty(), "레거시 오라클 규칙 위반(" + found + "개 중):\n  " + String.join("\n  ", problems));
    }
}
