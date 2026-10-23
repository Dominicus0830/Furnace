// 바닐라 IpBanListEntry (banned-ips.json 항목).
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import java.util.Date;

public final class IpBanListEntry extends BanListEntry<String> {

    public IpBanListEntry(String ip) { this(ip, null, null, null, null); }

    public IpBanListEntry(String ip, Date created, String source, Date expires, String reason) {
        super(ip, created, source, expires, reason);
    }

    public IpBanListEntry(JsonObject json) { super(json.has("ip") ? json.get("ip").getAsString() : null, json); }

    @Override
    protected void serialize(JsonObject json) {
        if (getUser() == null) return;
        json.addProperty("ip", getUser());
        super.serialize(json);
    }

    @Override
    public String getDisplayName() { return String.valueOf(getUser()); }
}
