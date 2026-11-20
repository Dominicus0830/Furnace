package org.domi.furnace.compat.nms;

import org.domi.furnace.compat.i18n.CompatLang;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.compat.paper.PluginLoaders;
import org.slf4j.LoggerFactory;

/** 지금 shim 을 부른 플러그인 이름 찾기와 "플러그인·주제마다 한 번" 경고. 스택에 플러그인 클래스가 없으면 아무것도 하지 않는다. */
public final class NmsCaller {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private NmsCaller() {}

    /** 스택에서 가장 가까운 플러그인 이름(없으면 null). */
    public static String pluginName() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .map(c -> PluginLoaders.of(c.getClassLoader()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(a -> a.name())
                .orElse(null));
    }

    /** 부른 플러그인마다 주제당 한 번만 WARN. 문구는 호환 번들의 키이고, 첫 자리({0})에 플러그인 이름이, 이어서 args 가 들어간다. 플러그인을 못 찾으면 안 낸다. */
    public static void warnOnce(String topic, String key, Object... args) {
        String who = pluginName();
        if (who == null || !WARNED.add(topic + "|" + who)) return;
        Object[] all = new Object[args.length + 1];
        all[0] = who;
        System.arraycopy(args, 0, all, 1, args.length);
        LoggerFactory.getLogger("Furnace").warn(CompatLang.t(key, all));
    }

    /** {@link #warnOnce} 와 같지만 플러그인 이름을 미리 잡아 둔 경우(넘긴 작업이 다른 스레드에서 끝날 때 — 그 스택에는 플러그인이 없다). */
    public static void warnOnceAs(String who, String topic, String key, Object... args) {
        if (who == null || !WARNED.add(topic + "|" + who)) return;
        Object[] all = new Object[args.length + 1];
        all[0] = who;
        System.arraycopy(args, 0, all, 1, args.length);
        LoggerFactory.getLogger("Furnace").warn(CompatLang.t(key, all));
    }

    /** 시험에서 한 번 기록을 비운다. */
    public static void resetWarnings() { WARNED.clear(); }
}
