// Furnace.access(): ops/밴/IP 밴/화이트리스트를 PlayerLists 위에서 다룬다(바닐라 명령과 같은 부수 효과).
package org.domi.furnace.server.plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.access.AccessLists;
import org.domi.furnace.api.access.BanEntry;
import org.domi.furnace.api.access.IpBanEntry;
import org.domi.furnace.api.entity.OfflinePlayer;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.builtin.PlayerListCommands;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.players.IpBanListEntry;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.UserBanListEntry;

final class AccessListsImpl implements AccessLists {

    private final PluginHost host;

    AccessListsImpl(PluginHost host) { this.host = host; }

    private ServerCore core() { return host.core(); }

    private PlayerLists lists() { return core().lists(); }

    static BanEntry convert(UserBanListEntry e) {
        return new BanEntry(e.getUser().id(), e.getUser().name(), e.getSource(), e.getCreated().toInstant(),
                e.getExpires() == null ? null : e.getExpires().toInstant(), e.getReason());
    }

    @Override
    public boolean isWhitelistEnabled() { return lists().isUsingWhitelist(); }

    @Override
    public void setWhitelistEnabled(boolean enabled) {
        lists().setUsingWhiteList(enabled);
        if (enabled) PlayerListCommands.kickUnlistedPlayers(core());
    }

    @Override
    public void reloadWhitelist() {
        lists().reloadWhiteList();
        PlayerListCommands.kickUnlistedPlayers(core());
    }

    @Override
    public List<OfflinePlayer> whitelisted() {
        List<OfflinePlayer> out = new ArrayList<>();
        for (var e : lists().getWhiteList().getEntries()) out.add(new OfflinePlayerImpl(host, e.getUser().id()));
        return List.copyOf(out);
    }

    @Override
    public List<OfflinePlayer> operators() {
        List<OfflinePlayer> out = new ArrayList<>();
        for (var e : lists().getOps().getEntries()) out.add(new OfflinePlayerImpl(host, e.getUser().id()));
        return List.copyOf(out);
    }

    @Override
    public List<BanEntry> bannedPlayers() {
        List<BanEntry> out = new ArrayList<>();
        for (var e : lists().getBans().getEntries()) out.add(convert(e));
        return List.copyOf(out);
    }

    @Override
    public List<IpBanEntry> bannedIps() {
        List<IpBanEntry> out = new ArrayList<>();
        for (var e : lists().getIpBans().getEntries()) {
            out.add(new IpBanEntry(e.getUser(), e.getSource(), e.getCreated().toInstant(),
                    e.getExpires() == null ? null : e.getExpires().toInstant(), e.getReason()));
        }
        return List.copyOf(out);
    }

    @Override
    public boolean isIpBanned(String ip) { return lists().getIpBans().isBanned(ip); }

    @Override
    public void banIp(String ip, String reason, Instant expires, String source) {
        java.util.Objects.requireNonNull(ip, "ip");
        if (!PlayerListCommands.IP_ADDRESS_PATTERN.matcher(ip).matches()) throw new IllegalArgumentException("IP 주소가 아니다: " + ip);
        lists().getIpBans().add(new IpBanListEntry(ip, null, source, expires == null ? null : java.util.Date.from(expires), reason));
        for (Player p : core().players()) {
            if (p.ipAddress().equals(ip)) p.kick(new Disconnect(Text.translatable("multiplayer.disconnect.ip_banned")));
        }
    }

    @Override
    public boolean unbanIp(String ip) {
        if (!lists().getIpBans().isBanned(ip)) return false;
        lists().getIpBans().remove(ip);
        return true;
    }
}
