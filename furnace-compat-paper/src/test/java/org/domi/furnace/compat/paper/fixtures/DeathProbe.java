package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 사망 드롭 시험 플러그인(Bukkit 리스너)이 남기는 기록과, 시험이 고르는 동작 방식. */
public final class DeathProbe {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    public static volatile String mode = "";
    public static volatile Throwable failure;

    private DeathProbe() {}

    public static void reset() {
        LOG.clear();
        mode = "";
        failure = null;
    }
}
