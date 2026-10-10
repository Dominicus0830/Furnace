// 바닐라 UserBanListEntry (banned-players.json 항목).
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import java.util.Date;

public final class UserBanListEntry extends BanListEntry<GameProfile> {

    public UserBanListEntry(GameProfile user) { this(user, null, null, null, null); }

    public UserBanListEntry(GameProfile user, Date created, String source, Date expires, String reason) {
        super(user, created, source, expires, reason);
    }

    public UserBanListEntry(JsonObject json) { super(profileFromJson(json), json); }

    @Override
    protected void serialize(JsonObject json) {
        if (getUser() == null) return;
        json.addProperty("uuid", getUser().id().toString());
        json.addProperty("name", getUser().name());
        super.serialize(json);
    }

    /** 이름이 없으면 바닐라는 번역 키 commands.banlist.entry.unknown 을 쓴다. */
    @Override
    public String getDisplayName() { return getUser() != null ? getUser().name() : null; }
}
