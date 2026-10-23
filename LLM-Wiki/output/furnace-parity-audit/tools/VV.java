// 순정 월드 두 개를 직접 비교해 노이즈 바닥의 성격(경계/내부, 블록종류, y대역, 바이옴)을 진단하는 도구
import java.nio.file.*;
import java.util.*;

public class VV {
    static void bump(Map<String, long[]> m, String k) { m.computeIfAbsent(k, x -> new long[1])[0]++; }

    public static void main(String[] a) throws Exception {
        int cx0 = Integer.parseInt(a[0]), cx1 = Integer.parseInt(a[1]), cz0 = Integer.parseInt(a[2]), cz1 = Integer.parseInt(a[3]);
        Path wa = Path.of(a[4]), wb = Path.of(a[5]);
        long total = 0, diff = 0, border = 0, borderPos = 0, propsOnly = 0;
        long[] yBand = new long[12];
        Map<String, long[]> pairs = new HashMap<>();
        long biomeDiff = 0; List<String> biomeEx = new ArrayList<>();
        int chunksWithDiff = 0, chunks = 0;
        List<Integer> perChunk = new ArrayList<>();
        for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
            var ra = Harness.readChunk(wa, cx, cz); var rb = Harness.readChunk(wb, cx, cz);
            short[] x = Harness.vanillaBlocks(ra), y = Harness.vanillaBlocks(rb);
            int cd = 0;
            for (int p = 0; p < x.length; p++) {
                int lx = p & 15, lz = (p >> 4) & 15;
                boolean b = lx < 3 || lx > 12 || lz < 3 || lz > 12;
                if (b) borderPos++;
                total++;
                if (x[p] != y[p]) {
                    diff++; cd++;
                    if (b) border++;
                    yBand[(p / 256) / 32]++;
                    String an = Harness.nameOfKey(Harness.keys.get(x[p])), bn = Harness.nameOfKey(Harness.keys.get(y[p]));
                    if (an.equals(bn)) { propsOnly++; bump(pairs, "props:" + an); } else bump(pairs, an + " -> " + bn);
                }
            }
            chunks++; if (cd > 0) chunksWithDiff++; perChunk.add(cd);
            String[] ba = Harness.vanillaBiomes(ra), bb = Harness.vanillaBiomes(rb);
            for (int i = 0; i < 1536; i++) if (!ba[i].equals(bb[i])) { biomeDiff++; if (biomeEx.size() < 8) biomeEx.add("c(" + cx + "," + cz + ") q=" + i + " " + ba[i] + " vs " + bb[i]); }
        }
        Collections.sort(perChunk);
        System.out.printf("chunks=%d  diffChunks=%d  diffBlocks=%d (%.4f%%)  propsOnly=%d  borderZoneShare=%.1f%% (border area=%.1f%%)%n",
            chunks, chunksWithDiff, diff, 100.0 * diff / total, propsOnly, 100.0 * border / Math.max(1, diff), 100.0 * borderPos / total);
        System.out.println("per-chunk diff: median=" + perChunk.get(perChunk.size() / 2) + " p90=" + perChunk.get((int) (perChunk.size() * 0.9)) + " max=" + perChunk.get(perChunk.size() - 1));
        System.out.println("y bands: " + Arrays.toString(yBand));
        System.out.println("vanilla biome diff cells=" + biomeDiff + " ex=" + biomeEx);
        pairs.entrySet().stream().sorted((p, q) -> Long.compare(q.getValue()[0], p.getValue()[0])).limit(25)
            .forEach(e -> System.out.printf("  %8d %s%n", e.getValue()[0], e.getKey()));
    }
}
