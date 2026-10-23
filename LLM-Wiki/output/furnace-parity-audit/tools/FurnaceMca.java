// Furnace 가 저장한 .mca 청크와 순정 청크의 NBT 키 구성을 비교하는 도구
import java.util.*;
public class FurnaceMca {
  @SuppressWarnings("unchecked")
  public static void main(String[] a) throws Exception {
    java.nio.file.Path w = java.nio.file.Path.of(a[0]);
    int found = 0;
    for (int cz = -32; cz < 32 && found < 3; cz++) for (int cx = -32; cx < 32 && found < 3; cx++) {
      var r = Harness.readChunk(w, cx, cz); if (r == null) continue; found++;
      var secs = (List<Object>) r.get("sections");
      var s0 = (Map<String,Object>) secs.get(8);
      System.out.println("furnace chunk (" + cx + "," + cz + ") root keys=" + new TreeSet<>(r.keySet()) + "  Status=" + r.get("Status"));
      System.out.println("   section[Y=" + s0.get("Y") + "] keys=" + s0.keySet() + "  Heightmaps keys=" + ((Map<String,Object>) r.get("Heightmaps")).keySet());
    }
    System.out.println("chunks found=" + found);
    // 비교: 순정 청크의 키
    var v = Harness.readChunk(java.nio.file.Path.of(a[1]), 0, 0);
    var vs = (Map<String,Object>) ((List<Object>) v.get("sections")).get(8);
    System.out.println("vanilla root keys=" + new TreeSet<>(v.keySet()));
    System.out.println("   vanilla section keys=" + vs.keySet() + "  Heightmaps keys=" + ((Map<String,Object>) v.get("Heightmaps")).keySet());
  }
}
