package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * `--mode sig-table` (T-227). 서명표를 `<캐시>/<판>/` 에 만든다. 출력은 캐시에만 두고 커밋하지 않는다.
 *
 * <ul>
 *   <li>기본: 바닐라 서버 jar(Mojang 이름) → {@code sig-table.tsv.gz} ({@code net/minecraft/}·{@code com/mojang/} 만)</li>
 *   <li>{@code --sig-paper-api}: repo.papermc.io 의 paper-api jar → {@code sig-table-paper-api.tsv.gz}</li>
 *   <li>{@code --sig-jar <jar> --sig-name <이름> [--sig-prefix a/,b/]}: 임의 jar(Mojang 이름 Paper 서버 jar,
 *       컴파일된 shim 폴더를 묶은 jar 등) → {@code sig-table-<이름>.tsv.gz}</li>
 * </ul>
 */
final class SigTableCommand {
    static final String PAPER_REPO = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/";
    private static final List<String> VANILLA_PREFIXES = List.of("net/minecraft/", "com/mojang/");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private SigTableCommand() {}

    static boolean run(Options o) throws IOException {
        Path dir = o.cache.resolve(o.mc);
        Files.createDirectories(dir);
        Path jar;
        List<String> prefixes = o.sigPrefix;
        String name;
        if (o.sigJar != null) {
            if (o.sigName == null) throw new IllegalArgumentException("--sig-jar 는 --sig-name <이름> 과 함께 쓴다");
            jar = o.sigJar;
            name = o.sigName;
        } else if (o.sigPaperApi) {
            jar = downloadPaperApi(o, dir);
            name = "paper-api";
        } else {
            jar = new Preparer(o).prepare(false).serverJar();
            if (prefixes.isEmpty()) prefixes = VANILLA_PREFIXES;
            name = null;
        }
        if (name != null && !NAME.matcher(name).matches()) throw new IllegalArgumentException("--sig-name 은 영문·숫자·._- 만: " + name);
        String file = name == null ? "sig-table.tsv.gz" : "sig-table-" + name + ".tsv.gz";
        String label = (name == null ? "vanilla" : name) + "-" + o.mc;
        SigTable t = SigTable.fromJar(jar, prefixes, label);
        Path out = dir.resolve(file);
        t.write(out);
        System.out.println("[서명표] " + label + ": 클래스 " + t.classes().size() + "개, 멤버 " + t.memberCount() + "개 → " + out);
        return !t.classes().isEmpty();
    }

    private static Path downloadPaperApi(Options o, Path dir) throws IOException {
        Downloader dl = new Downloader();
        String base = PAPER_REPO + o.mc + "-R0.1-SNAPSHOT/";
        String value = snapshotJarValue(dl.text(base + "maven-metadata.xml"))
                .orElseThrow(() -> new IOException("paper-api " + o.mc + " 스냅샷 jar 를 maven-metadata 에서 못 찾음"));
        String url = base + "paper-api-" + value + ".jar";
        String sha1 = dl.text(url + ".sha1").strip();
        return dl.file(url, sha1.split("\\s+")[0], dir.resolve("paper-api-" + value + ".jar"));
    }

    /** maven-metadata.xml 에서 분류자(classifier) 없는 jar 의 스냅샷 버전 값. */
    static Optional<String> snapshotJarValue(String xml) {
        Matcher m = Pattern.compile("<snapshotVersion>(.*?)</snapshotVersion>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            String b = m.group(1);
            if (b.contains("<classifier>") || !b.contains("<extension>jar</extension>")) continue;
            Matcher v = Pattern.compile("<value>([^<]+)</value>").matcher(b);
            if (v.find()) return Optional.of(v.group(1).strip());
        }
        return Optional.empty();
    }
}
