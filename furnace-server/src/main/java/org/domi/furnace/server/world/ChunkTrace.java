// 청크 로딩 단계별 시간 진단(-Dfurnace.chunktrace=true). 꺼져 있으면 ON 이 상수 거짓이라 부르는 쪽 분기가 사라진다.
// 단계마다 횟수·합계·최대를 모으고, 5초마다 그 사이의 값만 [CHUNKTRACE] 한 줄씩 로그로 남긴다(누적하지 않는다).
// 단계 이름: src.gen(생성)·src.disk(디스크 읽기+디코드), light(POI·블록 엔티티·빛 미리 계산), hop.load(완성 → 주인 region 이 설치하기까지),
// install(설치), ser(본문 직렬화+압축), hop.ser(직렬화 끝 → 주인 region 이 받기까지), batch(Start → Finished 를 보낼 때까지),
// batch.cached(배치 안 캐시 적중 청크 수), batch.size, client.want(클라이언트가 알린 틱당 청크 수 ×100).
package org.domi.furnace.server.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

public final class ChunkTrace {
    public static final boolean ON = Boolean.getBoolean("furnace.chunktrace");

    private static final class Stage {
        final LongAdder n = new LongAdder(), sum = new LongAdder();
        final LongAccumulator max = new LongAccumulator(Math::max, 0L);
    }

    private static final Map<String, Stage> STAGES = new ConcurrentHashMap<>();

    static {
        if (ON) {
            Thread t = new Thread(() -> {
                while (true) {
                    try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                    dump();
                }
            }, "furnace-chunktrace");
            t.setDaemon(true);
            t.start();
        }
    }

    private ChunkTrace() {}

    /** 시간(나노초) 하나를 단계에 넣는다. */
    public static void nanos(String stage, long nanos) { value(stage, nanos); }

    /** 값 하나를 단계에 넣는다(시간이 아닌 수 — 배치 크기 등). */
    public static void value(String stage, long v) {
        Stage s = STAGES.computeIfAbsent(stage, k -> new Stage());
        s.n.increment();
        s.sum.add(v);
        s.max.accumulate(v);
    }

    static void dump() {
        StringBuilder sb = new StringBuilder();
        for (var e : new java.util.TreeMap<>(STAGES).entrySet()) {
            Stage s = e.getValue();
            long n = s.n.sumThenReset();
            long sum = s.sum.sumThenReset();
            long max = s.max.getThenReset();
            if (n == 0) continue;
            boolean time = !e.getKey().startsWith("batch.") && !e.getKey().startsWith("client.");
            if (time) sb.append(String.format(" %s n=%d avg=%.2fms max=%.1fms sum=%.0fms", e.getKey(), n, sum / 1e6 / n, max / 1e6, sum / 1e6));
            else sb.append(String.format(" %s n=%d avg=%.2f max=%d", e.getKey(), n, (double) sum / n, max));
        }
        if (!sb.isEmpty()) org.slf4j.LoggerFactory.getLogger(ChunkTrace.class).info("[CHUNKTRACE]{}", sb);
    }
}
