package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

/**
 * 판 하나를 도구가 돌 수 있는 상태로 만든다(캐시 폴더 `<cache>/<판>/`):
 * version.json → 번들러 jar·공식 대응표 내려받기(sha1 확인) → 번들러 풀기 → 대응표로 이름 풀기 → 바닐라 데이터 생성기 실행.
 * 난독화가 없는 판(version.json 에 대응표가 없다)은 이름 풀기를 건너뛴다.
 * 이미 만든 단계는 입력 sha1 이 같으면 다시 하지 않는다.
 */
public final class Preparer {
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    /** 캐시 형식이 바뀌면 올려서 옛 캐시를 버린다. */
    static final String FORMAT = "1";

    private final Options o;
    private final Downloader dl = new Downloader();

    public Preparer(Options o) {
        this.o = o;
    }

    public Prepared prepare() throws IOException {
        return prepare(true);
    }

    /** 바닐라 데이터 생성기 실행이 필요 없는 도구(서명표 등)는 generate=false 로 jar 준비까지만 한다. */
    public Prepared prepare(boolean generate) throws IOException {
        Path dir = o.cache.resolve(o.mc);
        Files.createDirectories(dir);
        Path bundler;
        Optional<Path> mappingsFile;
        int protocol = -1;
        if (o.serverJar != null) {
            // 오프라인: 파일을 직접 준다. 프로토콜 번호는 알 수 없다.
            bundler = o.serverJar;
            mappingsFile = Optional.ofNullable(o.mappings);
        } else {
            Path vjson = dir.resolve("version.json");
            if (!Files.isRegularFile(vjson)) {
                String url = VersionInfo.manifestUrl(dl.text(MANIFEST), o.mc)
                        .orElseThrow(() -> new IOException("version_manifest 에 없는 판: " + o.mc));
                Files.writeString(vjson, dl.text(url), StandardCharsets.UTF_8);
            }
            VersionInfo vi = VersionInfo.parse(Files.readString(vjson, StandardCharsets.UTF_8));
            protocol = vi.protocol();
            if (vi.javaMajor() > Runtime.version().feature() && o.javaHome.equals(Path.of(System.getProperty("java.home")))) {
                System.out.println("[알림] 이 판은 Java " + vi.javaMajor() + " 이상이 필요하다 — --java-home 으로 그 JDK 를 준다(현재 " + Runtime.version().feature() + ").");
            }
            bundler = dl.file(vi.serverUrl(), vi.serverSha1(), dir.resolve("server.jar"));
            mappingsFile = vi.mappings().isPresent()
                    ? Optional.of(dl.file(vi.mappings().get().url(), vi.mappings().get().sha1(), dir.resolve("server_mappings.txt")))
                    : Optional.empty();
        }
        String stamp = FORMAT + "|" + Downloader.sha1(bundler) + "|" + (mappingsFile.isPresent() ? Downloader.sha1(mappingsFile.get()) : "none");
        Path stampFile = dir.resolve("prepared.stamp");
        Path work = dir.resolve("work");
        boolean fresh = Files.isRegularFile(stampFile) && Files.readString(stampFile).equals(stamp);

        ServerBundle.Unpacked un;
        Path serverJar;
        if (fresh && Files.isRegularFile(work.resolve("server-inner.jar"))) {
            un = new ServerBundle.Unpacked(work.resolve("server-inner.jar"), listLibs(work.resolve("libs")));
            serverJar = mappingsFile.isPresent() ? work.resolve("server-mojmap.jar") : un.serverJar();
        } else {
            Files.deleteIfExists(stampFile);
            deleteTree(work);
            un = ServerBundle.unpack(bundler, work);
            if (mappingsFile.isPresent()) {
                MojangMappings maps;
                try (Reader r = Files.newBufferedReader(mappingsFile.get(), StandardCharsets.UTF_8)) {
                    maps = MojangMappings.parse(r);
                }
                ClassHierarchy h;
                try (ZipFile zf = new ZipFile(un.serverJar().toFile())) {
                    h = ClassHierarchy.read(zf);
                }
                serverJar = work.resolve("server-mojmap.jar");
                int n = new JarRemapper(maps, h).remapJar(un.serverJar(), serverJar);
                System.out.println("[준비] " + o.mc + ": 클래스 " + n + "개 Mojang 이름으로 변환(대응표 클래스 " + maps.classCount() + "개)");
            } else {
                serverJar = un.serverJar();
                System.out.println("[준비] " + o.mc + ": 공식 대응표 없음 — 난독화 없는 jar 로 보고 이름 풀기 건너뜀");
            }
            Files.writeString(stampFile, stamp);
        }

        Path generated = dir.resolve("generated");
        Path genStamp = generated.resolve(".stamp");
        if (generate && !(fresh && Files.isRegularFile(genStamp) && Files.readString(genStamp).equals(stamp))) {
            deleteTree(generated);
            Files.createDirectories(generated);
            List<String> cp = new ArrayList<>();
            cp.add(serverJar.toString());
            for (Path l : un.libraries()) cp.add(l.toString());
            System.out.println("[준비] 바닐라 데이터 생성기 실행 → " + generated);
            Procs.run(List.of(Procs.exe(o.javaHome, "java"), "-cp", String.join(java.io.File.pathSeparator, cp),
                    "net.minecraft.data.Main", "--all", "--output", generated.toString()), dir);
            Files.writeString(genStamp, stamp);
        }
        return new Prepared(o.mc, serverJar, un.serverJar(), mappingsFile.orElse(null), un.libraries(), generated, protocol, mappingsFile.isPresent());
    }

    /** 대응표 파일과 난독화 원본 jar 로 이름 변환기를 만든다(포팅 점검이 이전 도구 클래스를 풀 때 쓴다). */
    static JarRemapper remapperFor(Path mappingsFile, Path innerJar) throws IOException {
        MojangMappings maps;
        try (Reader r = Files.newBufferedReader(mappingsFile, StandardCharsets.UTF_8)) {
            maps = MojangMappings.parse(r);
        }
        try (ZipFile zf = new ZipFile(innerJar.toFile())) {
            return new JarRemapper(maps, ClassHierarchy.read(zf));
        }
    }

    private static List<Path> listLibs(Path libs) throws IOException {
        if (!Files.isDirectory(libs)) return List.of();
        try (var s = Files.walk(libs)) {
            return s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        }
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            for (Path q : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(q);
        }
    }
}
