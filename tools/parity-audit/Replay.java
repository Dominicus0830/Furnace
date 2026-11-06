// 순정 서버가 실제로 청크를 decorate한 순서(ranks 파일)대로 하나의 공유 월드 위에서 Furnace feature를 전부 재생한다.
// 요청 청크마다 3x3을 독립 재계산하는 서버 방식과 달리, 순정과 같은 전역 순서·같은 누적 상태에서 돌리므로
// "순서 비결정성/지역 모델의 한계"가 빠진 순수한 feature 구현 오차만 남는다(측정 전용 도구).
import java.nio.file.*;
import java.util.*;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

public final class Replay {
    private static final int R = 2; // 읽기 반경(origin ±2)
    private final NoiseWorldSource src;
    private final Map<Long, ChunkColumn> cols = new HashMap<>();
    private final Map<Long, int[]> biomes = new HashMap<>();
    private final Map<Long, Set<String>> biomeNames = new HashMap<>();
    private final Set<Long> ranked = new HashSet<>();

    static long key(int x, int z) { return (((long) x) << 32) ^ (z & 0xFFFFFFFFL); }

    public Replay(NoiseWorldSource src, Path ranksFile) throws Exception {
        this(src, ranksFile, null);
    }

    /** dumpDir이 있으면 origin마다 RNG/feature 추적(-Dfurnace.rngTrace=true 필요)을 dumpDir/ox_oz.txt로 남긴다. */
    public Replay(NoiseWorldSource src, Path ranksFile, Path dumpDir) throws Exception {
        this.src = src;
        List<int[]> order = new ArrayList<>();
        for (String line : Files.readAllLines(ranksFile)) {
            String[] t = line.trim().split("\\s+");
            if (t.length == 3) order.add(new int[] {Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2])});
        }
        order.sort(Comparator.comparingInt(a -> a[2]));
        for (int[] o : order) ranked.add(key(o[0], o[1]));
        // 필요한 청크를 병렬로 pristine(CARVERS)까지 생성
        Set<Long> need = new TreeSet<>();
        List<int[]> needList = new ArrayList<>();
        for (int[] o : order) {
            for (int dz = -R; dz <= R; dz++) {
                for (int dx = -R; dx <= R; dx++) {
                    if (need.add(key(o[0] + dx, o[1] + dz))) needList.add(new int[] {o[0] + dx, o[1] + dz});
                }
            }
        }
        Map<Long, ChunkColumn> built = Collections.synchronizedMap(new HashMap<>());
        Map<Long, int[]> builtBiomes = Collections.synchronizedMap(new HashMap<>());
        needList.parallelStream().forEach(c -> {
            NoiseWorldSource.Generated g = src.generateWithBiomes(c[0], c[1]);
            src.applyCarvers(g.col(), c[0], c[1]);
            built.put(key(c[0], c[1]), g.col());
            builtBiomes.put(key(c[0], c[1]), g.biomeIds());
        });
        cols.putAll(built);
        biomes.putAll(builtBiomes);
        for (var e : builtBiomes.entrySet()) biomeNames.put(e.getKey(), src.biomeNamesOf(e.getValue()));
        // 순위 순서대로 decorate
        for (int[] o : order) {
            int ox = o[0], oz = o[1];
            ChunkColumn[] grid = new ChunkColumn[(2 * R + 1) * (2 * R + 1)];
            int[][] bgrid = new int[grid.length][];
            for (int dz = -R; dz <= R; dz++) {
                for (int dx = -R; dx <= R; dx++) {
                    int idx = (dz + R) * (2 * R + 1) + (dx + R);
                    grid[idx] = cols.get(key(ox + dx, oz + dz));
                    bgrid[idx] = biomes.get(key(ox + dx, oz + dz));
                }
            }
            Set<String> nb = new HashSet<>();
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) nb.addAll(biomeNames.get(key(ox + dx, oz + dz)));
            FeatureWorldView view = new FeatureWorldView(grid, bgrid, ox, oz, ox, oz, R, false, src.seed());
            if (dumpDir != null) {
                Files.createDirectories(dumpDir);
                org.domi.furnace.server.world.gen.carver.RandomTrace.setTarget(ox, oz);
                java.io.PrintStream orig = System.out;
                try (java.io.PrintStream ps = new java.io.PrintStream(new java.io.BufferedOutputStream(
                        new java.io.FileOutputStream(dumpDir.resolve(ox + "_" + oz + ".txt").toFile()), 1 << 20), false, "UTF-8")) {
                    System.setOut(ps);
                    src.decorateOrigin(view, nb, ox, oz, List.of());
                    ps.flush();
                } finally {
                    System.setOut(orig);
                }
            } else {
                src.decorateOrigin(view, nb, ox, oz, List.of());
            }
        }
    }

    /** 이 청크의 3x3 origin이 전부 순정에서 decorate됐으면 비교할 수 있다. */
    public boolean valid(int cx, int cz) {
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (!ranked.contains(key(cx + dx, cz + dz))) return false;
        return true;
    }

    public ChunkColumn column(int cx, int cz) { return cols.get(key(cx, cz)); }
}
