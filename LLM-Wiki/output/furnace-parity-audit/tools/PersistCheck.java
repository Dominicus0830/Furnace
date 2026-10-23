// FurnaceMain 과 동일한 배선으로 생성→저장(World.saveChunkAsync 경로)→재시작→재로딩 후 바이옴/블록 보존 여부를 잰다
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.domi.furnace.server.net.io.nbt.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.anvil.*;
import org.domi.furnace.server.world.gen.*;
public class PersistCheck {
  static int[] allBiomes(ChunkSnapshot s) { int[] out = new int[1536]; int k = 0; for (var sec : s.sections()) for (int b : sec.biomes()) out[k++] = b; return out; }
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]);
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    List<String> names = r.entries("minecraft:worldgen/biome");
    int plains = Math.max(0, r.indexOf("minecraft:worldgen/biome", "minecraft:plains"));   // FurnaceMain 과 동일
    NoiseWorldSource noise = NoiseWorldSource.overworld(seed, reg, r);
    Path dir = Files.createTempDirectory("furnace-persist");
    AnvilChunkCodec codec = new AnvilChunkCodec(reg, plains);
    RegionFileStore store = new RegionFileStore(dir);
    AnvilWorldSource run1 = new AnvilWorldSource(dir, store, codec, noise);
    int[][] coords = new int[a.length - 1][];
    for (int i = 1; i < a.length; i++) { String[] p = a[i].split(":"); coords[i-1] = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}; }
    Map<String, int[]> fresh = new LinkedHashMap<>(); Map<String, long[]> freshBlocks = new HashMap<>();
    for (int[] c : coords) {
      ChunkColumn col = run1.generate(c[0], c[1]);                 // 최초 생성(디스크 없음 → generateWithFeatures)
      fresh.put(c[0] + "," + c[1], allBiomes(ChunkSnapshot.of(c[0], c[1], col)));
      col.setBlock(0, 319, 0, reg.defaultStateId("minecraft:stone"));  // 플레이어 편집 시뮬레이션 → dirty
      if (!col.dirty()) throw new IllegalStateException("not dirty");
      ChunkSnapshot snap = ChunkSnapshot.of(c[0], c[1], col);       // World.saveChunkAsync 와 동일
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      NbtIo.write(new DataOutputStream(bos), codec.toNbt(snap), "");
      store.writeChunk(c[0], c[1], bos.toByteArray());
    }
    store.closeAll();
    // ---- 서버 재시작 시뮬레이션 ----
    AnvilWorldSource run2 = new AnvilWorldSource(dir, new RegionFileStore(dir), new AnvilChunkCodec(reg, plains), noise);
    long cells = 0, bad = 0; int chunksChanged = 0;
    for (int[] c : coords) {
      ChunkColumn re = run2.generate(c[0], c[1]);                  // 디스크에서 로드(fromNbt)
      int[] b1 = fresh.get(c[0] + "," + c[1]), b2 = allBiomes(ChunkSnapshot.of(c[0], c[1], re));
      int bad1 = 0; Set<String> distinctFresh = new TreeSet<>(), distinctRe = new TreeSet<>();
      for (int i = 0; i < 1536; i++) { distinctFresh.add(names.get(b1[i])); distinctRe.add(names.get(b2[i])); if (b1[i] != b2[i]) bad1++; }
      cells += 1536; bad += bad1; if (bad1 > 0) chunksChanged++;
      System.out.printf("chunk (%d,%d) biomes fresh=%s  reloaded=%s  changedCells=%d/1536%n", c[0], c[1], distinctFresh, distinctRe, bad1);
    }
    System.out.printf("TOTAL changed biome cells after save+restart: %d/%d in %d/%d chunks%n", bad, cells, chunksChanged, coords.length);
  }
}
