// C→S 로그인 시작. 플레이어 이름과 (클라가 보낸) UUID를 담는다. 오프라인 UUID 유틸 포함.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;

public record LoginStart(String name, UUID uuid) {

    public static final int ID = 0x00;

    public static LoginStart read(ByteBuf buf) {
        String name = Types.readString(buf, 16);
        UUID uuid = Types.readUuid(buf);
        return new LoginStart(name, uuid);
    }

    /** 오프라인 모드 UUID: "OfflinePlayer:<name>"의 MD5(name-based v3). */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
