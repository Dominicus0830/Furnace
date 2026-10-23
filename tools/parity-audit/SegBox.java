// 중심 청크 하나를 생성하면서 그 3x3 origin 전부의 RNG/feature 추적을 한 번에 덤프한다.
// SegDump는 origin마다 따로 생성(= 다른 이웃 문맥)해서 순정의 "중심 청크 + 이웃 8 origin" 상태와 달라질 수 있다 —
// 이 도구는 실제 중심 청크 한 번의 생성 안에서 9개 origin을 모두 추적하므로 순정 격리 대조와 같은 문맥이다.
// 실행: java -Dfurnace.rngTrace=true -Dfurnace.decorateRanks=파일 SegBox <seed> <중심목록파일("cx cz" 줄)> <출력디렉터리>
// 출력: <outDir>/<origin cx>_<origin cz>.txt (BEGIN 헤더의 cx/cz로 origin별로 나눔)
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.carver.RandomTrace;

public class SegBox {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        Path outDir = Path.of(a[2]);
        Files.createDirectories(outDir);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
        PrintStream orig = System.out;
        for (String line : Files.readAllLines(Path.of(a[1]))) {
            String[] t = line.trim().split("\\s+");
            if (t.length != 2) continue;
            int cx = Integer.parseInt(t[0]), cz = Integer.parseInt(t[1]);
            RandomTrace.setTargetBox(cx - 1, cx + 1, cz - 1, cz + 1);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 24);
            try (PrintStream ps = new PrintStream(bos, false, "UTF-8")) {
                System.setOut(ps);
                src.generateWithFeatures(cx, cz);
                ps.flush();
            } finally {
                System.setOut(orig);
            }
            Map<String, PrintStream> outs = new HashMap<>();
            String cur = null;
            for (String l : bos.toString("UTF-8").split("\n")) {
                if (l.startsWith("FURNACE_RNG_BEGIN")) {
                    int i = l.indexOf(" cx="), j = l.indexOf(" cz="), k = l.indexOf(" step=");
                    cur = l.substring(i + 4, j) + "_" + l.substring(j + 4, k);
                }
                if (cur == null) continue;
                PrintStream o = outs.computeIfAbsent(cur, key -> {
                    try { return new PrintStream(new FileOutputStream(outDir.resolve(key + ".txt").toFile()), false, "UTF-8"); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                });
                o.println(l);
            }
            for (PrintStream o : outs.values()) o.close();
        }
    }
}
