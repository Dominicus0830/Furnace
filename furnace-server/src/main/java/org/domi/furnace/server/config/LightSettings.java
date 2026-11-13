// 빛 계산 풀(T-064): region 스레드 대신 따로 도는 빛 작업이 빛을 계산한다(바닐라 ThreadedLevelLightEngine 방식). 기본 꺼짐.
package org.domi.furnace.server.config;

import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * furnace.properties 키
 * <ul>
 *   <li>{@code light.async} (기본 false): true 면 region 은 빛 검사·새 청크 빛 켜기를 모아 빛 스레드에 넘기고, 계산이 끝나면 다음 틱 사이에
 *       결과를 보이는 빛으로 바꿔 건다. 빛 일이 큰 장치(램프 시계 등)가 region 틱을 잡아먹지 않는다. 대신 빛 스레드가 밀리면 바닐라
 *       과부하 때처럼 빛이 보이는(작물·스폰·잔디가 보는) 시점과 빛 갱신 패킷이 한두 틱 늦어진다. 저장은 밀린 빛을 기다린 뒤 한다.</li>
 *   <li>{@code light.async-threads} (기본 1): 빛 스레드 수(서버 전체). 바닐라는 월드마다 빛 작업 하나다.</li>
 * </ul>
 * 끈 동안(기본)은 지금과 바이트까지 같다.
 */
public record LightSettings(boolean async, int threads) {

    public static final LightSettings DEFAULT = new LightSettings(false, 1);

    private static volatile LightSettings current = DEFAULT;
    private static volatile Executor pool;

    public static LightSettings current() { return current; }

    public static void install(LightSettings s) { current = s == null ? DEFAULT : s; }

    public static LightSettings load(Path file, Consumer<String> warn) { return fromProperties(FurnaceProps.read(file), warn); }

    static LightSettings fromProperties(Properties p, Consumer<String> warn) {
        boolean async = DEFAULT.async;
        String v = p.getProperty("light.async");
        if (v != null) {
            v = v.strip();
            if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("설정 'light.async' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
            }
            async = Boolean.parseBoolean(v);
        }
        int threads = DEFAULT.threads;
        String t = p.getProperty("light.async-threads");
        if (t != null) {
            try {
                threads = Integer.parseInt(t.strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("설정 'light.async-threads' 가 숫자가 아님: " + t, e);
            }
            if (threads < 1) throw new IllegalArgumentException("설정 'light.async-threads' 값 " + threads + " 이(가) 잘못됐습니다: 1 이상");
            if (!async) warn.accept("설정 'light.async-threads' 는 'light.async=true' 일 때만 쓰입니다");
        }
        return new LightSettings(async, threads);
    }

    /** 켜져 있으면 빛 작업 풀(처음 부를 때 만든다, 데몬 스레드), 꺼져 있으면 null. */
    public static Executor executorIfEnabled() {
        LightSettings s = current;
        if (!s.async) return null;
        Executor e = pool;
        if (e == null) {
            synchronized (LightSettings.class) {
                e = pool;
                if (e == null) {
                    var n = new java.util.concurrent.atomic.AtomicInteger();
                    pool = e = java.util.concurrent.Executors.newFixedThreadPool(s.threads, r -> {
                        Thread th = new Thread(r, "furnace-light-" + n.incrementAndGet());
                        th.setDaemon(true);
                        return th;
                    });
                }
            }
        }
        return e;
    }
}
