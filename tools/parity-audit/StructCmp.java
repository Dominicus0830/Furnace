// 구조물 start 청크 주변을 순정(구조물 ON) 월드 대 Furnace 로 대조 — 사용: StructCmp <seed> <structureId> <cx> <cz> <vanillaWorldDir> [OFF월드폴더]
// Furnace 의 start rawBox 안만 비교한다. OFF 월드를 주면 "구조물이 바꾼 칸(ON!=OFF)" 중 Furnace 일치율도 센다.
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class StructCmp {
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); String id = a[1]; int sx = Integer.parseInt(a[2]), sz = Integer.parseInt(a[3]);
    java.nio.file.Path on = java.nio.file.Path.of(a[4]);
    java.nio.file.Path off = a.length > 5 ? java.nio.file.Path.of(a[5]) : null;
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
    var starts = src.applyStructureStarts(sx, sz);
    org.domi.furnace.server.world.gen.structure.StructureStart st = null;
    for (var s : starts) if (s.structureId().equals(id)) st = s;
    if (st == null) { System.out.println("Furnace: 해당 start 없음 (starts=" + starts.size() + ")"); return; }
    var b = st.rawBox();
    System.out.println("furnace start pieces=" + st.pieces().size() + " box=[" + b.minX() + "," + b.minY() + "," + b.minZ() + " .. " + b.maxX() + "," + b.maxY() + "," + b.maxZ() + "]");
    int cx0 = Math.floorDiv(b.minX(), 16), cx1 = Math.floorDiv(b.maxX(), 16), cz0 = Math.floorDiv(b.minZ(), 16), cz1 = Math.floorDiv(b.maxZ(), 16);
    long inBox = 0, diff = 0, changed = 0, changedRight = 0;
    Map<String,Integer> pairs = new TreeMap<>();
    List<String> firsts = new ArrayList<>();
    for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
      short[] von = Harness.vanillaBlocks(Harness.readChunk(on, cx, cz));
      short[] voff = off == null ? null : Harness.vanillaBlocks(Harness.readChunk(off, cx, cz));
      ChunkColumn col = src.generateWithFeatures(cx, cz);
      for (int y = -64; y < 320; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
        int wx = cx * 16 + x, wz = cz * 16 + z;
        if (wx < b.minX() || wx > b.maxX() || wz < b.minZ() || wz > b.maxZ()) continue;
        int p = (y + 64) * 256 + z * 16 + x;
        String fk = reg.canonicalKeyOf(col.getBlock(x, y, z)); if (fk == null) fk = "unknown";
        String vk = Harness.keys.get(von[p]);
        inBox++;
        if (voff != null && !Harness.keys.get(voff[p]).equals(vk)) { changed++; if (fk.equals(vk)) changedRight++; }
        if (!fk.equals(vk)) {
          diff++;
          pairs.merge(vk + "  ->  " + fk, 1, Integer::sum);
          if (firsts.size() < 60) firsts.add(wx + " " + y + " " + wz + "  van=" + vk + "  fur=" + fk);
        }
      }
    }
    System.out.println("box blocks=" + inBox + "  furnace!=vanilla=" + diff + (off != null ? ("   structure-changed positions=" + changed + " furnace right=" + changedRight) : ""));
    pairs.entrySet().stream().sorted((p,q)->q.getValue()-p.getValue()).limit(25).forEach(e -> System.out.println("   " + e.getValue() + "  " + e.getKey()));
    System.out.println("--- first diffs");
    firsts.forEach(System.out::println);
  }
}
