// 순정 heightmap NBT 와 Furnace heightmap 의 컬럼별 차이 원인을 진단하는 도구
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class HmDbg {
  @SuppressWarnings("unchecked")
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]);
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
    var root = Harness.readChunk(java.nio.file.Path.of(a[3]), cx, cz);
    var hm = (Map<String,Object>) root.get("Heightmaps");
    int[] vmb = Harness.unpackHeights((long[]) hm.get("MOTION_BLOCKING"));
    int[] vws = Harness.unpackHeights((long[]) hm.get("WORLD_SURFACE"));
    ChunkColumn col = src.generateWithFeatures(cx, cz);
    int[] fmb = Harness.unpackHeights(col.motionBlockingHeightmap());
    int[] fws = Harness.unpackHeights(col.worldSurfaceHeightmap());
    short[] vb = Harness.vanillaBlocks(root);
    Map<String,Integer> deltaMB = new TreeMap<>(), deltaWS = new TreeMap<>(); Map<String,Integer> topAtMB = new TreeMap<>();
    int shown = 0;
    for (int i = 0; i < 256; i++) {
      int x = i & 15, z = i >> 4;
      deltaMB.merge("vanilla-furnace=" + (vmb[i]-fmb[i]), 1, Integer::sum);
      deltaWS.merge("vanilla-furnace=" + (vws[i]-fws[i]), 1, Integer::sum);
      if (vmb[i] != fmb[i]) {
        // 순정의 MOTION_BLOCKING 직전(최상단 blocksMotion) 블록 vs Furnace 쪽
        int yv = vmb[i] - 1 - 64, yf = fmb[i] - 1 - 64;
        String vk = Harness.keys.get(vb[(yv + 64) * 256 + z * 16 + x]);
        String fk = reg.canonicalKeyOf(col.getBlock(x, yf, z));
        topAtMB.merge(Harness.nameOfKey(vk) + " (vanilla top) | furnace top=" + Harness.nameOfKey(fk == null ? "?" : fk), 1, Integer::sum);
      }
    }
    System.out.println("MB delta: " + deltaMB); System.out.println("WS delta: " + deltaWS);
    topAtMB.entrySet().stream().sorted((p,q)->q.getValue()-p.getValue()).limit(10).forEach(e -> System.out.println("  " + e.getValue() + "  " + e.getKey()));
  }
}
