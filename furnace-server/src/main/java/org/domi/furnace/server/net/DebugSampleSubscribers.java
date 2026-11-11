// F3 디버그 표본 구독 — 바닐라 DebugSampleSubscriber 와 같은 결과: 구독을 요청한 OP 는 OP 인 동안(그리고 접속해 있는 동안) 틱마다 표본을 받는다.
// 바닐라는 요청 큐를 비우지 않아 매 틱 구독을 다시 넣으므로 만료가 없다. OP 가 풀리면 받지 못하지만 요청은 남아 있어, OP 를 다시 주면
// 새 요청 없이도 다시 받는다(나간 플레이어가 큐에 남는 바닐라의 누수는 따라 하지 않는다). 표본은 틱 하나당 한 번
// [FULL_TICK, TICK_SERVER_METHOD, SCHEDULED_TASKS, IDLE] 4칸(ns)이다. Furnace 는 전역 박동(scheduledTick)의 소요를 그 틱 시간으로 쓴다.
package org.domi.furnace.server.net;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.DebugSample;

public final class DebugSampleSubscribers {

    private static final long TICK_NANOS = 50_000_000L;

    private final Set<Player> subscribers = ConcurrentHashMap.newKeySet();
    private final Predicate<Player> isOp;

    public DebugSampleSubscribers(Predicate<Player> isOp) { this.isOp = isOp; }

    /** 구독 요청(호출하는 쪽이 OP 인지 이미 확인했다). */
    public void subscribe(Player p) { subscribers.add(p); }

    public boolean hasSubscribers() { return !subscribers.isEmpty(); }

    public int size() { return subscribers.size(); }

    /** 전역 박동 하나가 끝났을 때(전역 스레드): 나간 구독자는 빼고, 지금 OP 인 구독자에게 표본을 보낸다. */
    public void tick(long fullNanos, long tookNanos) {
        if (subscribers.isEmpty()) return;
        DebugSample packet = null;
        for (Iterator<Player> it = subscribers.iterator(); it.hasNext(); ) {
            Player p = it.next();
            if (p.isDisconnected()) { it.remove(); continue; }
            if (!isOp.test(p)) continue;
            if (packet == null) {
                packet = new DebugSample(new long[] {fullNanos, tookNanos, 0L, Math.max(0L, fullNanos - tookNanos)}, DebugSample.TICK_TIME);
            }
            p.send(packet);
        }
    }
}
