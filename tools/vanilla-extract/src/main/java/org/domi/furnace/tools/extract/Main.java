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
                if (!baselineable(t)) continue;
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
        runner.compile(selected);
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
        System.out.println("---- 결과 (" + o.mc + ", " + o.mode.name().toLowerCase(Locale.ROOT) + ") ----");
        for (Runner.Result r : results) {
            if (!r.ok()) {
                ok = false;
                System.out.println("실패  " + r.tool().id() + ": " + r.error());
                continue;
            }
            for (int i = 0; i < r.outputs().size(); i++) {
                Path staged = r.outputs().get(i);
                String rel = Tools.fill(r.tool().outputs().get(i).dest(), o.mc, p.protocol());
                Path repoFile = o.repo.resolve(rel);
                switch (o.mode) {
                    case VERIFY -> ok &= report("커밋", r.tool(), rel, Verifier.compare(staged, repoFile));
                    case PROOF -> {
                        Path legacyFile = legacyOut.resolve(rel);
                        if (Files.isRegularFile(legacyFile)) ok &= report("이전 도구", r.tool(), rel, Verifier.compare(staged, legacyFile));
                        else System.out.println("건너뜀  " + r.tool().id() + "  " + rel + "  (이전 도구 결과 없음)");
                        // 커밋된 파일과의 차이는 합격 판정에 넣지 않고 알려 준다(커밋된 것이 이전 도구의 옛 판 결과일 수 있다).
                        report("커밋(참고)", r.tool(), rel, Verifier.compare(staged, repoFile));
                    }
                    case INSTALL -> {
                        Files.createDirectories(repoFile.getParent());
                        Files.copy(staged, repoFile, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("설치  " + r.tool().id() + "  " + rel);
                    }
                    default -> System.out.println("생성  " + r.tool().id() + "  " + staged);
                }
            }
            if (r.outputs().isEmpty()) System.out.println("완료  " + r.tool().id());
        }
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

    /** 이전에 있던 도구만 전후 비교에 넣는다(Java 도구 중 재료가 필요 없는 것 + 파이썬 둘이던 것). */
    static boolean baselineable(Tool t) {
        if (t.id().equals("block-sound-types") || t.id().equals("protocol-ids")) return true;
        return t.kind() == Tool.Kind.JAVA && t.byDefault();
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
                System.out.println("다름  " + r.className());
                for (String d : r.differences()) System.out.println("    " + d);
            }
        }
        System.out.println("클래스 " + reports.size() + "개 중 같음 " + same + ", 다름 " + differ
                + " (다른 곳은 이름 추측이 틀렸거나 일부러 바꾼 곳 — 보고 하나씩 판단한다. Vanilla.boot 도우미로 바꾼 부트스트랩 호출은 알려진 차이)");
        return true;
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
        return res;
    }
}
