// 바닐라 StoredUserEntry — 사용자 목록 JSON 한 항목.
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import java.util.UUID;

public abstract class StoredUserEntry<T> {

    private final T user;

    protected StoredUserEntry(T user) {
        this.user = user;
    }

    public T getUser() { return user; }

    /** 기한이 지났는가(밴 항목만 의미가 있다). */
    boolean hasExpired() { return false; }

    protected abstract void serialize(JsonObject json);

    /** uuid·name 이 모두 있고 uuid 가 올바를 때만 프로필, 아니면 null. */
    static GameProfile profileFromJson(JsonObject json) {
        if (!json.has("uuid") || !json.has("name")) return null;
        UUID id;
        try {
            id = UUID.fromString(json.get("uuid").getAsString());
        } catch (Throwable t) {
            return null;
        }
        return new GameProfile(id, json.get("name").getAsString());
    }
}
