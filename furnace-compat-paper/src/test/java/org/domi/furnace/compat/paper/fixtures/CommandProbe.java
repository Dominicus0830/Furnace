package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 명령 정합 시험의 칠판 — 시험 쪽과 {@link JarCompiler} 로 만든 플러그인이 같이 본다. */
public final class CommandProbe {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    /** 표지판 명령 이벤트에서 취소할지. */
    public static volatile boolean cancelSign;
    /** 비어 있지 않으면 표지판 명령 이벤트의 메시지를 이걸로 바꾼다. */
    public static volatile String rewriteSign;

    private CommandProbe() {}

    public static void reset() {
        LOG.clear();
        cancelSign = false;
        rewriteSign = null;
    }
}
