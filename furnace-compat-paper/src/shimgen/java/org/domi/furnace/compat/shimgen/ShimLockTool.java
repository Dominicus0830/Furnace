// T-229: 잠금 파일 만들기·검사. 서명표 캐시(tools/compat/sig-tables.sh — Paper 서버·바닐라·라이브러리 셋)가 있어야 한다. 없으면 건너뛰지 않고 실패한다.
//   쓰기:  ./gradlew -PskipOracle=true :furnace-compat-paper:shimLock
//   검사:  ./gradlew -PskipOracle=true :furnace-compat-paper:shimLockCheck   (CI vanilla-gates — 잠금 파일이 서명표·명세와 같아야 한다)
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.tools.extract.SigTable;

public final class ShimLockTool {
    private ShimLockTool() {}

    /** args: write|check, 판, 서명표 캐시 폴더(&lt;캐시&gt;/&lt;판&gt;), 명세 폴더, 잠금 파일, shim 소스 폴더들(:로 여럿). */
    public static void main(String[] args) throws IOException {
        String mode = args[0];
        String mc = args[1];
        Path cache = Path.of(args[2]);
        Path specDir = Path.of(args[3]);
        Path lockFile = Path.of(args[4]);
        List<Path> roots = new ArrayList<>();
        for (String r : args[5].split(java.io.File.pathSeparator)) if (!r.isBlank()) roots.add(Path.of(r));
        List<SigTable> tables = new ArrayList<>();
        for (String f : List.of("sig-table-paper-server.tsv.gz", "sig-table.tsv.gz", "sig-table-libs.tsv.gz")) {
            Path p = cache.resolve(f);
            if (!Files.isRegularFile(p)) {
                System.err.println("[shimLock] missing signature table: " + p + " (tools/compat/sig-tables.sh " + mc + ") - failing instead of skipping");
                System.exit(1);
            }
            tables.add(SigTable.read(p));
        }
        ShimSpec spec = ShimSpec.readDir(specDir);
        ShimSources src = new ShimSources(roots);
        ShimLock lock;
        try {
            lock = ShimLock.resolve(mc, spec, tables, src::has);
        } catch (ShimLock.ResolveException e) {
            System.err.println("[shimLock] " + e.getMessage());
            System.exit(1);
            return;
        }
        String text = lock.text();
        if (mode.equals("write")) {
            lock.write(lockFile);
            System.out.println("[shimLock] " + lock.classes.size() + " classes, " + lock.members.values().stream().mapToInt(List::size).sum() + " members -> " + lockFile);
            return;
        }
        String now = Files.exists(lockFile) ? Files.readString(lockFile, StandardCharsets.UTF_8) : "";
        if (ShimLock.same(text, now)) {
            System.out.println("[shimLock] the shim lock matches the signature tables and the spec (" + lock.classes.size() + " classes)");
            return;
        }
        System.err.println("[shimLock] stale shim lock - rewrite it with ./gradlew -PskipOracle=true :furnace-compat-paper:shimLock. Differing lines (+ expected, - present):");
        for (String l : ShimLock.diff(text, now)) System.err.println("  " + l);
        System.exit(1);
    }
}
