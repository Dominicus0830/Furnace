// region 워커 풀 모양: 모든 차원이 풀 하나를 공유(S4, 기본)하거나 차원마다 따로(이전 방식). furnace.properties 로 바꾼다.
package org.domi.furnace.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * furnace.properties 키
 * <ul>
 *   <li>{@code shared-region-pool} (기본 true): 모든 차원·추가 월드가 region 워커 풀 하나를 함께 쓴다. 이때 {@code region-threads} 는
 *       서버 전체 워커 수다. false 면 차원마다 {@code region-threads} 개씩(이전 방식). JVM 인자 {@code -Dfurnace.sharedRegionPool=}
 *       이 있으면 그 값이 이긴다(되돌리기 스위치).</li>
 *   <li>{@code region-pool-dim-cap} (기본 0 = 워커 수−1): 공유 풀에서 다른 차원에 할 일이 있을 때 한 차원이 동시에 쓸 수 있는 워커 상한.</li>
 *   <li>{@code region-pool-starve-ticks} (기본 2): 차원 머리 작업이 이만큼 틱 늦으면 굶은 차원으로 보고 먼저 돌린다.</li>
 * </ul>
 * 바닐라 결과(틱 순서·region 안 동작)에는 영향이 없다 — 어느 워커가 어느 region 을 언제 돌리는지만 바뀐다.
 */
public record RegionPoolSettings(boolean shared, int dimCap, int starveTicks) {

    public static final RegionPoolSettings DEFAULT = new RegionPoolSettings(true, 0, 2);

    public static RegionPoolSettings load(Path file, Consumer<String> warn) {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                throw new RuntimeException("설정 로드 실패: " + file, e);
            }
        }
        return fromProperties(p, System.getProperty("furnace.sharedRegionPool"), warn);
    }

    /** jvmOverride: -Dfurnace.sharedRegionPool 값(없으면 null). */
    static RegionPoolSettings fromProperties(Properties p, String jvmOverride, Consumer<String> warn) {
        boolean shared = parseBool(p, "shared-region-pool", DEFAULT.shared());
        if (jvmOverride != null && !jvmOverride.isBlank()) {
            boolean o = Boolean.parseBoolean(jvmOverride.strip());
            if (o != shared && p.getProperty("shared-region-pool") != null) {
                warn.accept("JVM 인자 -Dfurnace.sharedRegionPool=" + o + " 가 furnace.properties 의 'shared-region-pool=" + shared + "' 보다 우선합니다");
            }
            shared = o;
        }
        // 기본값은 SharedScheduler 와 같게: -Dfurnace.sched.* 가 있으면 그것, 없으면 0(=워커 수−1)·2
        int dimCap = parseInt(p, "region-pool-dim-cap", Integer.getInteger("furnace.sched.dimCap", DEFAULT.dimCap()));
        int starve = parseInt(p, "region-pool-starve-ticks", Integer.getInteger("furnace.sched.starveTicks", DEFAULT.starveTicks()));
        if (dimCap < 0) throw new IllegalArgumentException("설정 'region-pool-dim-cap' 값 " + dimCap + " 이(가) 잘못됐습니다: 0(자동) 이상");
        if (starve < 1) throw new IllegalArgumentException("설정 'region-pool-starve-ticks' 값 " + starve + " 이(가) 잘못됐습니다: 1 이상");
        if (!shared && (p.getProperty("region-pool-dim-cap") != null || p.getProperty("region-pool-starve-ticks") != null)) {
            warn.accept("설정 'region-pool-dim-cap'·'region-pool-starve-ticks' 는 'shared-region-pool=true' 일 때만 쓰입니다");
        }
        return new RegionPoolSettings(shared, dimCap, starve);
    }

    /**
     * 기동 경고: 워커가 코어보다 훨씬 많으면 틱 스레드끼리 코어를 다퉈 틱 간격이 흔들린다(S4 측정 — 차원별 풀 12·24 개가 4 코어에서 p95 5ms 늦음).
     * dims 는 기동 때 차원 수, cores 는 코어 수.
     */
    public void warnOversubscription(int regionThreads, int dims, int cores, Consumer<String> warn) {
        int total = shared ? regionThreads : regionThreads * Math.max(1, dims);
        if (total > 2 * cores) {
            warn.accept(shared
                    ? "region-threads=" + regionThreads + " 는 서버 전체 워커 수입니다(공유 풀). 코어 " + cores + " 개의 두 배를 넘어 틱 간격이 흔들릴 수 있습니다"
                    : "차원별 region 풀: 워커 " + regionThreads + "×차원 " + dims + " = " + total + " 개가 코어 " + cores
                            + " 개를 다툽니다. 'shared-region-pool=true' 를 권장합니다");
        }
    }

    private static boolean parseBool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.strip();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("설정 '" + key + "' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
    }

    private static int parseInt(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 값 '" + v + "' 이(가) 숫자가 아닙니다", e);
        }
    }
}
