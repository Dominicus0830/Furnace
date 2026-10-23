// 바닐라 ServerOpListEntry (ops.json 항목).
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;

public final class ServerOpListEntry extends StoredUserEntry<GameProfile> {

    private final int level;
    private final boolean bypassesPlayerLimit;

    public ServerOpListEntry(GameProfile user, int level, boolean bypassesPlayerLimit) {
        super(user);
        this.level = level;
        this.bypassesPlayerLimit = bypassesPlayerLimit;
    }

    public ServerOpListEntry(JsonObject json) {
        super(profileFromJson(json));
        this.level = json.has("level") ? json.get("level").getAsInt() : 0;
        this.bypassesPlayerLimit = json.has("bypassesPlayerLimit") && json.get("bypassesPlayerLimit").getAsBoolean();
    }

    public int getLevel() { return level; }

    public boolean getBypassesPlayerLimit() { return bypassesPlayerLimit; }

    @Override
    protected void serialize(JsonObject json) {
        if (getUser() == null) return;
        json.addProperty("uuid", getUser().id().toString());
        json.addProperty("name", getUser().name());
        json.addProperty("level", level);
        json.addProperty("bypassesPlayerLimit", bypassesPlayerLimit);
    }
}
