// 청크 목록의 각 청크를 Furnace로 생성하면서 그 청크 자신의 decoration(origin)에 대한 RNG/feature 추적을 파일로 덤프한다.
// 순정 쪽 VFEAT/VR 로그(rngseg.py)와 feature 단위로 대조하기 위한 입력이다.
// 실행: java -Dfurnace.rngTrace=true [-Dfurnace.decorateRanks=파일] SegDump <seed> <청크목록파일> <출력디렉터리>
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.carver.RandomTrace;

public class SegDump {
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
            RandomTrace.setTarget(cx, cz);
            try (PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(outDir.resolve(cx + "_" + cz + ".txt").toFile()), 1 << 20), false, "UTF-8")) {
                System.setOut(ps);
                src.generateWithFeatures(cx, cz);
                ps.flush();
            } finally {
                System.setOut(orig);
            }
        }
    }
}
