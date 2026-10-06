// Furnace sampleBiomes 와 순정 .mca 바이옴을 대조해 불일치 쿼트를 나열하는 도구
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class BiomeDiff {
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]);
    int cx0=Integer.parseInt(a[1]),cx1=Integer.parseInt(a[2]),cz0=Integer.parseInt(a[3]),cz1=Integer.parseInt(a[4]);
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    List<String> names = r.entries("minecraft:worldgen/biome");
    NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
    Map<String,long[]> pair = new TreeMap<>(); Map<Integer,long[]> byQy = new TreeMap<>();
    int shown=0; long tot=0, bad=0; Set<String> chunksBad = new TreeSet<>();
    for (int cz=cz0; cz<=cz1; cz++) for (int cx=cx0; cx<=cx1; cx++) {
      var root = Harness.readChunk(java.nio.file.Path.of(a[5]), cx, cz);
      String[] vb = Harness.vanillaBiomes(root);
      int[] fb = src.sampleBiomes(cx, cz);
      for (int i=0;i<1536;i++) { tot++; String fn = names.get(fb[i]); if (!fn.equals(vb[i])) { bad++; chunksBad.add(cx+","+cz);
        pair.computeIfAbsent(vb[i]+" -> "+fn, k->new long[1])[0]++; byQy.computeIfAbsent(i/16, k->new long[1])[0]++;
        if (shown++ < 12) System.out.println("c("+cx+","+cz+") qx="+(i&3)+" qz="+((i>>2)&3)+" qy="+(i/16)+" (y="+((i/16)*4-64)+") vanilla="+vb[i]+" furnace="+fn); } }
    }
    System.out.println("biome cells bad="+bad+"/"+tot+" chunks="+chunksBad.size());
    pair.forEach((k,v)->System.out.println("  "+v[0]+"  "+k));
    System.out.println("by qy(y=qy*4-64): "); byQy.forEach((k,v)->System.out.print((k*4-64)+":"+v[0]+" ")); System.out.println();
  }
}
