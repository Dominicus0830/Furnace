// 기동 때 JVM 점검 경고(jvm.check, 기본 켜짐). Java 버전·힙 크기·GC 선택을 보고 서버에 불리한 설정을 알린다.
// 동작은 바꾸지 않는다 — 경고만 한다. 판단은 순수 함수({@link #check})라 시험이 합성 값으로 돌린다.
package org.domi.furnace.server.config;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class JvmCheck {

    private JvmCheck() {}

    public static final String KEY = "jvm.check";
    /** 이보다 작은 최대 힙은 월드·청크 캐시에 모자란다. */
    public static final long MIN_HEAP_BYTES = 1L << 30;
    /** LTS 가 아닌 Java 는 보안 패치가 6개월 만에 끊긴다. */
    private static final List<Integer> LTS = List.of(21, 25, 29);

    /** 현재 JVM 으로 점검하고 경고를 warn 에 보낸다. */
    public static void run(Consumer<String> warn) {
        for (String w : check(Runtime.version().feature(), Runtime.getRuntime().maxMemory(),
                ManagementFactory.getRuntimeMXBean().getInputArguments(), Runtime.getRuntime().availableProcessors())) warn.accept(w);
    }

    public static List<String> check(int javaFeature, long maxHeapBytes, List<String> jvmArgs, int cpus) {
        List<String> w = new ArrayList<>();
        if (javaFeature < 21) {
            w.add("Java " + javaFeature + " 로 실행 중입니다: Furnace 는 Java 21 이상을 요구합니다");
        } else if (!LTS.contains(javaFeature)) {
            w.add("Java " + javaFeature + " 는 장기 지원(LTS)이 아닙니다: 보안 패치가 일찍 끊깁니다. 운영 서버는 Java 21 또는 25 를 쓰세요");
        }
        if (maxHeapBytes < MIN_HEAP_BYTES) {
            w.add("최대 힙이 " + (maxHeapBytes >> 20) + "MB 입니다: 월드와 청크 캐시에 모자랄 수 있습니다. -Xmx 로 늘리세요(예: java -Xms4G -Xmx4G -jar furnace-server.jar)");
        }
        String xms = null;
        String xmx = null;
        boolean serial = false;
        boolean parallel = false;
        boolean epsilon = false;
        for (String a : jvmArgs) {
            String t = a.strip();
            if (t.startsWith("-Xms")) xms = t.substring(4);
            else if (t.startsWith("-Xmx")) xmx = t.substring(4);
            else if (t.equals("-XX:+UseSerialGC")) serial = true;
            else if (t.equals("-XX:+UseParallelGC")) parallel = true;
            else if (t.equals("-XX:+UseEpsilonGC")) epsilon = true;
        }
        if (serial) {
            w.add("-XX:+UseSerialGC: 단일 스레드 GC 라 힙이 큰 서버에서 틱이 끊깁니다. G1(기본) 또는 ZGC(-XX:+UseZGC)를 쓰세요");
        }
        if (parallel) {
            w.add("-XX:+UseParallelGC: 처리량 위주 GC 라 정지 시간이 길어 틱이 끊길 수 있습니다. G1(기본) 또는 ZGC(-XX:+UseZGC)를 쓰세요");
        }
        if (epsilon) {
            w.add("-XX:+UseEpsilonGC: 메모리를 회수하지 않아 힙이 차면 서버가 죽습니다. 시험용이 아니면 G1 이나 ZGC 를 쓰세요");
        }
        if (!serial && cpus < 2) {
            w.add("CPU 가 " + cpus + "개라 JVM 이 단일 스레드 GC(Serial)를 고릅니다. Furnace 는 region 별로 스레드를 쓰므로 코어를 더 주는 편이 좋습니다");
        }
        if (xms != null && xmx != null && !xms.equalsIgnoreCase(xmx) && sizeOf(xms) > 0 && sizeOf(xmx) > 0 && sizeOf(xms) < sizeOf(xmx)) {
            w.add("-Xms" + xms + " 가 -Xmx" + xmx + " 보다 작습니다: 힙이 자라는 동안 GC 가 잦아 틱이 끊깁니다. 둘을 같게 하세요");
        }
        return w;
    }

    /** "4G" "512m" "1048576k" "12345" → 바이트, 읽지 못하면 -1. */
    static long sizeOf(String s) {
        if (s == null || s.isEmpty()) return -1;
        char u = Character.toLowerCase(s.charAt(s.length() - 1));
        long mult = 1;
        String num = s;
        switch (u) {
            case 'k' -> mult = 1L << 10;
            case 'm' -> mult = 1L << 20;
            case 'g' -> mult = 1L << 30;
            case 't' -> mult = 1L << 40;
            default -> { }
        }
        if (mult != 1) num = s.substring(0, s.length() - 1);
        try {
            return Long.parseLong(num.toLowerCase(Locale.ROOT)) * mult;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
