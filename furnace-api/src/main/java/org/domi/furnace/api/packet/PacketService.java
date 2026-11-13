// 패킷 API 입구 — Furnace.server().packets(). 보내기·받은 것처럼 넣기·가로채기(관찰·수정·취소)를 버전 중립 타입으로 한다.
//
// 스레드: 모든 메서드는 어느 스레드에서 불러도 된다. 리스너는 그 플레이어 연결의 네트워크 스레드에서, 패킷 순서대로 불린다
// (한 플레이어의 패킷은 늘 같은 스레드라 리스너 안에서 그 플레이어 상태를 잠금 없이 다뤄도 순서가 섞이지 않는다).
// 나가는 패킷 리스너 안에서 같은 플레이어에게 보낸 것은 지금 가로챈 패킷(묶음 안이면 그 묶음)보다 먼저 나간다(Paper+ProtocolLib 과 같은 순서).
// 범위: play 단계 패킷만 다룬다(로그인·구성 단계는 아니다). 구독자가 없는 종류는 서버가 아무 비용도 쓰지 않는다.
//
// 예)
//   packets.listen(this, PacketTypes.Serverbound.INTERACT, e -> {
//       if (e.packet().entityId() == npcId) { e.setCancelled(true); onNpcClick(e.player()); }
//   });
//   player.sendPacket(new ClientboundBlockUpdatePacket(pos, BlockData.of("minecraft:diamond_block")));
package org.domi.furnace.api.packet;

import java.util.Collection;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface PacketService {

    /** NORMAL 우선순위로 그 종류를 가로챈다. 플러그인이 꺼지면 자동으로 풀린다. */
    default <P extends Packet> EventSubscription listen(FurnacePlugin owner, PacketType<P> type, PacketListener<P> listener) {
        return listen(owner, type, EventPriority.NORMAL, listener);
    }

    /** 우선순위를 정해 가로챈다(LOWEST 먼저, MONITOR 마지막 — MONITOR 는 관찰만). */
    <P extends Packet> EventSubscription listen(FurnacePlugin owner, PacketType<P> type, EventPriority priority, PacketListener<P> listener);

    /** 여러 종류를 한 리스너로. setPacket 은 원래 종류와 같은 값만 받는다. */
    EventSubscription listen(FurnacePlugin owner, EventPriority priority, PacketListener<Packet> listener, PacketType<?>... types);

    /** 이 플레이어에게 보낸다. 다른 플러그인의 리스너도 이 패킷을 본다(isPluginPacket = true). 접속이 끊겼으면 아무 일도 없다. */
    void send(Player player, ClientboundPacket packet);

    /** 리스너를 거치지 않고 보낸다. */
    void sendSilently(Player player, ClientboundPacket packet);

    /**
     * 여러 패킷을 번들 하나로 보낸다 — 클라이언트가 한 프레임에 함께 처리한다(바닐라 bundle_delimiter, 서버가 엔티티를 보여 줄 때 쓰는 것과 같다).
     * 엔티티 생성 + 메타데이터 + 장비를 따로 보내면 한 프레임 동안 맨몸·기본 모습이 보이는데, 번들로 보내면 처음부터 다 갖춘 모습으로 나타난다.
     * 다른 스레드가 보낸 패킷이 사이에 끼지 않는다. 리스너는 안의 패킷을 하나씩 본다(취소하면 그것만 빠진다).
     * 모두 먼저 인코딩해 보고 하나라도 못 하면 아무것도 보내지 않고 IllegalArgumentException. 최대 {@value #BUNDLE_MAX} 개(바닐라 클라이언트 상한).
     * 예) packets.sendBundle(player, List.of(addEntity, entityData, equipment));
     */
    void sendBundle(Player player, java.util.List<? extends ClientboundPacket> packets);

    /** sendBundle 과 같되 리스너를 거치지 않는다. */
    void sendBundleSilently(Player player, java.util.List<? extends ClientboundPacket> packets);

    /** 번들 하나에 넣을 수 있는 최대 패킷 수(바닐라 클라이언트가 이보다 많으면 연결을 끊는다). */
    int BUNDLE_MAX = 4096;

    /** 접속한 모든 플레이어에게 보낸다(바이트는 한 번만 만든다). */
    void broadcast(ClientboundPacket packet);

    /**
     * 그 플레이어의 클라이언트가 보낸 것처럼 서버에 넣는다(리스너를 거친다). 서버는 진짜 패킷과 똑같이 검사하고 처리한다.
     * 그 연결의 네트워크 스레드에서 차례대로 처리된다.
     */
    void receive(Player player, ServerboundPacket packet);

    /** 리스너를 거치지 않고 넣는다. */
    void receiveSilently(Player player, ServerboundPacket packet);

    /** 실행 중인 서버 버전에 이 패킷이 있는가(PacketTypes 는 여러 버전을 합친 목록이다). */
    boolean isSupported(PacketType<?> type);

    /** 이 서버 버전에서 다루는 모든 종류. */
    Collection<PacketType<?>> supportedTypes(PacketDirection direction);

    /** 서버 엔티티와 겹치지 않는 새 엔티티 번호 — 가상 엔티티(ClientboundAddEntityPacket)에 쓴다. */
    int nextEntityId();

    /**
     * 이름 있는 메타데이터 칸의 이 서버 버전 칸 번호(없는 칸이면 empty). 보통은 직접 부르지 않는다 —
     * ClientboundSetEntityDataPacket.get/with·VirtualEntity.set 이 이것으로 번호를 정한다.
     */
    java.util.OptionalInt entityDataIndex(org.domi.furnace.api.entity.data.EntityDataKey<?> key);

    /**
     * 그 엔티티 종류("minecraft:text_display")에 이 칸이 있는가 — 칸의 주인 부류(key.owner())가 그 종류의 바닐라 계층에 있고 번호·값 종류가 맞을 때.
     * 예) interaction 에 LivingEntity.HEALTH 는 false(9번이 height 라 번호·종류는 맞지만 살아 있는 엔티티가 아니다). 없는 칸을 보내면 클라이언트가 연결을 끊으므로
     * 숫자 칸으로 직접 패킷을 만들 때 확인용. 이 서버가 모르는 종류면 false.
     */
    boolean hasEntityData(String entityType, org.domi.furnace.api.entity.data.EntityDataKey<?> key);

    /** 클라이언트 프로토콜 번호(1.21.1 = 767). 버전별 분기가 꼭 필요할 때만 쓴다. */
    int protocolVersion();

    /**
     * 이 서버 버전에서 그 종류의 내용을 읽고 바꾸고 보낼 수 있는가. 타입 레코드 종류는 늘 true, GenericPacket 종류는 서버의 범용 코덱이
     * 그 와이어를 아는 것만 true(나머지는 리스너가 내용 없는 GenericPacket 을 받아 관찰·취소만 한다).
     */
    boolean isReadable(PacketType<?> type);

    /** GenericPacket 종류의 필드(이름 + 값 종류, 바닐라 순서). 타입 레코드 종류이거나 읽을 수 없는 종류면 빈 목록. */
    java.util.List<PacketField<?>> fields(PacketType<?> type);
}
