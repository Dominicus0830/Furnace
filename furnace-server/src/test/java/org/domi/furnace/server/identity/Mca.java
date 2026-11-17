// .mca 바이트 대조: 옛·새 구현이 같은 저장 순서로 쓴 영역 파일이 바이트까지 같은지(O02-03 류 저장 경로 최적화의 증명).
// 헤더 두 번째 4KB 는 청크별 저장 시각(초)이라, 같은 결과를 원하면 RegionFile 시계를 고정하거나(fixClock) 시각을 빼고 비교한다.
package org.domi.furnace.server.identity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import org.domi.furnace.server.world.anvil.RegionFile;

public final class Mca {

    private static final int SECTOR = 4096;

    private Mca() {}

    /** RegionFile 의 시험용 시계(초)를 고정한다. 패키지 비공개 필드라 반사로 넣는다. */
    public static void fixClock(RegionFile rf, int epochSeconds) {
        try {
            Field f = RegionFile.class.getDeclaredField("clock");
            f.setAccessible(true);
            f.set(rf, (IntSupplier) () -> epochSeconds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RegionFile.clock 을 못 바꿨다", e);
        }
    }

    /** dirA·dirB 아래(하위 폴더 포함) .mca·.mcc 파일 집합과 내용이 모두 같아야 한다. */
    public static void assertSameBytes(Path dirA, Path dirB) { assertSameBytes(dirA, dirB, false); }

    /** ignoreTimestamps 면 .mca 헤더의 저장 시각 영역(4096..8191)을 비교에서 뺀다. */
    public static void assertSameBytes(Path dirA, Path dirB, boolean ignoreTimestamps) {
        String diff = difference(dirA, dirB, ignoreTimestamps);
        if (diff != null) throw new AssertionError(".mca 바이트가 다르다: " + diff);
    }

    /** 같으면 null, 다르면 첫 차이 설명. */
    public static String difference(Path dirA, Path dirB, boolean ignoreTimestamps) {
        List<Path> a = regionFiles(dirA), b = regionFiles(dirB);
        if (!a.equals(b)) return "파일 목록이 다르다: " + a + " / " + b;
        if (a.isEmpty()) return "비교할 .mca 파일이 없다(" + dirA + ")";
        try {
            for (Path rel : a) {
                byte[] x = Files.readAllBytes(dirA.resolve(rel)), y = Files.readAllBytes(dirB.resolve(rel));
                boolean mca = rel.toString().endsWith(".mca");
                if (ignoreTimestamps && mca) {
                    x = x.clone();
                    y = y.clone();
                    for (int i = SECTOR; i < Math.min(2 * SECTOR, x.length); i++) x[i] = 0;
                    for (int i = SECTOR; i < Math.min(2 * SECTOR, y.length); i++) y[i] = 0;
                }
                int off = java.util.Arrays.mismatch(x, y);
                if (off >= 0) return rel + " 길이 " + x.length + "/" + y.length + ", 첫 차이 바이트 " + off + (mca ? " " + where(x, off) : "");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    /** 바이트 위치가 헤더(어느 청크 항목)인지, 몇 번 섹터인지. */
    static String where(byte[] mca, int off) {
        if (off < SECTOR) return "(위치 헤더, 청크 " + (off / 4 % 32) + "," + (off / 4 / 32) + ")";
        if (off < 2 * SECTOR) return "(시각 헤더, 청크 " + ((off - SECTOR) / 4 % 32) + "," + ((off - SECTOR) / 4 / 32) + ")";
        int sector = off / SECTOR;
        // 이 섹터를 차지하는 청크 항목을 찾는다
        for (int i = 0; i < 1024 && 4 * i + 3 < mca.length; i++) {
            int loc = ((mca[4 * i] & 0xFF) << 24) | ((mca[4 * i + 1] & 0xFF) << 16) | ((mca[4 * i + 2] & 0xFF) << 8) | (mca[4 * i + 3] & 0xFF);
            int start = loc >>> 8, count = loc & 0xFF;
            if (loc != 0 && sector >= start && sector < start + count) return "(섹터 " + sector + ", 청크 " + (i % 32) + "," + (i / 32) + ")";
        }
        return "(섹터 " + sector + ", 헤더에 없는 섹터)";
    }

    static List<Path> regionFiles(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> out = new ArrayList<>();
            s.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".mca") || p.toString().endsWith(".mcc"))
                    .map(dir::relativize).sorted().forEach(out::add);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
