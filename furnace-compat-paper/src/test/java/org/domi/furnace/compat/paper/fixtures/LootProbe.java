package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** LootGenerateEvent 시험 플러그인이 남기는 기록과, 시험이 고르는 동작 방식(""·"cancel"·"replace"). */
public final class LootProbe {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    public static volatile String mode = "";
    public static volatile Throwable failure;

    private LootProbe() {}

    public static void reset() {
        LOG.clear();
        mode = "";
        failure = null;
    }
}
