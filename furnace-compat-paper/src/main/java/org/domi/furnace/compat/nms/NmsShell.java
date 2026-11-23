package org.domi.furnace.compat.nms;

import org.domi.furnace.compat.i18n.CompatLang;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 전체 껍데기(T-260)의 던지는 몸통이 부르는 곳. 플러그인 바이트코드의 직접 호출은 {@link NmsLinker} 가 링크 때 덫으로 바꾸므로 여기에는
 * 리플렉션({@code Method.invoke}·{@code Field.get})이나 변환되지 않은 코드로 들어온 호출만 온다. 값을 지어내지 않고 늘 던지며,
 * 덫과 같은 {@code compat gaps} 표({@link NmsGaps})에 센다 — 던지는 길에서만 세므로 정상 경로 비용은 0이다.
 */
public final class NmsShell {
    private static final Logger LOG = LoggerFactory.getLogger("Furnace");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    static final String WHY = "only the shape exists in Furnace yet (full shell, T-260)";

    private NmsShell() {}

    /** 던질 예외를 돌려준다(껍데기 몸통: {@code throw NmsShell.unsupported("소유.이름(서술자)")}). */
    public static RuntimeException unsupported(String target) {
        String plugin = NmsCaller.pluginName();
        String who = plugin == null ? "?" : plugin;
        NmsGaps.trapped(who, target, target.indexOf('(') >= 0 ? UnsupportedNmsLink.METHOD : UnsupportedNmsLink.FIELD);
        if (WARNED.add(who + '\0' + target)) LOG.warn(CompatLang.t("compat.nms-unsupported-call", who, "reflection", target, WHY));
        return new UnsupportedNmsCall(target, WHY);
    }

    /**
     * NMS 밖 범위(Bukkit API·Paper 내부·Mojang) 껍데기 가운데 값 없는 필드를 가진 것의 클래스 초기화가 부른다. 연결기 덫은 그 범위의 필드 참조를 보지
     * 않으므로, 필드가 조용히 null/0 을 주는 대신 클래스 초기화를 실패시킨다(JVM 이 ExceptionInInitializerError, 그다음 NoClassDefFoundError — 예전 "클래스 없음"과 같은 꼴).
     */
    public static RuntimeException unsupportedClass(String className) {
        String plugin = NmsCaller.pluginName();
        String who = plugin == null ? "?" : plugin;
        NmsGaps.trapped(who, className, UnsupportedNmsLink.CLASS);
        if (WARNED.add(who + '\0' + className)) LOG.warn(CompatLang.t("compat.nms-unsupported-call", who, "class init", className, WHY));
        return new UnsupportedNmsCall(className, WHY);
    }
}
