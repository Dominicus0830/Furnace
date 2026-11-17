// 바닐라 UserWhiteListEntry (whitelist.json 항목).
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.server.players.UserWhiteListEntry")
public final class UserWhiteListEntry extends StoredUserEntry<GameProfile> {

    public UserWhiteListEntry(GameProfile user) { super(user); }

    public UserWhiteListEntry(JsonObject json) { super(profileFromJson(json)); }

    @Override
    protected void serialize(JsonObject json) {
        if (getUser() == null) return;
        json.addProperty("uuid", getUser().id() == null ? "" : getUser().id().toString());
        json.addProperty("name", getUser().name());
    }
}
