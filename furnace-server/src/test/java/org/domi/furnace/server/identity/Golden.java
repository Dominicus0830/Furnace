// 결과 동일 금 파일(golden): src/test/resources/identity/<이름>.txt 에 커밋한 기대 줄과 지금 결과를 줄 단위로 비교한다.
// '#' 로 시작하는 줄과 빈 줄은 설명이라 비교하지 않는다.
//
// 금 파일은 "옛 커밋 == 새 커밋" 의 기준이다. 최적화 PR 은 금 파일을 바꾸지 않는다.
// 갱신(bless)은 의도한 정확성 수정일 때만, 이름을 하나씩 적어서 한다:
//   ./gradlew :furnace-server:test --tests '<시험>' -Dfurnace.identity.bless=<이름>[,<이름>...]
// 그러면 src/test/resources/identity/<이름>.txt 를 지금 결과로 다시 쓰고(머리 '#' 줄은 유지) 시험은 통과한다.
// 갱신한 PR 의 절차(사유·검토)는 docs/testing/identitykit.md "금 파일 갱신".
// 불일치하면 지금 결과를 build/identity/actual/<이름>.txt 에 남긴다 — 금 파일과 diff 해서 어디가 바뀌었는지 본다.
package org.domi.furnace.server.identity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Golden {

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    static final String RESOURCE_DIR = "identity/";

    private Golden() {}

    /** 이름 규칙: 소문자·숫자·'.', '_', '-'. 파일 이름이 되므로 경로 문자를 막는다. */
    public static String checkName(String name) {
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("금 파일 이름은 [a-z0-9._-] 만: " + name);
        return name;
    }

    /** 비교 대상 줄(설명 줄 제외). 금 파일이 없으면 null. */
    public static List<String> expected(String name) {
        String res = RESOURCE_DIR + checkName(name) + ".txt";
        try (InputStream in = Golden.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) return null;
            return significant(Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> significant(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            String t = l.endsWith("\r") ? l.substring(0, l.length() - 1) : l;
            if (t.isBlank() || t.startsWith("#")) continue;
            out.add(t);
        }
        return out;
    }

    /** -Dfurnace.identity.bless 에 이 이름이 적혀 있는가. 와일드카드는 받지 않는다(한꺼번에 갱신 금지). */
    public static boolean blessRequested(String name) {
        return blessRequested(name, System.getProperty("furnace.identity.bless"));
    }

    static boolean blessRequested(String name, String v) {
        if (v == null || v.isBlank()) return false;
        Set<String> names = Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (names.contains("*") || names.contains("all") || names.contains("true")) {
            throw new IllegalArgumentException("furnace.identity.bless 는 금 파일 이름을 하나씩 적는다(전체 갱신 금지): " + v);
        }
        return names.contains(name);
    }

    /** 지금 결과가 금 파일과 같아야 한다. bless 요청이면 금 파일을 다시 쓴다. */
    public static void assertMatches(String name, List<String> actual) {
        checkName(name);
        for (String l : actual) {
            if (l.contains("\n") || l.contains("\r")) throw new IllegalArgumentException("줄 안에 줄바꿈: " + l);
            if (l.isBlank() || l.startsWith("#")) throw new IllegalArgumentException("빈 줄·'#' 줄은 비교에서 빠지므로 결과 줄로 쓸 수 없다: '" + l + "'");
        }
        if (actual.isEmpty()) throw new AssertionError("[" + name + "] 결과 줄이 하나도 없다 — 빈 결과를 금 파일과 같다고 볼 수 없다");
        if (blessRequested(name)) {
            Path file = bless(name, actual);
            System.out.printf("IDENTITY-BLESS %s lines=%d -> %s%n", name, actual.size(), file);
            return;
        }
        List<String> expected = expected(name);
        if (expected == null) {
            Path dump = dumpActual(name, actual);
            throw new AssertionError("[" + name + "] 금 파일 src/test/resources/" + RESOURCE_DIR + name + ".txt 이 없다. 지금 결과: " + dump
                    + "\n  처음 만들 때: -Dfurnace.identity.bless=" + name + " (docs/testing/identitykit.md)");
        }
        if (expected.equals(actual)) return;
        Path dump = dumpActual(name, actual);
        int diffs = 0, first = -1;
        int n = Math.max(expected.size(), actual.size());
        for (int i = 0; i < n; i++) {
            String e = i < expected.size() ? expected.get(i) : null, a = i < actual.size() ? actual.get(i) : null;
            if (e == null || !e.equals(a)) {
                diffs++;
                if (first < 0) first = i;
            }
        }
        String e = first < expected.size() ? expected.get(first) : "(없음)";
        String a = first < actual.size() ? actual.get(first) : "(없음)";
        throw new AssertionError("[" + name + "] 결과가 금 파일과 다르다 — " + diffs + "줄(기대 " + expected.size() + "줄, 지금 " + actual.size() + "줄)"
                + "\n  첫 차이 " + (first + 1) + "번째 결과 줄\n    금: " + e + "\n    지금: " + a
                + "\n  지금 결과 전체: " + dump
                + "\n  최적화 PR 이면 결과가 달라진 것이다(금 파일을 고치지 않는다). 의도한 정확성 수정일 때만 docs/testing/identitykit.md 절차로 갱신.");
    }

    /** src/test/resources/identity 를 찾는다(시험 JVM 작업 폴더는 모듈 폴더, IDE 는 저장소 루트일 수 있다). */
    static Path sourceDir() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path base : new Path[]{cwd, cwd.resolve("furnace-server")}) {
            Path p = base.resolve("src/test/resources");
            if (Files.isDirectory(p) && Files.isRegularFile(base.resolve("build.gradle.kts"))) return p.resolve("identity");
        }
        throw new IllegalStateException("src/test/resources 를 못 찾았다(user.dir=" + cwd + ")");
    }

    static Path bless(String name, List<String> actual) {
        try {
            Path dir = sourceDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".txt");
            List<String> out = new ArrayList<>();
            if (Files.isRegularFile(file)) {
                // 머리 설명 줄은 그대로 둔다(첫 결과 줄 전까지의 '#'·빈 줄)
                for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!l.isBlank() && !l.startsWith("#")) break;
                    out.add(l);
                }
            } else {
                out.add("# 결과 동일 금 파일 — " + name + ". 최적화 PR 은 이 파일을 바꾸지 않는다. 갱신 절차: docs/testing/identitykit.md");
            }
            out.addAll(actual);
            Files.write(file, (String.join("\n", out) + "\n").getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Path dumpActual(String name, List<String> actual) {
        try {
            Path dir = Path.of(System.getProperty("user.dir")).resolve("build/identity/actual");
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".txt");
            Files.write(file, (String.join("\n", actual) + "\n").getBytes(StandardCharsets.UTF_8));
            return file.toAbsolutePath();
        } catch (IOException e) {
            return Path.of("(쓰기 실패: " + e + ")");
        }
    }
}
