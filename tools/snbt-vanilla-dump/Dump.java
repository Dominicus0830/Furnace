import com.mojang.brigadier.StringReader;
import java.nio.file.*;
public class Dump {
  public static void main(String[] a) throws Exception {
    for (String line : Files.readAllLines(Path.of(a[0]))) {
      if (line.isEmpty()) continue;
      String s = line;
      String out;
      try {
        StringReader r = new StringReader(s);
        uy t = new uz(r).d();
        out = "OK\t" + t + "\t" + t.getClass().getSimpleName() + "\trest=" + r.getCursor();
      } catch (Exception e) { out = "ERR\t" + e.getMessage(); }
      System.out.println(line + "\t" + out);
    }
  }
}
