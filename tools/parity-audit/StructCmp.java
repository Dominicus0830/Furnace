// 구조물 start 청크 주변을 순정(구조물 ON) 월드 대 Furnace 로 대조 — 사용: StructCmp <seed> <structureId> <cx> <cz> <vanillaWorldDir> [OFF월드폴더]
// Furnace 의 start rawBox 안만 비교한다. OFF 월드를 주면 "구조물이 바꾼 칸(ON!=OFF)" 중 Furnace 일치율도 센다.
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
@SuppressWarnings("unchecked")
public class StructCmp {
  static final int YMIN = Integer.parseInt(System.getenv().getOrDefault("YMIN", "-64")), YMAX = Integer.parseInt(System.getenv().getOrDefault("YMAX", "320"));
  static final String FILTER = System.getenv("STRUCT_FILTER");
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); String id = a[1]; int sx = Integer.parseInt(a[2]), sz = Integer.parseInt(a[3]);
    String dim = System.getenv("DIM");
    String sub = "nether".equals(dim) ? "DIM-1" : "end".equals(dim) ? "DIM1" : null;
    java.nio.file.Path on = sub == null ? java.nio.file.Path.of(a[4]) : java.nio.file.Path.of(a[4]).resolve(sub);
    java.nio.file.Path off = a.length > 5 ? (sub == null ? java.nio.file.Path.of(a[5]) : java.nio.file.Path.of(a[5]).resolve(sub)) : null;
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    NoiseWorldSource src = "nether".equals(dim) ? NoiseWorldSource.nether(seed, reg, r) : "end".equals(dim) ? NoiseWorldSource.end(seed, reg, r) : NoiseWorldSource.overworld(seed, reg, r);
    var starts = src.applyStructureStarts(sx, sz);
    org.domi.furnace.server.world.gen.structure.StructureStart st = null;
    for (var s : starts) if (s.structureId().equals(id)) st = s;
    if (st == null) { System.out.println("Furnace: 해당 start 없음 (starts=" + starts.size() + ")"); return; }
    try {
      var rootC = Harness.readChunk(on, sx, sz);
      var vs = (Map<String,Object>) ((Map<String,Object>) rootC.get("structures")).get("starts");
      for (var e : vs.entrySet()) {
        var m = (Map<String,Object>) e.getValue();
        System.out.println("vanilla start " + e.getKey() + (m.containsKey("Children") ? "" : " (INVALID)"));
        if (m.containsKey("Children")) for (Object co : (List<Object>) m.get("Children")) {
          var cm = (Map<String,Object>) co;
          Object pe = cm.get("pool_element");
          String loc = pe instanceof Map ? String.valueOf(((Map<String,Object>) pe).get("location")) : null;
          System.out.println("   vanilla piece " + cm.get("id") + " tpl=" + (loc != null ? loc + " P=" + cm.get("PosX") + "," + cm.get("PosY") + "," + cm.get("PosZ") + " rot=" + cm.get("rotation") : cm.get("Template")) + " rot=" + cm.get("Rot") + " BB=" + Arrays.toString((int[]) cm.get("BB")) + " TP=" + cm.get("TPX") + "," + cm.get("TPY") + "," + cm.get("TPZ"));
        }
      }
    } catch (Exception ex) { System.out.println("vanilla start info failed: " + ex); }
    for (var pc : st.pieces()) if (pc instanceof org.domi.furnace.server.world.gen.structure.JigsawPiece jp) System.out.println("   furnace " + jp + " BB=" + jp.boundingBox().minX() + "," + jp.boundingBox().minY() + "," + jp.boundingBox().minZ() + " " + jp.boundingBox().maxX() + "," + jp.boundingBox().maxY() + "," + jp.boundingBox().maxZ());
    for (var pc : st.pieces()) if (pc instanceof org.domi.furnace.server.world.gen.structure.TemplatePiece tp) System.out.println("   furnace piece tpl=" + tp.templateName() + " rot=" + tp.settings().rotation + " pos=" + tp.tx() + "," + tp.ty() + "," + tp.tz());
    var b = st.rawBox();
    System.out.println("furnace start pieces=" + st.pieces().size() + " box=[" + b.minX() + "," + b.minY() + "," + b.minZ() + " .. " + b.maxX() + "," + b.maxY() + "," + b.maxZ() + "]");
    final int M = Integer.parseInt(System.getenv().getOrDefault("XZMARGIN", "0"));
    int cx0 = Math.floorDiv(b.minX() - M, 16), cx1 = Math.floorDiv(b.maxX() + M, 16), cz0 = Math.floorDiv(b.minZ() - M, 16), cz1 = Math.floorDiv(b.maxZ() + M, 16);
    long inBox = 0, diff = 0, changed = 0, changedRight = 0;
    Map<String,Integer> pairs = new TreeMap<>();
    List<String> firsts = new ArrayList<>();
    for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
      short[] von = Harness.vanillaBlocks(Harness.readChunk(on, cx, cz));
      short[] voff = off == null ? null : Harness.vanillaBlocks(Harness.readChunk(off, cx, cz));
      ChunkColumn col = src.generateWithFeatures(cx, cz);
      for (int y = Math.max(YMIN, col.minY()); y < Math.min(YMAX, col.maxY() + 1); y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
        int wx = cx * 16 + x, wz = cz * 16 + z;
        if (wx < b.minX() - M || wx > b.maxX() + M || wz < b.minZ() - M || wz > b.maxZ() + M) continue;
        int p = (y + 64) * 256 + z * 16 + x;
        String fk = reg.canonicalKeyOf(col.getBlock(x, y, z)); if (fk == null) fk = "unknown"; if (fk.equals("minecraft:cave_air")) fk = "minecraft:air";
        String vk = Harness.keys.get(von[p]); if (vk.equals("minecraft:cave_air") || vk.equals("minecraft:void_air")) vk = "minecraft:air";
        inBox++;
        if (voff != null && !Harness.keys.get(voff[p]).equals(vk)) { changed++; if (fk.equals(vk)) changedRight++; }
        if (!fk.equals(vk)) {
          if (FILTER != null && !java.util.regex.Pattern.compile(FILTER).matcher(vk + " " + fk).find()) continue;
          diff++;
          pairs.merge(vk + "  ->  " + fk, 1, Integer::sum);
          if (firsts.size() < 400 && (FILTER == null || java.util.regex.Pattern.compile(FILTER).matcher(vk + " " + fk).find())) firsts.add(wx + " " + y + " " + wz + "  van=" + vk + "  fur=" + fk);
        }
      }
    }
    System.out.println("box blocks=" + inBox + "  furnace!=vanilla=" + diff + (off != null ? ("   structure-changed positions=" + changed + " furnace right=" + changedRight) : ""));
    pairs.entrySet().stream().sorted((p,q)->q.getValue()-p.getValue()).limit(25).forEach(e -> System.out.println("   " + e.getValue() + "  " + e.getKey()));
    System.out.println("--- first diffs");
    firsts.forEach(System.out::println);
  }
}
