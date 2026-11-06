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
    public Location location() { return new Location(p.dimension(), p.x(), p.y(), p.z(), p.yaw(), p.pitch()); }

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
    public void deleteChatMessage(byte[] signature) {
        if (signature == null || signature.length != org.domi.furnace.server.chat.MessageSignature.BYTES)
            throw new IllegalArgumentException("서명은 256바이트여야 한다");
        var packed = p.chat().pack(new org.domi.furnace.server.chat.MessageSignature(signature.clone()));
        p.send(new org.domi.furnace.server.net.protocol.play.DeleteChat(packed.id(), packed.full() == null ? null : packed.full().bytes()));
    }

    @Override
    public java.util.Set<String> listeningPluginChannels() { return p.pluginChannels().snapshot(); }

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

    // ------------------------------------------------------------------ Entity / LivingEntity

    @Override
    public float maxHealth() { return p.vitals().maxHealth(); }

    @Override
    public org.domi.furnace.api.sched.TaskScheduler scheduler() {
        var w = host.worldHandle(p.dimension());
        return w.handle().chunkScheduler(Math.floorDiv((int) Math.floor(p.x()), 16), Math.floorDiv((int) Math.floor(p.z()), 16));
    }

    /** 같은 차원 안만. 서버 텔레포트는 연결 스레드가 보정 패킷을 보내는 스레드 안전 경로다. */
    @Override
    public java.util.concurrent.CompletableFuture<Boolean> teleport(Location to) {
        if (!to.world().equals(p.dimension()) || p.isDisconnected()) return java.util.concurrent.CompletableFuture.completedFuture(false);
        if (!p.hasTeleporter()) return java.util.concurrent.CompletableFuture.completedFuture(false);
        p.teleportTo(to.x(), to.y(), to.z(), to.yaw(), to.pitch());
        return java.util.concurrent.CompletableFuture.completedFuture(true);
    }

    private volatile EntitySchedulerImpl entityScheduler;

    @Override
    public org.domi.furnace.api.entity.EntityScheduler entityScheduler() {
        EntitySchedulerImpl s = entityScheduler;
        if (s == null) entityScheduler = s = new EntitySchedulerImpl(p.apiTasks());
        return s;
    }

    /** 플레이어는 최근 값(지금 내용의 사본). */
    @Override
    public org.domi.furnace.api.persistence.PersistentDataView persistentData() {
        return p.withPersistentData(NbtPersistentData::snapshot);
    }

    /** 플레이어 소유 스레드에서(접속이 끊겼으면 false). */
    @Override
    public java.util.concurrent.CompletableFuture<Boolean> editPersistentData(java.util.function.Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        if (p.isDisconnected()) return RegionFuture.wrap(java.util.concurrent.CompletableFuture.completedFuture(false));
        RegionFuture<Boolean> f = new RegionFuture<>();
        p.runOnOwner(() -> {
            try {
                p.withPersistentData(tag -> {
                    edit.accept(new NbtPersistentData(tag, false));
                    return null;
                });
                f.complete(true);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /** /data get entity 와 같은 경로 — 플레이어 NBT 스냅샷은 인벤토리 잠금 아래에서 떠서 아무 스레드에서 바로 읽는다(바닐라 /data 와 같다). */
    @Override
    public java.util.concurrent.CompletableFuture<org.domi.furnace.api.nbt.NbtCompound> readNbt() {
        if (p.isDisconnected()) return RegionFuture.wrap(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("접속이 끊긴 플레이어: " + p.name())));
        try {
            return RegionFuture.wrap(java.util.concurrent.CompletableFuture.completedFuture(
                    ApiNbt.toApi(org.domi.furnace.server.command.builtin.DataCommands.entityData(p))));
        } catch (Throwable t) {
            return RegionFuture.wrap(java.util.concurrent.CompletableFuture.failedFuture(t));
        }
    }

    /** 바닐라 /data 처럼 플레이어 NBT 는 쓸 수 없다. */
    @Override
    public java.util.concurrent.CompletableFuture<Boolean> editNbt(java.util.function.Consumer<org.domi.furnace.api.nbt.NbtCompound> edit) {
        return RegionFuture.wrap(java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("플레이어 NBT 는 쓸 수 없다(바닐라 /data 와 같다) — Player API 나 Internals 를 쓴다")));
    }

    private PlayerInventoryHandle inventory;

    @Override
    public org.domi.furnace.api.inventory.Inventory inventory() {
        PlayerInventoryHandle i = inventory;
        if (i == null) inventory = i = new PlayerInventoryHandle(host, p);
        return i;
    }
}
