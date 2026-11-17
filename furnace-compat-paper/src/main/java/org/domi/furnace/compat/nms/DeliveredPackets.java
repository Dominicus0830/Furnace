package org.domi.furnace.compat.nms;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.packet.PacketType;

/**
 * 패킷 라이브러리를 이미 지난 패킷 표지(ProtocolLib 다리 2b-2). {@link PacketDelivery} 가 shim 에서 바꿔 내보내는 Furnace 패킷(플러그인이 NMS·ProtocolLib 로
 * 보낸 것 — 라이브러리 리스너가 이미 봤다)을 객체 그대로 표시하고, 다리의 Furnace 패킷 리스너가 그것을 꺼내 한 번 더 보이지 않는다(무한 되돌기 방지).
 * 다리가 듣는 종류({@link #watch})만 표시한다. 리스너에 닿지 못한 표지(앞선 Furnace 리스너가 취소·교체)는 주기적으로 버린다({@link #prune}).
 */
public final class DeliveredPackets {

    private DeliveredPackets() {}

    private static volatile Set<PacketType<?>> watched = Set.of();

    /** 객체 그대로(equals 가 아니라 ==) 비교하는 열쇠. */
    private record Key(Object o) {
        @Override
        public boolean equals(Object x) { return x instanceof Key k && k.o == o; }

        @Override
        public int hashCode() { return System.identityHashCode(o); }
    }

    private static final Map<Key, Long> MARKS = new ConcurrentHashMap<>();

    /** 다리가 듣는 종류를 바꾼다(빈 집합 = 다리 꺼짐, 표지도 비운다). */
    public static void watch(Set<PacketType<?>> types) {
        watched = Set.copyOf(types);
        if (types.isEmpty()) MARKS.clear();
    }

    static void mark(org.domi.furnace.api.packet.ClientboundPacket p) {
        if (watched.contains(p.type())) MARKS.put(new Key(p), System.nanoTime());
    }

    /** 표시된 패킷이면 표지를 떼고 true. */
    public static boolean take(Object p) { return !MARKS.isEmpty() && MARKS.remove(new Key(p)) != null; }

    /** 이만큼(나노초) 지난 표지를 버린다. */
    public static void prune(long olderThanNanos) {
        if (MARKS.isEmpty()) return;
        long now = System.nanoTime();
        MARKS.values().removeIf(t -> now - t > olderThanNanos);
    }

    /** 시험용: 남은 표지 수. */
    static int size() { return MARKS.size(); }
}
