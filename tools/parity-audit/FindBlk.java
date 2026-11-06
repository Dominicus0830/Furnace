// 순정 월드 청크 범위에서 이름이 정규식에 맞는 블록 위치 출력 — 사용: FindBlk <world> <cx0> <cx1> <cz0> <cz1> <정규식>
public class FindBlk {
  public static void main(String[] a) throws Exception {
    java.nio.file.Path w = java.nio.file.Path.of(a[0]);
    int cx0 = Integer.parseInt(a[1]), cx1 = Integer.parseInt(a[2]), cz0 = Integer.parseInt(a[3]), cz1 = Integer.parseInt(a[4]);
    java.util.regex.Pattern p = java.util.regex.Pattern.compile(a[5]);
    for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
      short[] v = Harness.vanillaBlocks(Harness.readChunk(w, cx, cz));
      for (int i = 0; i < v.length; i++) {
        String k = Harness.keys.get(v[i]);
        if (p.matcher(k).find()) System.out.println((cx * 16 + (i & 15)) + " " + ((i >> 8) - 64) + " " + (cz * 16 + ((i >> 4) & 15)) + " " + k);
      }
    }
  }
}
