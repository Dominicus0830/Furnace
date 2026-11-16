package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 바닐라 데이터 추출 도구 진입점. 사용법은 README.md 와 build.gradle.kts 머리 주석.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        // 한국어 안내문이 로케일과 무관하게 그대로 나오게 한다.
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true, java.nio.charset.StandardCharsets.UTF_8));
        Options o;
        try {
            o = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        if (o.list) {
            for (Tool t : Tools.all().values()) {
                System.out.printf("%-26s %-6s %s%s%n", t.id(), t.kind() == Tool.Kind.JAVA ? "java" : "python", t.what(), t.byDefault() ? "" : "  (전체 실행에 안 들어감)");
            }
            return;
        }
        boolean ok;
        try {
            ok = run(o);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        System.exit(ok ? 0 : 1);
    }

    static boolean run(Options o) throws IOException {
        List<Tool> selected = select(o);
        Prepared p = new Preparer(o).prepare();
        if (o.protocol >= 0) p = new Prepared(p.mc(), p.serverJar(), p.innerJar(), p.mappings(), p.libraries(), p.generated(), o.protocol, p.remapped());
        Runner runner = new Runner(o, p);
        boolean legacyMode = o.mode == Options.Mode.BASELINE || o.mode == Options.Mode.PORT_CHECK || o.mode == Options.Mode.PROOF;
        Legacy legacy = null;
        Path legacyOut = o.out.resolveSibling(o.mc + "-legacy");
        if (legacyMode) {
            legacy = new Legacy(o, p);
            legacy.prepare();
        }
        boolean ok = true;
        if (o.mode == Options.Mode.BASELINE || o.mode == Options.Mode.PROOF) {
            Preparer.deleteTree(legacyOut);
            for (Tool t : selected) {
                if (!baselineable(t, o)) continue;
                System.out.println("== (이전 도구) " + t.id());
                try {
                    legacy.run(t, legacyOut, p.protocol());
                } catch (IOException e) {
                    ok = false;
                    System.out.println("이전 도구 실패  " + t.id() + ": " + e.getMessage());
                }
            }
            if (o.mode == Options.Mode.BASELINE) {
                System.out.println("이전 도구 결과: " + legacyOut);
                return ok;
            }
        }
        // 포팅 점검·증명에서는 전체 기본 선택이 아니어도 모든 Java 도구를 컴파일해 이름을 점검한다(실행은 선택한 것만).
        runner.compile(legacyMode ? javaTools() : selected);
        if (o.mode == Options.Mode.PORT_CHECK || o.mode == Options.Mode.PROOF) {
            ok &= portCheck(o, p, legacy, runner, selected);
            if (o.mode == Options.Mode.PORT_CHECK) return ok;
        }

        List<Runner.Result> results = new ArrayList<>();
        for (Tool t : selected) {
            System.out.println("== " + t.id());
            results.add(runner.run(t));
        }
        System.out.println();
        System.out.println("---- 도구별 결과 (" + o.mc + ", " + o.mode.name().toLowerCase(Locale.ROOT) + ") ----");
        for (Runner.Result r : results) {
            System.out.printf("%-12s %s%n", r.status().label(), r.tool().id());
        }
        for (Runner.Result r : results) {
            if (!r.ok()) {
                ok = false;
                System.out.println("실패(" + r.status().label() + ")  " + r.tool().id() + ": " + r.error());
            }
        }
        System.out.println();
        System.out.println("---- 비교 (" + o.mc + ", " + o.mode.name().toLowerCase(Locale.ROOT) + ") ----");
        for (Runner.Result r : results) {
            if (!r.ok()) continue;
            for (int i = 0; i < r.outputs().size(); i++) {
                Path staged = r.outputs().get(i);
                String rel = Tools.fill(r.tool().outputs().get(i).dest(), o.mc, p.protocol());
                Path repoFile = o.repo.resolve(rel);
                switch (o.mode) {
                    case VERIFY -> ok &= report("커밋", r.tool(), rel, Verifier.compare(staged, repoFile));
                    case PROOF -> {
                        if (baselineable(r.tool(), o)) {
                            // 옛 도구가 있던 것: 전↔후가 합격 판정이다. 옛 결과가 없으면(옛 도구 실패 등) 합격이 아니다.
                            Path legacyFile = legacyOut.resolve(rel);
                            if (Files.isRegularFile(legacyFile)) ok &= report("이전 도구", r.tool(), rel, Verifier.compare(staged, legacyFile));
                            else {
                                ok = false;
                                System.out.println("실패  " + r.tool().id() + "  " + rel + "  (이전 도구 결과 없음 — 전↔후를 견줄 수 없다)");
                            }
                            // 커밋된 파일과의 차이는 합격 판정에 넣지 않고 알려 준다(커밋된 것이 이전 도구의 옛 판 결과일 수 있다).
                            report("커밋(참고)", r.tool(), rel, Verifier.compare(staged, repoFile));
                        } else {
                            // 옛 Java 도구가 없던 것(데이터 생성기 JSON 을 읽는 파이썬 도구들): 같은 도구가 만든 커밋 파일이 정답이라 합격 판정에 넣는다.
                            ok &= report("커밋(합격)", r.tool(), rel, Verifier.compare(staged, repoFile));
                        }
                    }
                    case INSTALL -> {
                        Files.createDirectories(repoFile.getParent());
                        Files.copy(staged, repoFile, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("설치  " + r.tool().id() + "  " + rel);
                    }
                    default -> System.out.println("생성  " + r.tool().id() + "  " + staged);
                }
            }
            if (r.outputs().isEmpty() && !r.tool().id().equals("shape-vectors")) System.out.println("완료  " + r.tool().id());
        }
        if (o.mode == Options.Mode.PROOF) ok &= shapeVectorsProof(o, results, legacyOut);
        return ok;
    }

    private static boolean report(String against, Tool t, String rel, Verifier.Outcome v) {
        String tag = switch (v.kind()) {
            case IDENTICAL, IDENTICAL_UNZIPPED -> "같음";
            case EQUIVALENT -> "동등";
            default -> "다름";
        };
        System.out.println(tag + "  [" + against + "] " + t.id() + "  " + rel + "  " + v.detail());
        return v.ok() || against.endsWith("(참고)");
    }

    /**
     * 이전에 있던 도구만 전후 비교에 넣는다(Java 도구 중 재료가 필요 없는 것 + 파이썬 둘이던 것).
     * shape-vectors 는 고정 시드 인자(--tool-arg)를 받았을 때만 넣는다.
     */
    static boolean baselineable(Tool t, Options o) {
        if (t.id().equals("block-sound-types") || t.id().equals("protocol-ids")) return true;
        if (t.id().equals("shape-vectors")) return !o.toolArgs.getOrDefault("shape-vectors", List.of()).isEmpty();
        return t.kind() == Tool.Kind.JAVA && t.byDefault();
    }

    /** 모든 Java 도구(기본 선택 여부와 무관). 포팅 점검·증명 모드에서 컴파일 대상이다. */
    static List<Tool> javaTools() {
        List<Tool> res = new ArrayList<>();
        for (Tool t : Tools.all().values()) if (t.kind() == Tool.Kind.JAVA) res.add(t);
        return res;
    }

    /** shape-vectors 의 전↔후: 같은 고정 시드로 옛 도구와 새 도구가 쓴 벡터 파일을 견준다. 선택하지 않았으면 아무것도 하지 않는다. */
    private static boolean shapeVectorsProof(Options o, List<Runner.Result> results, Path legacyOut) throws IOException {
        for (Runner.Result r : results) {
            if (!r.tool().id().equals("shape-vectors") || !r.ok()) continue;
            String name = "scratch/shape-vectors-" + o.mc + ".txt";
            Path fresh = o.out.resolve(name);
            Path old = legacyOut.resolve(name);
            if (!Files.isRegularFile(old)) {
                System.out.println("실패  shape-vectors  " + name + "  (이전 도구 결과 없음 — 전↔후를 견줄 수 없다)");
                return false;
            }
            return report("이전 도구", r.tool(), name, Verifier.compare(fresh, old));
        }
        return true;
    }

    private static boolean portCheck(Options o, Prepared p, Legacy legacy, Runner runner, List<Tool> selected) throws IOException {
        if (p.mappings() == null) {
            System.out.println("[포팅 점검] 이 판에는 공식 대응표가 없어 건너뜀");
            return true;
        }
        JarRemapper remapper = Preparer.remapperFor(p.mappings(), p.innerJar());
        List<PortCheck.ClassReport> reports = PortCheck.compare(legacy.classesDir(), runner.classesDir(), remapper);
        System.out.println();
        System.out.println("---- 포팅 점검 (이전 도구 바이트코드를 Mojang 이름으로 푼 것 ↔ 새 도구) ----");
        int same = 0, differ = 0;
        for (PortCheck.ClassReport r : reports) {
            if (r.same()) {
                same++;
                System.out.println("같음  " + r.className() + "  (메서드 " + r.methodsCompared() + "개)");
            } else {
                differ++;
                System.out.println("다름  " + r.className() + (compileFailed(runner, r.className()) ? "  (이 도구는 컴파일 실패 — 위 오류부터 고친다)" : ""));
                for (String d : r.differences()) System.out.println("    " + d);
            }
        }
        System.out.println("클래스 " + reports.size() + "개 중 같음 " + same + ", 다름 " + differ
                + " (다른 곳은 이름 추측이 틀렸거나 일부러 바꾼 곳 — 보고 하나씩 판단한다. Vanilla.boot 도우미로 바꾼 부트스트랩 호출은 알려진 차이)");
        return true;
    }

    private static boolean compileFailed(Runner runner, String className) {
        String base = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
        for (String id : runner.compileFailures().keySet()) {
            Tool t = Tools.all().get(id);
            if (t != null && t.entry().equals("furnacetools." + base)) return true;
        }
        return false;
    }

    static List<Tool> select(Options o) {
        List<Tool> res = new ArrayList<>();
        if (o.tools.isEmpty()) {
            for (Tool t : Tools.all().values()) if (t.byDefault()) res.add(t);
        } else {
            for (String id : o.tools) {
                Tool t = Tools.all().get(id.strip());
                if (t == null) throw new IllegalArgumentException("없는 도구: " + id + " (--list 로 목록)");
                res.add(t);
            }
        }
        if (res.stream().anyMatch(t -> t.id().equals("shape-vectors")) && o.toolArgs.getOrDefault("shape-vectors", List.of()).size() < 4) {
            throw new IllegalArgumentException("shape-vectors 는 --tool-arg shape-vectors=<값> 네 개가 필요하다: 블록들, 이웃 블록들, 상태당 시도 수, 시드 (출력 파일은 실행기가 정한다)");
        }
        return res;
    }
}
