// 바닐라 DedicatedPlayerList 의 목록 부분 — 기동 시 바닐라와 같은 순서로 밴·OP·화이트리스트를 읽고 다시 쓴다.
// 로그인 게이트(밴/화이트리스트/인원)와 OP 판정이 여기 목록을 본다.
package org.domi.furnace.server.players;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerLists {

    private static final Logger log = LoggerFactory.getLogger(PlayerLists.class);

    private final UserBanList bans;
    private final IpBanList ipBans;
    private final ServerOpList ops;
    private final UserWhiteList whitelist;
    private final ServerPropertiesFile settings;
    private volatile boolean doWhiteList;

    /** dir: 서버 작업 디렉터리(바닐라는 현재 디렉터리 기준 상대 경로). */
    public PlayerLists(Path dir, ServerPropertiesFile settings) {
        this.settings = settings;
        this.bans = new UserBanList(dir.resolve("banned-players.json"));
        this.ipBans = new IpBanList(dir.resolve("banned-ips.json"));
        this.ops = new ServerOpList(dir.resolve("ops.json"));
        this.whitelist = new UserWhiteList(dir.resolve("whitelist.json"));
        this.doWhiteList = settings.get().whiteList.get();
        try { bans.load(); } catch (IOException e) { log.warn("Failed to load user banlist: ", e); }
        try { bans.save(); } catch (IOException e) { log.warn("Failed to save user banlist: ", e); }
        try { ipBans.load(); } catch (IOException e) { log.warn("Failed to load ip banlist: ", e); }
        try { ipBans.save(); } catch (IOException e) { log.warn("Failed to save ip banlist: ", e); }
        try { ops.load(); } catch (Exception e) { log.warn("Failed to load operators list: ", e); }
        reloadWhiteList();
        saveOps();
        if (!Files.exists(whitelist.getFile())) {
            try { whitelist.save(); } catch (Exception e) { log.warn("Failed to save white-list: ", e); }
        }
    }

    public UserBanList getBans() { return bans; }

    public IpBanList getIpBans() { return ipBans; }

    public ServerOpList getOps() { return ops; }

    public UserWhiteList getWhiteList() { return whitelist; }

    public boolean isUsingWhitelist() { return doWhiteList; }

    /** /whitelist on|off — server.properties 의 white-list 도 갱신해 저장한다. */
    public void setUsingWhiteList(boolean v) {
        doWhiteList = v;
        settings.update(p -> p.whiteList.with(v));
    }

    public void reloadWhiteList() {
        try { whitelist.load(); } catch (Exception e) { log.warn("Failed to load white-list: ", e); }
    }

    private void saveOps() {
        try { ops.save(); } catch (Exception e) { log.warn("Failed to save operators list: ", e); }
    }

    public void op(GameProfile profile, int level) {
        ops.add(new ServerOpListEntry(profile, level, ops.canBypassPlayerLimit(profile)));
        saveOps();
    }

    public void deop(GameProfile profile) {
        ops.remove(profile);
        saveOps();
    }

    public boolean isOp(GameProfile profile) { return ops.isListed(profile); }

    /** OP 레벨(목록에 없으면 0). */
    public int opLevel(GameProfile profile) {
        ServerOpListEntry e = ops.get(profile);
        return e != null ? e.getLevel() : 0;
    }

    private static final ThreadLocal<java.text.SimpleDateFormat> BAN_DATE_FORMAT =
            ThreadLocal.withInitial(() -> new java.text.SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z"));

    /**
     * 바닐라 PlayerList.canPlayerLogin — 밴 → 화이트리스트 → IP 밴 → 인원 순으로 검사하고, 막히면 사유(번역 컴포넌트), 통과면 null.
     */
    public org.domi.furnace.server.net.protocol.Text canPlayerLogin(java.net.SocketAddress address, GameProfile profile,
                                                                    int onlinePlayers, int maxPlayers) {
        UserBanListEntry ban = bans.isListed(profile) ? bans.get(profile) : null;
        if (ban != null) {
            var t = org.domi.furnace.server.net.protocol.Text.translatable("multiplayer.disconnect.banned.reason", ban.getReason());
            if (ban.getExpires() != null) {
                t.append(org.domi.furnace.server.net.protocol.Text.translatable("multiplayer.disconnect.banned.expiration",
                        BAN_DATE_FORMAT.get().format(ban.getExpires())));
            }
            return t;
        }
        if (!isWhiteListed(profile)) {
            return org.domi.furnace.server.net.protocol.Text.translatable("multiplayer.disconnect.not_whitelisted");
        }
        IpBanListEntry ipBan = address != null && ipBans.isBanned(address) ? ipBans.get(address) : null;
        if (ipBan != null) {
            var t = org.domi.furnace.server.net.protocol.Text.translatable("multiplayer.disconnect.banned_ip.reason", ipBan.getReason());
            if (ipBan.getExpires() != null) {
                t.append(org.domi.furnace.server.net.protocol.Text.translatable("multiplayer.disconnect.banned_ip.expiration",
                        BAN_DATE_FORMAT.get().format(ipBan.getExpires())));
            }
            return t;
        }
        if (onlinePlayers >= maxPlayers && !ops.canBypassPlayerLimit(profile)) {
            return org.domi.furnace.server.net.protocol.Text.translatable("multiplayer.disconnect.server_full");
        }
        return null;
    }

    /** 화이트리스트를 쓰지 않거나, OP 이거나, 목록에 있으면 참. */
    public boolean isWhiteListed(GameProfile profile) {
        return !doWhiteList || ops.isListed(profile) || whitelist.isListed(profile);
    }
}
