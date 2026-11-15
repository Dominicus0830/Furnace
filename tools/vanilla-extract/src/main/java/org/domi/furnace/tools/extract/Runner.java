package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** 도구를 실제로 돌린다: Java 도구는 한 번에 컴파일(javac)해 도구마다 새 JVM 으로, 파이썬 도구는 python 으로. */
public final class Runner {
    /** 도구 한 개의 실행 결과. */
    public record Result(Tool tool, boolean ok, List<Path> outputs, String error) {}

    private final Options o;
    private final Prepared p;
    private Path classes;
    private String python;

    public Runner(Options o, Prepared p) {
        this.o = o;
        this.p = p;
    }

    /** 선택된 도구들의 Java 소스를 한 번에 컴파일한다(소스는 이 모듈의 resources/exporters). */
    public void compile(List<Tool> tools) throws IOException {
        List<String> names = new ArrayList<>(List.of("Vanilla"));
        for (Tool t : tools) if (t.kind() == Tool.Kind.JAVA) names.add(t.entry().substring("furnacetools.".length()));
        Path src = o.cache.resolve(o.mc).resolve("exporter-src");
        classes = o.cache.resolve(o.mc).resolve("exporter-classes");
        Preparer.deleteTree(src);
        Preparer.deleteTree(classes);
        Files.createDirectories(src);
        Files.createDirectories(classes);
        List<String> files = new ArrayList<>();
        for (String n : names) {
            Path f = src.resolve(n + ".java");
            try (InputStream in = Runner.class.getResourceAsStream("/exporters/furnacetools/" + n + ".java")) {
                if (in == null) throw new IOException("도구 소스가 없다: exporters/furnacetools/" + n + ".java");
                Files.copy(in, f);
            }
            files.add(f.toString());
        }
        List<String> cmd = new ArrayList<>(List.of(Procs.exe(o.javaHome, "javac"), "-proc:none", "-nowarn", "-encoding", "UTF-8",
                "-cp", classpath(false), "-d", classes.toString()));
        cmd.addAll(files);
        Procs.run(cmd, null);
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
            return new Result(t, true, outs, null);
        } catch (IOException | RuntimeException e) {
            return new Result(t, false, outs, e.getMessage());
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
