// FAWE 2.16.2 CharBlocks 겹친 첫 읽기 경쟁 재현(T-204 후속 — //fixblocks 뒤 레드스톤 가루 한 칸이 실행마다 갈림).
// 서버 없이 FAWE 의 진짜 CharBlocks(worldedit-core, Maven Central)를 돌린다. 쓰기·실행은 README.md.
//
// 경쟁: CharBlocks.Section.EMPTY.get 은 층 잠금 안에서 update 를 부르는데, 기다리는 사이 다른 스레드가 그 층을 이미 실었으면
// update(layer, 이미 공개된 배열) 로 그 배열을 제자리에서 다시 채운다(PaperweightGetBlocks.update: toRaw 로 팔레트 번호를 쓴 뒤 팔레트를 사상).
// 그 사이 이 배열을 읽는 스레드는 블록 순번 대신 팔레트 번호(작은 수 — 공기류)를 본다. 첫 읽기(서버 섹션 읽기)가 오래 걸릴수록 둘째 스레드가 그 안에
// 들어올 확률이 커진다 — Furnace 의 주인 아닌 스레드 첫 섹션 읽기(장벽 실체화)는 Paper 의 필드 읽기보다 길다.
import com.fastasyncworldedit.core.queue.implementation.blocks.CharBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class RaceHarness {

    // 블록 순번(FAWE BlockTypesCache 순번 흉내 — 1..3 은 공기류)
    static final char AIR = 1, STONE = 300, STAIRS = 4000, RBLOCK = 4500, WIRE = 5000;
    static final char[] truth = new char[4096];
    static final int[] paletteIndex = new int[4096];
    static char[] palette;
    static volatile long readNanos;
    static volatile int sink;
    static final AtomicInteger inPlace = new AtomicInteger();

    /** PaperweightGetBlocks.update 꼴: 서버 섹션 읽기(readNanos) → toRaw(팔레트 번호) → 팔레트 사상. data 가 있으면 그 배열을 제자리에서 채운다. */
    static final class Get extends CharBlocks {
        Get() { super(0, 15); }

        @Override protected char defaultOrdinal() { return AIR; }

        @Override
        public char[] update(int layer, char[] data, boolean aggressive) {
            long end = System.nanoTime() + readNanos;
            while (System.nanoTime() < end) Thread.onSpinWait();
            if (data != null) inPlace.incrementAndGet();
            else data = new char[4096];
            for (int i = 0; i < 4096; i++) data[i] = (char) paletteIndex[i];
            for (int i = 0; i < 4096; i++) data[i] = palette[data[i]];
            return data;
        }

        @Override public Map tiles() { return Map.of(); }
        @Override public com.fastasyncworldedit.core.nbt.FaweCompoundTag tile(int x, int y, int z) { return null; }
        @Override public Collection entities() { return List.of(); }
        @Override public com.sk89q.worldedit.world.biome.BiomeType getBiomeType(int x, int y, int z) { return null; }
        @Override public void removeSectionLighting(int layer, boolean sky) {}
    }

    public static void main(String[] args) throws Exception {
        String[] micros = (args.length > 0 ? args[0] : "5,20,50,140,300").split(",");
        int trials = args.length > 1 ? Integer.parseInt(args[1]) : 3000;
        // 청크 (-3,0) 6층(y96~111), 칸 = (y&15)<<8 | z<<4 | x. y=100: 계단 -48..-45(x0~3)·돌 -40..-35(x8~13), y=101: 가루 + 레드스톤 블록(-38,-37 × z2,3)
        Arrays.fill(truth, AIR);
        for (int z = 0; z <= 5; z++) {
            for (int x = 0; x <= 3; x++) truth[4 << 8 | z << 4 | x] = STAIRS;
            for (int x = 8; x <= 13; x++) {
                truth[4 << 8 | z << 4 | x] = STONE;
                truth[5 << 8 | z << 4 | x] = (x == 10 || x == 11) && (z == 2 || z == 3) ? RBLOCK : WIRE;
            }
        }
        List<Character> pal = new ArrayList<>();   // 처음 나온 순서의 팔레트 — 공기 0, 계단 1, 돌 2, 가루 3(= 공기류 순번), 레드스톤 블록 4
        for (int i = 0; i < 4096; i++) {
            int k = pal.indexOf(truth[i]);
            if (k < 0) {
                pal.add(truth[i]);
                k = pal.size() - 1;
            }
            paletteIndex[i] = k;
        }
        palette = new char[pal.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = pal.get(i);
        int north = 5 << 8 | 3 << 4 | 13;   // (-35,101,3) — (-35,101,4) 가루의 북쪽 이웃
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (String m : micros) {
                readNanos = Long.parseLong(m.trim()) * 1000;
                inPlace.set(0);
                int wrong = 0;
                for (int t = 0; t < trials; t++) {
                    Get get = new Get();
                    CountDownLatch go = new CountDownLatch(1);
                    long otherDelay = ThreadLocalRandom.current().nextLong(0, 1_000_000);
                    // A: 자기 청크(-3,0)를 거른다 — 층을 싣고 1.5ms 동안 가루 이웃을 읽는다
                    Future<Boolean> a = pool.submit(() -> {
                        go.await();
                        get.load(6);
                        boolean bad = false;
                        long end = System.nanoTime() + 1_500_000;
                        while (System.nanoTime() < end) {
                            int f = sink;
                            if (get.get(6, north) != WIRE) bad = true;
                            for (int k = 0; k < 50; k++) f += k * f;
                            sink = f;
                        }
                        return bad;
                    });
                    // D: 옆 청크(-4,0)의 계단(-49,100,z)이 동쪽 이웃(-48,100,z)을 읽는다 — 시작 뒤 0~1ms 에 이 청크를 처음 읽는다
                    Future<?> d = pool.submit(() -> {
                        go.await();
                        long end = System.nanoTime() + otherDelay;
                        while (System.nanoTime() < end) Thread.onSpinWait();
                        get.get(6, 4 << 8);
                        return null;
                    });
                    go.countDown();
                    if (a.get()) wrong++;
                    d.get();
                }
                System.out.printf("first read %4s us: in-place refill %4d / %d, north neighbour read wrong %4d / %d%n", m.trim(), inPlace.get(), trials, wrong, trials);
            }
        } finally {
            pool.shutdown();
        }
    }
}
