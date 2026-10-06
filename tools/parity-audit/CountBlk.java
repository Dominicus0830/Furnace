// 순정 월드 청크 범위에서 지정 블록 이름의 개수를 세는 도구
import java.util.*;
public class CountBlk {
  public static void main(String[] a) throws Exception {
    java.nio.file.Path w = java.nio.file.Path.of(a[0]);
    Set<String> want = new TreeSet<>(Arrays.asList(a).subList(1, a.length));
    Map<String,Long> cnt = new TreeMap<>();
    for (int cz = -6; cz <= 6; cz++) for (int cx = -6; cx <= 6; cx++) {
      short[] b = Harness.vanillaBlocks(Harness.readChunk(w, cx, cz));
      for (short s : b) { String n = Harness.nameOfKey(Harness.keys.get(s)); if (want.contains(n)) cnt.merge(n, 1L, Long::sum); }
    }
    System.out.println(w + " -> " + cnt);
  }
}
