package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 시험과 {@link JarCompiler} 로 만든 홀로그램 플러그인 사이의 칠판(부모 클래스로더에서 찾는다). 엔티티는 Bukkit 객체 그대로 둔다. */
public final class HoloProbe {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    /** 마지막으로 만든 홀로그램 줄의 갑옷 거치대·돼지(Bukkit 객체). */
    public static volatile Object stand;
    public static volatile Object pig;
    /** 클릭 시험용 평범한 돼지. */
    public static volatile Object plainPig;
    /** 평범한 돼지를 때리면 취소할지. */
    public static volatile boolean cancelDamage;
    public static volatile Throwable failure;

    private HoloProbe() {}

    public static void reset() {
        LOG.clear();
        stand = null;
        pig = null;
        plainPig = null;
        cancelDamage = false;
        failure = null;
    }

    /** prefix 로 시작하는 기록만. */
    public static List<String> log(String prefix) { return LOG.stream().filter(s -> s.startsWith(prefix)).toList(); }
}
