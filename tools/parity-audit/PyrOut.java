// 사막 신전 BB 바깥 영역의 순정(구조물 ON/OFF) 대비 Furnace 차이를 분석하는 도구
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class PyrOut {
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); int px = Integer.parseInt(a[1]), pz = Integer.parseInt(a[2]);
    int[] bb = {Integer.parseInt(a[5]), Integer.parseInt(a[6]), Integer.parseInt(a[7]), Integer.parseInt(a[8]), Integer.parseInt(a[9]), Integer.parseInt(a[10])};
    java.nio.file.Path on = java.nio.file.Path.of(a[3]), off = java.nio.file.Path.of(a[4]);
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
    long mmOn = 0, mmOff = 0, structOutside = 0, structOutsideFurnaceAdapted = 0, structOutsideFurnaceIsOff = 0;
    Map<String,Integer> notAdapted = new TreeMap<>();
    for (int cz = pz - 2; cz <= pz + 2; cz++) for (int cx = px - 2; cx <= px + 2; cx++) {
      short[] von = Harness.vanillaBlocks(Harness.readChunk(on, cx, cz));
      short[] voff = Harness.vanillaBlocks(Harness.readChunk(off, cx, cz));
      ChunkColumn col = src.generateWithFeatures(cx, cz);
      for (int y = -64; y < 320; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
        int wx = cx * 16 + x, wz = cz * 16 + z, p = (y + 64) * 256 + z * 16 + x;
        boolean inside = wx >= bb[0] && wx <= bb[3] && y >= bb[1] && y <= bb[4] && wz >= bb[2] && wz <= bb[5];
        if (inside) continue;
        String fk = reg.canonicalKeyOf(col.getBlock(x, y, z)); if (fk == null) fk = "unknown";
        String vk = Harness.keys.get(von[p]), vo = Harness.keys.get(voff[p]);
        if (!fk.equals(vk)) mmOn++;
        if (!fk.equals(vo)) mmOff++;
        if (!vk.equals(vo)) {
          structOutside++;
          if (fk.equals(vk)) structOutsideFurnaceAdapted++;
          else if (fk.equals(vo)) { structOutsideFurnaceIsOff++; notAdapted.merge(Harness.nameOfKey(vk) + " (vanilla ON; furnace=" + Harness.nameOfKey(vo) + ")", 1, Integer::sum); }
        }
      }
    }
    System.out.println("outside pyramid BB, 5x5 chunks: furnace!=vanillaON=" + mmOn + "  furnace!=vanillaOFF=" + mmOff);
    System.out.println("positions where vanilla ON!=OFF outside BB=" + structOutside + "  furnace==ON:" + structOutsideFurnaceAdapted + "  furnace==OFF(not adapted):" + structOutsideFurnaceIsOff);
    notAdapted.entrySet().stream().sorted((p,q)->q.getValue()-p.getValue()).limit(10).forEach(e -> System.out.println("   " + e.getValue() + "  " + e.getKey()));
  }
}
