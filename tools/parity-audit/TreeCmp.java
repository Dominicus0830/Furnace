// 특정 통나무/잎 블록의 순정 두 실행과 Furnace 간 위치 겹침률을 재는 도구
import java.util.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.*;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
public class TreeCmp {
  public static void main(String[] a) throws Exception {
    long seed = Long.parseLong(a[0]); int cx0=Integer.parseInt(a[1]),cx1=Integer.parseInt(a[2]),cz0=Integer.parseInt(a[3]),cz1=Integer.parseInt(a[4]);
    java.nio.file.Path w1 = java.nio.file.Path.of(a[5]), w2 = java.nio.file.Path.of(a[6]); String log = a[7], leaves = a[8];
    BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
    NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, r);
    long v1=0, v2=0, f=0, v1v2=0, v1f=0, lv1=0, lv2=0, lf=0, lv1v2=0, lv1f=0;
    for (int cz=cz0; cz<=cz1; cz++) for (int cx=cx0; cx<=cx1; cx++) {
      short[] b1 = Harness.vanillaBlocks(Harness.readChunk(w1, cx, cz)), b2 = Harness.vanillaBlocks(Harness.readChunk(w2, cx, cz));
      ChunkColumn col = src.generateWithFeatures(cx, cz);
      for (int y=-64; y<320; y++) for (int z=0; z<16; z++) for (int x=0; x<16; x++) {
        int p=(y+64)*256+z*16+x;
        String n1 = Harness.nameOfKey(Harness.keys.get(b1[p])), n2 = Harness.nameOfKey(Harness.keys.get(b2[p]));
        String k = reg.canonicalKeyOf(col.getBlock(x,y,z)); String nf = k==null? "?" : Harness.nameOfKey(k);
        boolean l1=n1.equals(log), l2=n2.equals(log), lf_=nf.equals(log);
        if (l1) v1++; if (l2) v2++; if (lf_) f++; if (l1&&l2) v1v2++; if (l1&&lf_) v1f++;
        boolean e1=n1.equals(leaves), e2=n2.equals(leaves), ef=nf.equals(leaves);
        if (e1) lv1++; if (e2) lv2++; if (ef) lf++; if (e1&&e2) lv1v2++; if (e1&&ef) lv1f++;
      }
    }
    System.out.printf("%s count: vanillaRun1=%d vanillaRun2=%d furnace=%d | overlap(run1,run2)=%d (%.1f%% of run1)  overlap(run1,furnace)=%d (%.1f%% of run1)%n", log, v1, v2, f, v1v2, 100.0*v1v2/v1, v1f, 100.0*v1f/v1);
    System.out.printf("%s count: vanillaRun1=%d vanillaRun2=%d furnace=%d | overlap(run1,run2)=%d (%.1f%%)  overlap(run1,furnace)=%d (%.1f%%)%n", leaves, lv1, lv2, lf, lv1v2, 100.0*lv1v2/lv1, lv1f, 100.0*lv1f/lv1);
  }
}
