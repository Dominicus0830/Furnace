// Furnace 가 사막 신전 시작점을 만드는 시드를 탐색하는 도구
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class DesertSeed {
  public static void main(String[] a) throws Exception {
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    int from = Integer.parseInt(a[0]), to = Integer.parseInt(a[1]);
    for (int seed = from; seed <= to; seed++) {
      NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
      for (int cz = -12; cz <= 12; cz++) for (int cx = -12; cx <= 12; cx++) {
        var cands = src.structurePlacementCandidates(cx, cz);
        if (cands.isEmpty()) continue;
        var starts = src.applyStructureStarts(cx, cz);
        if (!starts.isEmpty()) System.out.println("seed=" + seed + " chunk=(" + cx + "," + cz + ") sets=" + cands.stream().map(s -> s.setId()).toList() + " starts=" + starts.size() + " bb=" + starts.get(0).boundingBox());
      }
    }
  }
}
