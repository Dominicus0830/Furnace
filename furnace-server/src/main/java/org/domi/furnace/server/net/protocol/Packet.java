// 서버→클라 패킷의 공통 형태. id와 body 직렬화를 노출한다.
package org.domi.furnace.server.net.protocol;

import io.netty.buffer.ByteBuf;

public interface Packet {

    /** 이 패킷의 프로토콜 id(해당 단계 기준). */
    int id();

    /** 패킷 body를 buf에 기록(id는 ConnectionHandler가 앞에 씀). */
    void write(ByteBuf buf);
}
