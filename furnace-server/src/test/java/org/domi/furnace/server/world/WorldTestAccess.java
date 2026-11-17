// 다른 패키지 시험이 청크 구독 상태(package-private)를 다룰 때 쓰는 다리.
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.Player;

public final class WorldTestAccess {

    private WorldTestAccess() {}

    /** 그 플레이어가 그 청크를 이미 받은 것으로 구독한다. */
    public static void subscribeReady(World w, ChunkPos cp, Player p) { w.subscribeChunk(cp, p).ready = true; }

    /** 지금 구독이 받은 상태인가. */
    public static boolean isReady(World w, ChunkPos cp, Player p) {
        var s = w.subscriptionOf(cp, p);
        return s != null && s.ready;
    }
}
