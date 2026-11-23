// 연결에 붙은 패킷 가로채기 지점(PacketTap)을 부르는 한 곳. 리스너가 도는 동안 같은 연결로 보낸 패킷의 순서도 여기서 맞춘다.
// 플러그인 패킷 API·ProtocolLib 다리는 PacketTap 을 꽂기만 하고, 보내는 쪽(SharedChunkFrames·Player)은 이 클래스만 부른다.
package org.domi.furnace.server.net;

import io.netty.channel.Channel;
import org.domi.furnace.server.i18n.Lang;
import org.domi.furnace.server.net.protocol.Packet;

public final class PacketTaps {

    private PacketTaps() {}

    /**
     * 채널 이벤트 루프에서 부른다. 그 연결에 가로채기 지점이 있으면 거친다 — 버리면 packet 을 discard 하고 null, 아니면 보낼 패킷(그대로거나 바뀐 것).
     * 가로채기 지점이 없으면 packet 그대로.
     */
    public static Packet outbound(Channel ch, Packet packet) {
        PacketTap tap = ch.attr(PacketTap.KEY).get();
        if (tap == null) return packet;
        Packet out;
        Nested outer = NESTED.get();
        Nested mine = new Nested(ch);
        NESTED.set(mine);
        try {
            out = tap.outbound(packet);
        } finally {
            NESTED.set(outer);
        }
        mine.writeAll();   // 리스너 안에서 이 연결로 보낸 것은 지금 패킷(묶음이면 그 묶음) 앞에
        if (out == null) packet.discard();
        return out;
    }

    // ---- 리스너 안에서 보낸 패킷 (ProtocolLib 2b-2 검토 R2) ----
    // 패킷 리스너(플러그인·ProtocolLib 다리)가 그 연결로 다시 보내면 예전엔 이벤트 루프 큐 맨 뒤(지금 쓰는 패킷·묶음과 그 뒤 작업들 다음)로 갔다.
    // Paper+ProtocolLib 에서는 리스너가 바깥 쓰기 작업 안에서 돌고, 안에서 보낸 것은 먼저 실제 루프로 나간다 — 그래서 "스폰을 취소하고 바꾼 것을
    // 다시 보내기"가 엔티티 → 메타데이터 순서로 도착한다. 같은 순서가 되게, 리스너가 도는 동안 같은 연결의 루프 작업을 모았다가 지금 패킷을 쓰기 전에 돌린다.

    private static final ThreadLocal<Nested> NESTED = new ThreadLocal<>();

    private static final class Nested {
        final Channel channel;
        java.util.List<Runnable> tasks;

        Nested(Channel channel) { this.channel = channel; }

        void writeAll() {
            if (tasks == null) return;
            for (Runnable t : tasks) {
                try {
                    t.run();
                } catch (RuntimeException e) {   // 루프 작업 하나가 던진 것처럼 — 바깥 쓰기는 계속
                    org.slf4j.LoggerFactory.getLogger(PacketTaps.class).warn(Lang.t("net.listener-task-failed"), e);
                }
            }
            tasks = null;
        }
    }

    /**
     * 그 연결의 이벤트 루프에서, 그 연결 패킷 리스너가 도는 중이면 작업을 모아 두고 true(지금 패킷 앞에 쓴다). 아니면 false — 부른 쪽이 루프에 넣는다.
     */
    public static boolean deferInsideListener(Channel ch, Runnable task) {
        Nested n = NESTED.get();
        if (n == null || n.channel != ch || !ch.eventLoop().inEventLoop()) return false;
        if (n.tasks == null) n.tasks = new java.util.ArrayList<>(2);
        n.tasks.add(task);
        return true;
    }
}
