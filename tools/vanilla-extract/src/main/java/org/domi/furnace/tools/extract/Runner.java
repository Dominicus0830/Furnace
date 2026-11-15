package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** 도구를 실제로 돌린다: Java 도구는 도구마다 컴파일(javac)해 도구마다 새 JVM 으로, 파이썬 도구는 python 으로. */
public final class Runner {
    /** 도구 한 개의 결과 상태. */
    public enum Status {
        OK("성공"), COMPILE_FAILED("컴파일 실패"), RUN_FAILED("실행 실패");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 도구 한 개의 실행 결과. */
    public record Result(Tool tool, Status status, List<Path> outputs, String error) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    private static final int ERROR_LINES_SHOWN = 40;

    private final Options o;
    private final Prepared p;
    private Path classes;
    private String python;
    /** 컴파일에 실패한 Java 도구(id → 오류 요약). */
    private final java.util.Map<String, String> compileFailures = new java.util.LinkedHashMap<>();

    /** 도구 소스 이름(확장자 뺀 것)으로 소스 스트림을 준다. 기본은 이 모듈의 resources/exporters/furnacetools. 시험이 바꿔 끼운다. */
    private final java.util.function.Function<String, InputStream> sources;

    public Runner(Options o, Prepared p) {
        this(o, p, name -> Runner.class.getResourceAsStream("/exporters/furnacetools/" + name + ".java"));
    }

    Runner(Options o, Prepared p, java.util.function.Function<String, InputStream> sources) {
        this.o = o;
        this.p = p;
        this.sources = sources;
    }

    /** 컴파일에 실패한 도구(id → 오류 요약). 점검·실행 결과에 도구별로 적는다. */
    public java.util.Map<String, String> compileFailures() {
        return compileFailures;
    }

    /**
     * 주어진 Java 도구들의 소스를 도구마다 따로 컴파일한다(소스는 이 모듈의 resources/exporters).
     * 한 도구가 깨져도 다른 도구는 그대로 컴파일·실행되고, 실패한 도구는 compileFailures() 에 도구별로 남는다.
     * 공용 도우미(Vanilla)가 깨지면 모든 도구가 같은 이유로 실패한다. 전체 오류는 캐시 폴더의 exporter-compile-<id>.log 에 쓰고 화면에는 앞부분만 보인다.
     */
    public void compile(List<Tool> tools) throws IOException {
        compileFailures.clear();
        Path src = o.cache.resolve(o.mc).resolve("exporter-src");
        Path logs = o.cache.resolve(o.mc);
        classes = o.cache.resolve(o.mc).resolve("exporter-classes");
        Preparer.deleteTree(src);
        Preparer.deleteTree(classes);
        Files.createDirectories(src);
        Files.createDirectories(classes);
        String vanillaError = compileOne("Vanilla", src, logs);
        for (Tool t : tools) {
            if (t.kind() != Tool.Kind.JAVA) continue;
            String simple = t.entry().substring("furnacetools.".length());
            String err = vanillaError != null ? "공용 도우미 Vanilla 컴파일 실패 — " + vanillaError : compileOne(simple, src, logs);
            if (err != null) {
                compileFailures.put(t.id(), err);
                System.out.println("컴파일 실패  " + t.id() + ": " + err);
            }
        }
    }

    /** 소스 한 개를 컴파일하고, 실패하면 오류 요약(앞부분 + 로그 위치)을 돌려준다. 성공하면 null. */
    private String compileOne(String simple, Path src, Path logs) throws IOException {
        Path f = src.resolve(simple + ".java");
        if (!Files.exists(f)) {
            try (InputStream in = sources.apply(simple)) {
                if (in == null) throw new IOException("도구 소스가 없다: exporters/furnacetools/" + simple + ".java");
                Files.copy(in, f);
            }
        }
        List<String> cmd = new ArrayList<>(List.of(Procs.exe(o.javaHome, "javac"), "-proc:none", "-nowarn", "-encoding", "UTF-8", "-Xmaxerrs", "1000",
                "-cp", classpath(true), "-d", classes.toString(), f.toString()));
        Procs.Captured c = Procs.capture(cmd, null);
        if (c.code() == 0) return null;
        Path log = logs.resolve("exporter-compile-" + simple + ".log");
        Files.writeString(log, c.output(), java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = c.output().split("\\R");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, ERROR_LINES_SHOWN); i++) sb.append("\n    ").append(lines[i]);
        if (lines.length > ERROR_LINES_SHOWN) sb.append("\n    … (").append(lines.length - ERROR_LINES_SHOWN).append("줄 더)");
        return "오류 전체: " + log + sb;
    }

    /** compile() 이 만든 클래스 폴더. */
    public Path classesDir() {
        return classes;
    }

    private String classpath(boolean withClasses) {
        List<String> cp = new ArrayList<>();
        cp.add(p.serverJar().toString());
        for (Path l : p.libraries()) cp.add(l.toString());
        if (withClasses) cp.add(classes.toString());
        return String.join(java.io.File.pathSeparator, cp);
    }

