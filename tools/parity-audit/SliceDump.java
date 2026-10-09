// 순정/Furnace 블록을 y 단면 문자 격자로 나란히 출력 — 사용: SliceDump <seed> <world> <x0> <x1> <y0> <y1> <z0> <z1>
// 문자: S sculk, v sculk_vein, C catalyst, s sensor, h shrieker, . 공기, ~ 물, # 그 밖 고체, 대문자 아닌 것은 표 참조. NO_STRUCT=1 지원.
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class SliceDump {
  static char ch(String k) {
    if (k.startsWith("minecraft:sculk_vein")) return 'v';
    if (k.startsWith("minecraft:sculk_catalyst")) return 'C';
    if (k.startsWith("minecraft:sculk_sensor")) return 's';
    if (k.startsWith("minecraft:sculk_shrieker")) return 'h';
    if (k.startsWith("minecraft:sculk")) return 'S';
    if (k.startsWith("minecraft:air") || k.startsWith("minecraft:cave_air") || k.startsWith("minecraft:void_air")) return '.';
    if (k.startsWith("minecraft:water")) return '~';
    return '#';
  }
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); java.nio.file.Path w = java.nio.file.Path.of(a[1]);
    int x0 = Integer.parseInt(a[2]), x1 = Integer.parseInt(a[3]), y0 = Integer.parseInt(a[4]), y1 = Integer.parseInt(a[5]), z0 = Integer.parseInt(a[6]), z1 = Integer.parseInt(a[7]);
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
    if ("1".equals(System.getenv("NO_STRUCT"))) src.structureEngine().setStructuresEnabled(false);
    Map<Long, ChunkColumn> fc = new HashMap<>(); Map<Long, short[]> vc = new HashMap<>();
    for (int y = y0; y <= y1; y++) {
      System.out.println("y=" + y + "   vanilla" + " ".repeat(Math.max(1, x1 - x0 - 5)) + "furnace");
      for (int z = z0; z <= z1; z++) {
        StringBuilder vs = new StringBuilder(), fs = new StringBuilder();
        for (int x = x0; x <= x1; x++) {
          int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16); long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
          ChunkColumn col = fc.computeIfAbsent(key, k -> src.generateWithFeatures(cx, cz));
          short[] v = vc.computeIfAbsent(key, k -> { try { return Harness.vanillaBlocks(Harness.readChunk(w, cx, cz)); } catch (Exception e) { throw new RuntimeException(e); } });
          int lx = x - cx * 16, lz = z - cz * 16;
          char vch = ch(Harness.keys.get(v[(y + 64) * 256 + lz * 16 + lx]));
          String fk = reg.canonicalKeyOf(col.getBlock(lx, y, lz)); char fch = ch(fk == null ? "?" : fk);
          vs.append(vch); fs.append(fch == vch ? fch : Character.toLowerCase(fch) == fch && fch != '.' && fch != '#' && fch != '~' ? Character.toUpperCase(fch) : fch);
        }
        System.out.println(String.format("%5d ", z) + vs + "   " + fs);
      }
    }
  }
}
