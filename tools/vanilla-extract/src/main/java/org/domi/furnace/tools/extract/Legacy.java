package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 이 모듈로 옮기기 전의 도구(난독화 이름으로 쓴 tools/vanilla-data/Export*.java 와 파이썬 둘)를 git 기록에서 꺼내
 * 같은 바닐라 jar(난독화 원본)에 돌려, 새 도구와 견줄 "전" 결과를 만든다. 새 도구가 옛 도구와 같은 결과를 낸다는 증명(V-05 수용 조건)에 쓴다.
 */
public final class Legacy {
    private final Options o;
    private final Prepared p;
    private final Path dir;
    private Path classes;
    private Path unsignedJar;
    private Path classDir;

    /** 이전 도구의 소스 경로(저장소 기준). */
    static final List<String> SOURCE_ROOTS = List.of("tools/vanilla-data", "tools/compat/protocol-ids-extract.py");

    public Legacy(Options o, Prepared p) {
        this.o = o;
        this.p = p;
        this.dir = o.cache.resolve(o.mc).resolve("legacy");
    }

    public Path sourceDir() {
        return dir.resolve("src");
    }

    public Path classesDir() {
        return classes;
    }

    public Path unsignedJar() {
        return unsignedJar;
    }

    /** git 기록에서 소스를 꺼내고 컴파일한다. */
    public void prepare() throws IOException {
        if (o.legacyRef == null) throw new IllegalArgumentException("--legacy-ref <이전 도구가 있던 커밋> 이 필요하다(README 의 증명 절차 참고)");
        Preparer.deleteTree(dir);
        Files.createDirectories(sourceDir());
        String listing = git("ls-tree", "-r", "--name-only", o.legacyRef, "--", SOURCE_ROOTS.get(0), SOURCE_ROOTS.get(1));
        List<String> javaFiles = new ArrayList<>();
        for (String path : listing.split("\n")) {
            path = path.strip();
            if (path.isEmpty() || path.contains("/server-")) continue;   // tools/vanilla-data/server-<판>/ 은 풀어 둔 jar 데이터
            if (!path.endsWith(".java") && !path.endsWith(".py")) continue;
            Path target = sourceDir().resolve(path.substring(path.lastIndexOf('/') + 1));
            Files.writeString(target, git("show", o.legacyRef + ":" + path), StandardCharsets.UTF_8);
            if (path.endsWith(".java")) javaFiles.add(target.toString());
        }
        if (javaFiles.isEmpty()) throw new IOException(o.legacyRef + " 에 tools/vanilla-data/*.java 가 없다 — 이전 도구가 있는 커밋을 준다");
        unsignedJar = dir.resolve("server-inner-unsigned.jar");
        JarRemapper.stripSignatures(p.innerJar(), unsignedJar);
        classDir = dir.resolve("server-classes");
        try (ZipFile zf = new ZipFile(unsignedJar.toFile())) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                Path t = classDir.resolve(e.getName());
                Files.createDirectories(t.getParent());
                try (var in = zf.getInputStream(e)) {
                    Files.copy(in, t);
                }
            }
        }
        classes = dir.resolve("classes");
        Files.createDirectories(classes);
        List<String> cmd = new ArrayList<>(List.of(Procs.exe(o.javaHome, "javac"), "-proc:none", "-nowarn", "-encoding", "UTF-8", "-cp", classpath(false), "-d", classes.toString()));
        cmd.addAll(javaFiles);
        Procs.run(cmd, null);
    }

    private String classpath(boolean withClasses) {
        List<String> cp = new ArrayList<>();
        if (withClasses) cp.add(classes.toString());
        cp.add(unsignedJar.toString());
        for (Path l : p.libraries()) cp.add(l.toString());
        return String.join(java.io.File.pathSeparator, cp);
    }

    /** 이전 도구 한 개를 돌려 `<legacyOut>/<저장소 경로>` 에 쓴다. 이전에 없던 도구(새로 생긴 것)는 건너뛰고 null. */
    public List<Path> run(Tool t, Path legacyOut, int protocol) throws IOException {
        List<Path> outs = new ArrayList<>();
        List<Path> toolPaths = new ArrayList<>();
        for (Tool.Output out : t.outputs()) {
            Path staged = legacyOut.resolve(Tools.fill(out.dest(), o.mc, protocol));
            Files.createDirectories(staged.getParent());
            outs.add(staged);
            toolPaths.add(out.gzipAfter() ? Runner.stripGz(staged) : staged);
        }
        switch (t.id()) {
            case "block-sound-types" -> Procs.run(List.of(python(), "-I", sourceDir().resolve("extract_block_sound_types.py").toString(), classDir.toString(), toolPaths.get(0).toString()), null, pathWithJavap());
            case "protocol-ids" -> Procs.runToFile(List.of(python(), "-I", sourceDir().resolve("protocol-ids-extract.py").toString(), classDir.toString()), toolPaths.get(0), pathWithJavap());
            default -> {
                String simple = t.entry().substring("furnacetools.".length());
                // 이전 도구는 한 파일뿐이라 기본 패키지(ExportContextCollisionShapes 만 furnacetools 패키지) — 어느 쪽인지는 컴파일 결과로 안다.
                String main = Files.isRegularFile(classes.resolve(simple + ".class")) ? simple : "furnacetools." + simple;
                if (t.id().equals("palette-oracle")) {
                    Procs.run(List.of(Procs.exe(o.javaHome, "java"), "-cp", classpath(true), main, toolPaths.get(0).toString(), unsignedJar.toString()), null);
                } else if (t.args().isEmpty()) {
                    List<String> cmd = new ArrayList<>(List.of(Procs.exe(o.javaHome, "java"), "-cp", classpath(true), main));
                    for (Path x : toolPaths) cmd.add(x.toString());
                    Procs.run(cmd, null);
                } else {
                    return List.of();
                }
            }
        }
        for (int i = 0; i < outs.size(); i++) {
            if (!t.outputs().get(i).gzipAfter()) continue;
            Runner.gzip(toolPaths.get(i), outs.get(i));
            Files.delete(toolPaths.get(i));
        }
        return outs;
    }

    private String python() {
        return o.python != null ? o.python : (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "python" : "python3");
    }

    private java.util.Map<String, String> pathWithJavap() {
        String sep = java.io.File.pathSeparator;
        return java.util.Map.of("PATH", o.javaHome.resolve("bin") + sep + System.getenv().getOrDefault("PATH", ""));
    }

    private String git(String... args) throws IOException {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", o.repo.toString()));
        cmd.addAll(List.of(args));
        try {
            Process pr = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            byte[] out = pr.getInputStream().readAllBytes();
            if (pr.waitFor() != 0) throw new IOException("git 실패: " + String.join(" ", args));
            return new String(out, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("중단", e);
        }
    }
}
