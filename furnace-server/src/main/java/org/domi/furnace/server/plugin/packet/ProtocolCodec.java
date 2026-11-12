// 버전별 변환 계층 — 버전 중립 PacketType·값과 그 마인크래프트 버전의 패킷 id·와이어 형식을 오간다.
// 새 버전을 지원할 때는 이 인터페이스의 구현을 하나 더 만든다(API 타입은 그대로 둔다).
package org.domi.furnace.server.plugin.packet;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketType;

public interface ProtocolCodec {

    int protocolVersion();

    /** play 단계 서버→클라이언트 id 의 종류(모르는 id 면 null). */
    PacketType<?> clientboundType(int id);

    /** play 단계 클라이언트→서버 id 의 종류(모르는 id 면 null). */
    PacketType<?> serverboundType(int id);

    /** 그 종류가 이 버전에서 쓰는 id 들(없으면 빈 배열). 이동 패킷처럼 하나의 종류가 여러 id 일 수 있다. */
    int[] ids(PacketType<?> type);

    /** 본문을 읽어 값으로(타입 래퍼가 없는 종류는 OpaquePacket). body 의 읽기 위치는 옮겨진다. */
    Packet decode(PacketType<?> type, int id, ByteBuf body);

    /** 값을 이 버전의 와이어로: 본문은 out 에 쓰고 id 를 돌려준다. 보낼 수 없는 값이면 IllegalArgumentException. */
    int encode(Packet packet, ByteBuf out);

    /** 이름 있는 메타데이터 칸의 이 버전 칸 번호(없으면 empty). */
    java.util.OptionalInt entityDataIndex(org.domi.furnace.api.entity.data.EntityDataKey<?> key);

    /** 그 엔티티 종류에 이 칸이 있는가(같은 번호·같은 값 종류). */
    boolean hasEntityData(String entityType, org.domi.furnace.api.entity.data.EntityDataKey<?> key);
}
