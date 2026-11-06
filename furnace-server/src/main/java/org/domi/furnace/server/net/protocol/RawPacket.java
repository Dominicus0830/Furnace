// 이미 인코딩된 본문을 그대로 보내는 패킷 — 플러그인 패킷 API 가 만든다(버전 변환 계층이 id·본문을 정했다).
// source 는 플러그인이 넘긴 원래 값(리스너가 다시 디코드하지 않게), silent 면 플러그인 리스너를 거치지 않는다.
package org.domi.furnace.server.net.protocol;

import io.netty.buffer.ByteBuf;

public record RawPacket(int id, byte[] body, Object source, boolean silent) implements Packet {

    @Override
    public void write(ByteBuf buf) { buf.writeBytes(body); }
}
