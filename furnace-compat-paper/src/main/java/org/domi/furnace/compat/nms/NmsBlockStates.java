package org.domi.furnace.compat.nms;

import org.domi.furnace.api.internal.BlockStateInternals;

/** NMS 블록 상태 shim(N2b-6)의 서버 다리 — 상태 속성 표를 서버마다 한 번 꺼내 둔다(시험은 한 JVM 에서 서버를 여러 번 띄운다). */
public final class NmsBlockStates {

    private NmsBlockStates() {}

    private record Cached(org.domi.furnace.api.Furnace server, BlockStateInternals bridge, BlockStateInternals.Table table) {}

    private static volatile Cached cached;

    private static Cached cache() {
        var server = org.domi.furnace.api.Furnace.server();
        Cached c = cached;
        if (c == null || c.server != server) {
            BlockStateInternals b = server.internals().blockStates();
            cached = c = new Cached(server, b, b.table());
        }
        return c;
    }

    public static BlockStateInternals bridge() { return cache().bridge; }

    /** 모든 상태의 속성 표(상태 번호로 인덱스). */
    public static BlockStateInternals.Table table() { return cache().table; }
}
