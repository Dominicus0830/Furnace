package org.domi.furnace.server.plugin.packet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.schema.ProtocolIds;
import org.junit.jupiter.api.Test;

/**
 * 호환 계층 패킷 등록부 shim 이 쓰는 번호 표(ProtocolIds)가 서버 자신의 play 번호 표(Protocol767)와 같은 번호를 말하는지.
 * 두 표는 따로 바닐라 바이트코드에서 확인된 것이라, 한쪽이 어긋나면 여기서 잡힌다.
 */
class ProtocolIdsMatchServerTableTest {

    private static String[] game(PacketDirection d) {
        return ProtocolIds.protocol767().entries("game", d).stream().map(ProtocolIds.Entry::wireName).toArray(String[]::new);
    }

    @Test
    void gameClientboundIdsEqualTheServerCodecTable() { assertArrayEquals(Protocol767.CLIENTBOUND, game(PacketDirection.CLIENTBOUND)); }

    @Test
    void gameServerboundIdsEqualTheServerCodecTable() { assertArrayEquals(Protocol767.SERVERBOUND, game(PacketDirection.SERVERBOUND)); }
}
