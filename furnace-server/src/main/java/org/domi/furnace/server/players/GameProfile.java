// 플레이어 프로필(authlib GameProfile 대응). properties 는 세션 서버가 준 textures 등 서명된 속성.
package org.domi.furnace.server.players;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public record GameProfile(UUID id, String name, List<Property> properties) {

    public record Property(String name, String value, String signature) {}

    public GameProfile(UUID id, String name) {
        this(id, name, List.of());
    }

    /** UUIDUtil.createOfflinePlayerUUID: "OfflinePlayer:<name>" 의 name-based(v3) UUID. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static GameProfile offline(String name) {
        return new GameProfile(offlineUuid(name), name);
    }

    /** StringUtil.isValidPlayerName: 16자 이하, 모든 문자가 공백(32) 초과 127 미만. */
    public static boolean isValidPlayerName(String name) {
        if (name.length() > 16) return false;
        return name.chars().noneMatch(c -> c <= 32 || c >= 127);
    }
}
