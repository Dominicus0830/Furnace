// 바닐라 IpBanList (banned-ips.json). 키는 IP 문자열.
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import java.net.SocketAddress;
import java.nio.file.Path;

public final class IpBanList extends StoredUserList<String, IpBanListEntry> {

    public IpBanList(Path file) { super(file); }

    @Override
    protected StoredUserEntry<String> createEntry(JsonObject json) { return new IpBanListEntry(json); }

    public boolean isBanned(SocketAddress address) { return contains(getIpFromAddress(address)); }

    public boolean isBanned(String ip) { return contains(ip); }

    public IpBanListEntry get(SocketAddress address) { return get(getIpFromAddress(address)); }

    /** SocketAddress.toString 에서 '/' 앞(호스트명)과 ':' 뒤(포트)를 떼어낸다. */
    public static String getIpFromAddress(SocketAddress address) {
        String s = address.toString();
        if (s.contains("/")) s = s.substring(s.indexOf('/') + 1);
        if (s.contains(":")) s = s.substring(0, s.indexOf(':'));
        return s;
    }
}