    public Result run(Tool t) {
        List<Path> outs = new ArrayList<>();
        String compileError = compileFailures.get(t.id());
        if (compileError != null) return new Result(t, Status.COMPILE_FAILED, outs, compileError);
        try {
            for (Tool.Output out : t.outputs()) {
                Path staged = o.out.resolve(Tools.fill(out.dest(), o.mc, p.protocol()));
                Files.createDirectories(staged.getParent());
                outs.add(staged);
            }
            // gzipAfter: 도구는 `.gz` 를 뺀 이름으로 쓰고 실행기가 묶는다.
            List<Path> toolPaths = new ArrayList<>();
            for (int i = 0; i < outs.size(); i++) {
                Path pth = outs.get(i);
                toolPaths.add(t.outputs().get(i).gzipAfter() ? stripGz(pth) : pth);
            }
            // 결과 목록이 없는 도구(shape-vectors)는 출력 파일을 {scratch} 아래에 두므로 그 폴더를 여기서 만든다.
            if (t.args().stream().anyMatch(a -> a.contains("{scratch}"))) Files.createDirectories(o.out.resolve("scratch"));
            List<String> args = args(t, toolPaths);
            if (t.kind() == Tool.Kind.JAVA) {
                List<String> cmd = new ArrayList<>(List.of(Procs.exe(o.javaHome, "java"), "-Dfurnace.repo=" + o.repo, "-Dfurnace.mc=" + o.mc,
                        "-cp", classpath(true), t.entry()));
                cmd.addAll(args);
                Procs.run(cmd, o.repo);
            } else {
                Path script = o.cache.resolve(o.mc).resolve("python").resolve(t.entry());
                Files.createDirectories(script.getParent());
                try (InputStream in = Runner.class.getResourceAsStream("/python/" + t.entry())) {
                    if (in == null) throw new IOException("파이썬 도구가 없다: " + t.entry());
                    Files.copy(in, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                List<String> cmd = new ArrayList<>(List.of(python(), "-I", script.toString()));
                cmd.addAll(args);
                Procs.run(cmd, o.repo);
            }
            for (int i = 0; i < outs.size(); i++) {
                if (!t.outputs().get(i).gzipAfter()) continue;
                gzip(toolPaths.get(i), outs.get(i));
                Files.delete(toolPaths.get(i));
            }
            return new Result(t, Status.OK, outs, null);
        } catch (IOException | RuntimeException e) {
            return new Result(t, Status.RUN_FAILED, outs, e.getMessage());
        }
    }

    private List<String> args(Tool t, List<Path> toolPaths) {
        List<String> template = t.args();
        List<String> res = new ArrayList<>();
        if (template.isEmpty()) {
            for (Path x : toolPaths) res.add(x.toString());
            return res;
        }
        for (String a : template) {
            if (a.equals("{tool-args}")) {
                res.addAll(o.toolArgs.getOrDefault(t.id(), List.of()));
                continue;
            }
            String v = a.replace("{serverJar}", p.serverJar().toString())
                    .replace("{generated}", p.generated().toString())
                    .replace("{repo}", o.repo.toString().replace('\\', '/'))
                    .replace("{scratch}", o.out.resolve("scratch").toString().replace('\\', '/'))
                    .replace("{mc}", o.mc)
                    .replace("{protocol}", Integer.toString(p.protocol()));
            for (int i = 0; i < toolPaths.size(); i++) v = v.replace("{out" + i + "}", toolPaths.get(i).toString());
            res.add(Tools.fill(v, o.mc, p.protocol()));
        }
        // 추가 인자는 도구 인자 끝에 붙는다(재료를 받는 도구가 아니어도 --tool-arg 로 덧붙일 수 있다).
        if (!t.id().equals("shape-vectors")) res.addAll(o.toolArgs.getOrDefault(t.id(), List.of()));
        return res;
    }

    private String python() throws IOException {
        if (python != null) return python;
        for (String c : o.python != null ? List.of(o.python) : List.of("python3", "python", "py")) {
            try {
                Process pr = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                pr.getInputStream().readAllBytes();
                if (pr.waitFor() == 0) return python = c;
            } catch (IOException | InterruptedException ignored) {
                // 다음 후보
            }
        }
        throw new IOException("python 을 찾지 못했다(--python <명령> 으로 준다)");
    }

    static Path stripGz(Path p) {
        String n = p.getFileName().toString();
        if (!n.endsWith(".gz")) throw new IllegalArgumentException("gzipAfter 인데 .gz 이름이 아니다: " + n);
        return p.resolveSibling(n.substring(0, n.length() - 3));
    }

    /** 헤더에 시각·파일 이름을 넣지 않는 gzip — 같은 내용이면 같은 파일이 나온다. */
    static void gzip(Path plain, Path out) throws IOException {
        try (InputStream in = Files.newInputStream(plain); OutputStream os = Files.newOutputStream(out);
             GZIPOutputStream gz = new GZIPOutputStream(os) {
                 { def.setLevel(java.util.zip.Deflater.BEST_COMPRESSION); }
             }) {
            in.transferTo(gz);
        }
    }
}
