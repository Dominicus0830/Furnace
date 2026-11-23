package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** `--mode body-table`: 바닐라 서버 jar(Mojang 이름)의 본문 지문표를 `<캐시>/<판>/body-table.tsv.gz` 에 만든다. 캐시에만 두고 커밋하지 않는다. */
final class BodyTableCommand {
    private BodyTableCommand() {}

    static boolean run(Options o) throws IOException {
        Path dir = o.cache.resolve(o.mc);
        Files.createDirectories(dir);
        Prepared p = new Preparer(o).prepare(false);
        BodyTable t = BodyTable.fromJar(p.serverJar(), List.of("net/minecraft/", "com/mojang/"), "vanilla-" + o.mc);
        Path out = dir.resolve("body-table.tsv.gz");
        t.write(out);
        System.out.println("[본문표] vanilla-" + o.mc + ": 메서드 " + t.methodCount() + "개 → " + out);
        return t.methodCount() > 0;
    }
}
