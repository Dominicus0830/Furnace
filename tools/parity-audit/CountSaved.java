// Furnace 월드 폴더의 region 파일에 실제로 저장된 청크 수를 센다
import java.nio.file.*;
public class CountSaved {
  public static void main(String[] a) throws Exception {
    long total = 0;
    try (var s = Files.list(Path.of(a[0], "region"))) {
      for (Path p : (Iterable<Path>) s::iterator) {
        byte[] f = Files.readAllBytes(p); int n = 0;
        for (int i = 0; i < 1024; i++) if (f.length >= 4096 && (f[i*4] != 0 || f[i*4+1] != 0 || f[i*4+2] != 0)) n++;
        System.out.println(p.getFileName() + "  size=" + f.length + "  savedChunks=" + n); total += n;
      }
    }
    System.out.println("TOTAL saved chunks=" + total);
  }
}
