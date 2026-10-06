// 순정 level.dat 의 스폰 좌표와 Furnace defaultSpawn() 을 대조하는 도구
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class SpawnCheck {
  @SuppressWarnings("unchecked")
  public static void main(String[] a) throws Exception {
    // a: seed worldDir ...  (pairs)
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    for (int i = 0; i + 1 < a.length; i += 2) {
      long seed = Long.parseLong(a[i]);
      DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(a[i+1] + "/level.dat"))));
      int t = in.readByte(); in.readUTF();
      Map<String,Object> root = (Map<String,Object>) Harness.readPayload(in, t);
      Map<String,Object> d = (Map<String,Object>) root.get("Data");
      int vx = (Integer) d.get("SpawnX"), vy = (Integer) d.get("SpawnY"), vz = (Integer) d.get("SpawnZ");
      Object vs = d.get("RandomSeed"); 
      NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
      SpawnPoint s = src.defaultSpawn();
      System.out.printf("seed=%d vanilla spawn=(%d,%d,%d)  furnace=(%d,%d,%d)  %s%n", seed, vx, vy, vz, s.x(), s.y(), s.z(),
        (vx==s.x() && vy==s.y() && vz==s.z()) ? "MATCH" : "DIFF");
    }
  }
}
