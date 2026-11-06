// 패킷 리스너. 그 플레이어 연결의 네트워크 스레드에서 불린다 — 오래 걸리는 일(파일·DB·월드 변경)은 하지 말고
// 필요하면 Player.scheduler()·Furnace.scheduler()·asyncExecutor() 로 넘긴다. 예외는 기록하고 다음 리스너로 넘어간다.
package org.domi.furnace.api.packet;

@FunctionalInterface
public interface PacketListener<P extends Packet> {

    void onPacket(PacketEvent<P> event);
}
