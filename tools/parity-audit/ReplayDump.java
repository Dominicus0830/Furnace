// Replay(순정 순서 재생)를 돌리며 origin별 RNG/feature 추적을 덤프한다. segagg.py가 순정 VR 로그와 feature 단위로 대조한다.
// 실행: java -Dfurnace.rngTrace=true ReplayDump <seed> <ranks파일> <덤프디렉터리>
import java.nio.file.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.NoiseWorldSource;

public class ReplayDump {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
        new Replay(src, Path.of(a[1]), Path.of(a[2]));
    }
}
