// 한 청크를 순정 월드와 블록 단위로 대조해 위치별로 출력 — 사용: ChunkDiff <seed> <cx> <cz> <vanillaWorldDir> [정규식필터]
// 환경변수 NO_STRUCT=1 이면 Furnace 구조물을 끈다(순정 generate-structures=false 월드용).
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class ChunkDiff {
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]);
    java.nio.file.Path w0 = java.nio.file.Path.of(a[3]);
    java.nio.file.Path w = "nether".equals(System.getenv("DIM")) ? w0.resolve("DIM-1") : "end".equals(System.getenv("DIM")) ? w0.resolve("DIM1") : w0;
    java.util.regex.Pattern f = a.length > 4 ? java.util.regex.Pattern.compile(a[4]) : null;
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    boolean nether = "nether".equals(System.getenv("DIM"));
    NoiseWorldSource src = nether ? NoiseWorldSource.nether(seed, reg, r) : "end".equals(System.getenv("DIM")) ? NoiseWorldSource.end(seed, reg, r) : NoiseWorldSource.overworld(seed, reg, r);
    if ("1".equals(System.getenv("NO_STRUCT"))) src.structureEngine().setStructuresEnabled(false);
    var root = Harness.readChunk(w, cx, cz);
    short[] v = Harness.vanillaBlocks(root);
    {   // 바이옴 대조(쿼트 단위)
      String[] vb = Harness.vanillaBiomes(root);
      int[] fb = src.sampleBiomes(cx, cz);
      java.util.List<String> names = r.entries("minecraft:worldgen/biome");
      int minSec = Math.floorDiv(src.minY(), 16), bad = 0;
      for (int i = 0; i < fb.length; i++) {
        int vi = (minSec + 4) * 64 + i;
        if (vi < 0 || vi >= vb.length || vb[vi] == null) continue;
        if (!vb[vi].equals(names.get(fb[i]))) { if (bad < 3) System.out.println("BIOME q" + i + " van=" + vb[vi] + " fur=" + names.get(fb[i])); bad++; }
      }
      System.out.println("biomeMismatch=" + bad + "/" + fb.length);
    }
    ChunkColumn col = src.generateWithFeatures(cx, cz);
    int n = 0, vs = 0, fs = 0, both = 0;
    for (int y = col.minY(); y <= col.maxY(); y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
      int p = (y + 64) * 256 + z * 16 + x;
      String fk = reg.canonicalKeyOf(col.getBlock(x, y, z)); if (fk == null) fk = "unknown"; if (fk.equals("minecraft:cave_air")) fk = "minecraft:air";
      String vk = Harness.keys.get(v[p]); if (vk.equals("minecraft:cave_air") || vk.equals("minecraft:void_air")) vk = "minecraft:air";
      boolean vS = vk.startsWith("minecraft:sculk"), fS = fk.startsWith("minecraft:sculk");
      if (vS) vs++; if (fS) fs++; if (vS && fk.equals(vk)) both++;
      if (fk.equals(vk)) continue;
      String line = (cx * 16 + x) + " " + y + " " + (cz * 16 + z) + "  van=" + vk + "  fur=" + fk;
      if (f != null && !f.matcher(line).find()) continue;
      System.out.println(line); n++;
    }
    System.out.println("diffs=" + n + " sculkFamily vanilla=" + vs + " furnace=" + fs + " sameExact=" + both);
  }
}
