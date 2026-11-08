package org.domi.furnace.compat.nms;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 변환된 플러그인 바이트코드가 shim 에 없는 NMS 심볼 자리에서 부르는 덫(설계 H5 5.1). 그 호출이 <b>실행될 때만</b> 던지므로
 * 그 기능을 안 쓰는 플러그인은 멀쩡히 돈다. 같은 (플러그인, 대상)은 첫 번째만 WARN, 나머지는 DEBUG.
 */
public final class NmsTrap {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private NmsTrap() {}

    /**
     * 늘 던진다(반환형이 void 라 호출 뒤에 원래 반환값 자리를 채우는 코드가 남지만 실행되지 않는다).
     *
     * @param plugin 플러그인 이름
     * @param site   호출 위치(클래스#메서드:줄)
     * @param target 대상 심볼(점 이름, 예: net.minecraft.world.level.chunk.LevelChunk#getFluidState(..))
     * @param why    이유·대체(등급 문구)
     */
    public static void call(String plugin, String site, String target, String why) {
        UnsupportedNmsCall e = new UnsupportedNmsCall(target, why + " (플러그인=" + plugin + ", 호출 위치=" + site + ")");
        if (WARNED.add(plugin + '\0' + target)) {
            LOG.warn("미지원 NMS 호출: 플러그인={}, 호출 위치={}, 대상={} — {} (같은 대상의 다음 호출은 DEBUG)", plugin, site, target, why);
        } else {
            LOG.debug("미지원 NMS 호출: 플러그인={}, 호출 위치={}, 대상={}", plugin, site, target);
        }
        throw e;
    }

    /** 시험용: 경고 기록을 지운다. */
    public static void resetWarnings() { WARNED.clear(); }
}
