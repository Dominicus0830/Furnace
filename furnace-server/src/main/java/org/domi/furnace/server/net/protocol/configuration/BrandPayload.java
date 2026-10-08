// S→C 구성 단계 커스텀 페이로드(0x01) 중 minecraft:brand. 클라이언트 F3 화면에 서버 브랜드로 표시된다.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record BrandPayload(String brand) implements Packet {

    public static final int ID = 0x01;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, "minecraft:brand");
        Types.writeString(buf, brand);
    }
}
