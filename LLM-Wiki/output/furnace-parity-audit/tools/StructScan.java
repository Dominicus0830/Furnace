// 순정 .mca 의 structures.starts 를 스캔해 구조물 종류별 시작점 개수를 세는 도구
import java.nio.file.*;
import java.util.*;
public class StructScan {
  @SuppressWarnings("unchecked")
  public static void main(String[] a) throws Exception {
    int cx0=Integer.parseInt(a[0]),cx1=Integer.parseInt(a[1]),cz0=Integer.parseInt(a[2]),cz1=Integer.parseInt(a[3]);
    Path w = Path.of(a[4]);
    Map<String, Set<String>> byType = new TreeMap<>();
    int chunks=0, nonFull=0, withRef=0;
    for (int cz=cz0; cz<=cz1; cz++) for (int cx=cx0; cx<=cx1; cx++) {
      var r = Harness.readChunk(w, cx, cz); if (r == null) continue; chunks++;
      if (!"minecraft:full".equals(r.get("Status"))) nonFull++;
      var s = (Map<String,Object>) r.get("structures"); if (s == null) continue;
      var starts = (Map<String,Object>) s.get("starts");
      if (starts != null) for (var e : starts.entrySet()) {
        var st = (Map<String,Object>) e.getValue();
        if ("INVALID".equals(st.get("id"))) continue;
        byType.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).add(st.get("ChunkX") + "," + st.get("ChunkZ"));
      }
    }
    System.out.println("chunks=" + chunks + " nonFull=" + nonFull);
    int total=0; for (var e : byType.entrySet()) { total += e.getValue().size(); System.out.println("  " + e.getKey() + "  x" + e.getValue().size() + "  " + e.getValue()); }
    System.out.println("TOTAL distinct structure starts=" + total + " types=" + byType.size());
  }
}
