// tools/vanilla-data/ShapeVectors가 만든 바닐라 updateShape 벡터와 Furnace BlockShapes를 차분한다.
// 실행: java ShapeDiff <벡터파일> [맨 위 N개 불일치 출력]
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.block.ShapeLevel;

public class ShapeDiff {
    public static void main(String[] a) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        BlockShapes shapes = BlockShapes.of(reg);
        int show = a.length > 1 ? Integer.parseInt(a[1]) : 10;
        int total = 0, bad = 0;
        Map<String, Integer> badByBlock = new TreeMap<>();
        Map<String, Integer> totalByBlock = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of(a[0]))) {
            if (line.startsWith("#")) { if (line.contains("EXC")) System.out.println(line); continue; }
            String[] t = line.split(" ");
            int state = Integer.parseInt(t[0]);
            int dir = Integer.parseInt(t[1]);
            int[] cells = new int[27];
            for (int i = 0; i < 27; i++) cells[i] = Integer.parseInt(t[2 + i]);
            int expected = Integer.parseInt(t[29]);
            ShapeLevel level = (x, y, z) -> (x < 0 || x > 2 || y < 0 || y > 2 || z < 0 || z > 2) ? 0 : cells[x + y * 3 + z * 9];
            int[] dx = {0, 0, 0, 0, -1, 1}, dy = {-1, 1, 0, 0, 0, 0}, dz = {0, 0, -1, 1, 0, 0};
            int neighbor = level.getBlock(1 + dx[dir], 1 + dy[dir], 1 + dz[dir]);
            int got = shapes.updateShape(level, state, dir, neighbor, 1, 1, 1);
            String name = reg.nameOf(state);
            totalByBlock.merge(name, 1, Integer::sum);
            total++;
            if (got != expected) {
                bad++;
                badByBlock.merge(name, 1, Integer::sum);
                if (bad <= show) {
                    System.out.println("MISMATCH " + reg.canonicalKeyOf(state) + " dir=" + dir + " neighbor=" + reg.canonicalKeyOf(neighbor)
                        + "\n   expected " + reg.canonicalKeyOf(expected) + "\n   got      " + reg.canonicalKeyOf(got));
                }
            }
        }
        System.out.println("total=" + total + " mismatches=" + bad);
        badByBlock.forEach((k, v) -> System.out.println("  " + k + " " + v + "/" + totalByBlock.get(k)));
    }
}
