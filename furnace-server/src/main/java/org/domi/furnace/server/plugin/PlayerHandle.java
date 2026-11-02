// 플러그인이 보는 플레이어 — 서버 Player 를 감싼다. 상태 변경은 스레드 안전한 PlayerVitals·패킷 전송으로 간다.
package org.domi.furnace.server.plugin;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;
import org.domi.furnace.api.audience.Title;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.permission.PermissionAttachment;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.ClearTitles;
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.net.protocol.play.TabListHeaderFooter;
import org.domi.furnace.server.net.protocol.play.TextOnlyPacket;
import org.domi.furnace.server.net.protocol.play.TitleTimes;

public final class PlayerHandle implements org.domi.furnace.api.entity.Player {

    private final Player p;
    private final PluginHost host;
    private final PermissibleBase perms;

    PlayerHandle(Player p, PluginHost host) {
        this.p = p;
        this.host = host;
        this.perms = new PermissibleBase(host.permissionsImpl(), this::isOp);
    }

    /** 서버 내부 플레이어. */
    public Player handle() { return p; }

    PermissibleBase perms() { return perms; }

    private ServerCore core() { return host.core(); }

    @Override
    public String name() { return p.name(); }

    @Override
    public UUID uniqueId() { return p.uuid(); }

    @Override
    public Text displayName() { return p.displayName(); }

    @Override
    public boolean isOnline() { return !p.isDisconnected(); }

    @Override
    public Location location() { return new Location("minecraft:overworld", p.x(), p.y(), p.z(), p.yaw(), p.pitch()); }

    @Override
    public GameMode gameMode() { return GameMode.byId(p.gameMode().id()); }

    @Override
    public boolean setGameMode(GameMode mode) {
        return p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.byId(mode.id()));
    }

    @Override
    public float health() { return p.vitals().health(); }

    @Override
    public void setHealth(float health) { p.vitals().setHealth(health); }

    @Override
    public int foodLevel() { return p.vitals().foodLevel(); }

    @Override
    public void setFoodLevel(int food) { p.vitals().setFoodLevel(food); }

    @Override
    public int level() { return p.vitals().xpLevel(); }

    @Override
    public void setLevel(int level) { p.vitals().setExperienceLevels(level); }

    @Override
    public void giveExperience(int points) { p.vitals().giveExperiencePoints(points); }

    @Override
    public boolean isDead() { return p.vitals().isDeadOrDying(); }

    @Override
    public Locale locale() { return p.javaLocale(); }

    @Override
    public int ping() { return p.latencyMs(); }

    @Override
    public InetSocketAddress address() {
        return p.remoteAddress() instanceof InetSocketAddress a ? a : null;
    }

    @Override
    public void kick(Text reason) { p.kick(new Disconnect(reason)); }

    @Override
    public String clientBrand() { return p.clientBrand(); }

    @Override
    public void sendPluginMessage(String channel, byte[] data) {
        if (org.domi.furnace.server.data.ResourceId.tryParse(channel) == null) throw new IllegalArgumentException("잘못된 채널 이름: " + channel);
        p.send(new org.domi.furnace.server.net.protocol.play.CustomPayloadPacket(channel, data.clone(), true));
    }

    @Override
    public boolean performCommand(String command) { return host.commandsImpl().dispatch(this, command); }

    // ------------------------------------------------------------------ Audience

    @Override
    public void sendMessage(Text message) { p.sendSystemMessage(message, false); }

    @Override
    public void sendActionBar(Text message) { p.send(new TextOnlyPacket(TextOnlyPacket.ACTION_BAR, p.localize(message))); }

    @Override
    public void showTitle(Title title) {
        // Adventure/Paper 와 같은 순서: 시간 → 부제 → 제목(제목이 와야 화면에 뜬다).
        if (title.times() != null) p.send(new TitleTimes(title.times().fadeInTicks(), title.times().stayTicks(), title.times().fadeOutTicks()));
        p.send(new TextOnlyPacket(TextOnlyPacket.SUBTITLE, p.localize(title.subtitle())));
        p.send(new TextOnlyPacket(TextOnlyPacket.TITLE, p.localize(title.title())));
    }

    @Override
    public void clearTitle() { p.send(new ClearTitles(false)); }

    @Override
    public void resetTitle() { p.send(new ClearTitles(true)); }

    @Override
    public void sendPlayerListHeaderAndFooter(Text header, Text footer) { p.send(new TabListHeaderFooter(p.localize(header), p.localize(footer))); }

    // ------------------------------------------------------------------ 권한

    @Override
    public boolean isOp() { return core().lists().isOp(ServerCore.profileOf(p)); }

    @Override
    public boolean hasPermission(String node) { return perms.hasPermission(node); }

    @Override
    public boolean isPermissionSet(String node) { return perms.isPermissionSet(node); }

    @Override
    public PermissionAttachment addAttachment(FurnacePlugin plugin) { return perms.addAttachment(plugin); }

    @Override
    public boolean equals(Object o) { return o instanceof PlayerHandle h && h.p == p; }

    @Override
    public int hashCode() { return p.hashCode(); }

    @Override
    public String toString() { return "Player{" + p.name() + "}"; }
}
