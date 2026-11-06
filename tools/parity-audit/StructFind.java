// Furnace 가 특정 구조물 시작점을 만드는 (시드, 청크) 탐색 — 사용: StructFind <구조물id> <seedFrom> <seedTo> <반경> [최대개수/시드]
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class StructFind {
  public static void main(String[] a) throws Exception {
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    String id = a[0];
    int from = Integer.parseInt(a[1]), to = Integer.parseInt(a[2]), rad = Integer.parseInt(a[3]);
    int maxPer = a.length > 4 ? Integer.parseInt(a[4]) : 3;
    for (int seed = from; seed <= to; seed++) {
      NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
      int n = 0;
      for (int cz = -rad; cz <= rad && n < maxPer; cz++) for (int cx = -rad; cx <= rad && n < maxPer; cx++) {
        for (var s : src.applyStructureStarts(cx, cz)) {
          if (!s.structureId().equals(id) && !id.equals("*")) continue;
          var b = s.rawBox();
          System.out.println("seed=" + seed + " chunk=(" + cx + "," + cz + ") id=" + s.structureId() + " pieces=" + s.pieces().size()
            + " box=[" + b.minX() + "," + b.minY() + "," + b.minZ() + " .. " + b.maxX() + "," + b.maxY() + "," + b.maxZ() + "]");
          n++;
        }
      }
    }
  }
}
