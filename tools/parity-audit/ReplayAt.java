// Replay(순정 순서 재생) 결과와 순정 월드를 한 청크에서 대조해 다른 블록을 속성까지 전부 출력한다.
// 실행: java ReplayAt <seed> <ranks파일> <월드 world 디렉터리> <cx> <cz> [<cx> <cz> ...]
import java.nio.file.*;
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;

public class ReplayAt {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
        Replay replay = new Replay(src, Path.of(a[1]));
        for (int i = 3; i + 1 < a.length; i += 2) {
            int cx = Integer.parseInt(a[i]), cz = Integer.parseInt(a[i + 1]);
            if (!replay.valid(cx, cz)) { System.out.println("SKIP " + cx + "," + cz + " (3x3 중 순정이 decorate하지 않은 origin 있음)"); continue; }
            short[] b1 = Harness.vanillaBlocks(Harness.readChunk(Path.of(a[2]), cx, cz));
            ChunkColumn col = replay.column(cx, cz);
            int n = 0;
            for (int y = -64; y < 320; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int p = (y + 64) * 256 + z * 16 + x;
                String v = Harness.keys.get(b1[p]);
                String f = reg.canonicalKeyOf(col.getBlock(x, y, z));
                if (!v.equals(f)) { System.out.println("D " + (cx * 16 + x) + " " + y + " " + (cz * 16 + z) + " van=" + v + " fur=" + f); n++; }
            }
            System.out.println("CHUNK " + cx + "," + cz + " diffs=" + n);
        }
    }
}
