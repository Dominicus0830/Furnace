package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * `--mode fill-table` (T-262). 바닐라 서버 jar(Mojang 이름, 캐시의 work/server-mojmap.jar)에서 채움 표를 뽑아
 * {@code <캐시>/<판>/fill-table.tsv.gz} 에 쓴다. 캐시에만 두고 커밋하지 않는다. {@code --sig-jar <jar> --sig-name <이름>} 을 주면 그 jar(Paper 서버 jar)를 읽어 {@code fill-table-<이름>.tsv.gz} 에 쓴다.
 * 껍데기는 바닐라 표와 Paper 표에서 줄이 같은 것만 채운다.
 */
final class FillTableCommand {
    private static final List<String> PREFIXES = List.of("net/minecraft/", "com/mojang/");

    private FillTableCommand() {}

    static boolean run(Options o) throws IOException {
        Path dir = o.cache.resolve(o.mc);
        Files.createDirectories(dir);
        Path jar;
        if (o.sigJar != null) {
            jar = o.sigJar;
        } else {
            jar = new Preparer(o).prepare(false).serverJar();
        }
        List<String> prefixes = o.sigPrefix.isEmpty() ? PREFIXES : o.sigPrefix;
        FillTable t = FillExtractor.fromJar(jar, prefixes, (o.sigName == null ? "fill-" : "fill-" + o.sigName + "-") + o.mc);
        Path out = dir.resolve(o.sigName == null ? "fill-table.tsv.gz" : "fill-table-" + o.sigName + ".tsv.gz");
        t.write(out);
        System.out.println("[채움 표] fill-" + o.mc + ": 줄 " + t.size() + "개 → " + out);
        return t.size() > 0;
    }
}
