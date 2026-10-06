// 사막 신전 영역을 순정(구조물 ON/OFF)과 Furnace 로 비교해 신전 BB 안 일치율을 재는 도구
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
// 사막 신전 영역: 순정(구조물 ON) vs 순정(구조물 OFF) vs Furnace — 신전 BB 안에서만 비교
public class PyrCmp {
  @SuppressWarnings("unchecked")
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); int px = Integer.parseInt(a[1]), pz = Integer.parseInt(a[2]);
    java.nio.file.Path on = java.nio.file.Path.of(a[3]), off = java.nio.file.Path.of(a[4]);
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
    // 순정 구조물 BB 수집
    int[] bb = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    var rootC = Harness.readChunk(on, px, pz);
    var starts = (Map<String,Object>) ((Map<String,Object>) rootC.get("structures")).get("starts");
    var st = (Map<String,Object>) starts.get("minecraft:desert_pyramid");
    for (Object co : (List<Object>) st.get("Children")) {
      int[] b = (int[]) ((Map<String,Object>) co).get("BB");
      for (int i = 0; i < 3; i++) { bb[i] = Math.min(bb[i], b[i]); bb[i+3] = Math.max(bb[i+3], b[i+3]); }
      System.out.println("vanilla piece: " + ((Map<String,Object>) co).get("id") + " BB=" + Arrays.toString(b));
    }
    System.out.println("vanilla union BB=" + Arrays.toString(bb));
    var fs = src.applyStructureStarts(px, pz);
    System.out.println("furnace start count=" + fs.size() + (fs.isEmpty() ? "" : " bb=" + bbStr(fs.get(0).boundingBox())));
    long inBox = 0, vsOffDiff = 0, furVsOnMM = 0, furVsOnMMnoStruct = 0, structPos = 0, structPosFurnaceRight = 0;
    Map<String,Integer> mmPairs = new TreeMap<>();
    for (int cz = pz - 2; cz <= pz + 2; cz++) for (int cx = px - 2; cx <= px + 2; cx++) {
      short[] von = Harness.vanillaBlocks(Harness.readChunk(on, cx, cz));
      short[] voff = Harness.vanillaBlocks(Harness.readChunk(off, cx, cz));
      ChunkColumn col = src.generateWithFeatures(cx, cz);
      for (int y = -64; y < 320; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
        int wx = cx * 16 + x, wz = cz * 16 + z;
        int p = (y + 64) * 256 + z * 16 + x;
        String fk = reg.canonicalKeyOf(col.getBlock(x, y, z)); if (fk == null) fk = "unknown";
        String vk = Harness.keys.get(von[p]), vo = Harness.keys.get(voff[p]);
        boolean inside = wx >= bb[0] && wx <= bb[3] && y >= bb[1] && y <= bb[4] && wz >= bb[2] && wz <= bb[5];
        if (!vk.equals(vo)) { structPos++; if (fk.equals(vk)) structPosFurnaceRight++; }   // 구조물이 바꾼 위치
        if (inside) { inBox++; if (!fk.equals(vk)) { furVsOnMM++; mmPairs.merge(Harness.nameOfKey(vk) + " -> " + Harness.nameOfKey(fk), 1, Integer::sum); } }
      }
    }
    System.out.println("blocks inside vanilla pyramid BB: " + inBox + "  furnace!=vanilla(structure ON) inside BB: " + furVsOnMM);
    System.out.println("positions changed by structure (vanilla ON vs OFF, 5x5 chunks): " + structPos + "  of which furnace==vanilla(ON): " + structPosFurnaceRight);
    mmPairs.entrySet().stream().sorted((p,q)->q.getValue()-p.getValue()).limit(12).forEach(e -> System.out.println("   " + e.getValue() + "  " + e.getKey()));
  }
  static String bbStr(Object b) { return String.valueOf(b); }
}
