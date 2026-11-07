package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.plugin.Plugin;

/** 시험 쪽과 {@link JarCompiler} 로 만든 플러그인 사이의 공용 칠판 — 플러그인 클래스로더가 부모(시험 클래스패스)에서 이 클래스를 찾는다. */
public final class WiringProbe {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    public static volatile Plugin plugin;
    public static volatile Plugin providingForUtil;
    public static volatile Plugin selfByClass;
    public static volatile Throwable failure;
    public static volatile ClassLoader failingLoader;

    private WiringProbe() {}

    public static void reset() {
        LOG.clear();
        plugin = null;
        providingForUtil = null;
        selfByClass = null;
        failure = null;
        failingLoader = null;
    }
}
