// 클라이언트→서버 패킷. PacketService.receive 로 "클라이언트가 보낸 것처럼" 서버에 넣을 수 있다.
package org.domi.furnace.api.packet;

public interface ServerboundPacket extends Packet {
}
