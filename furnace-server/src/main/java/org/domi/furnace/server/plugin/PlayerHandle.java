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

    @Override
    public void sendPacket(org.domi.furnace.api.packet.ClientboundPacket packet) { host.packets().send(this, packet); }

    @Override
    public void hideEntity(org.domi.furnace.api.plugin.FurnacePlugin plugin, org.domi.furnace.api.entity.Entity target) { host.entityViews().hide(plugin, this, target); }

    @Override
    public void showEntity(org.domi.furnace.api.plugin.FurnacePlugin plugin, org.domi.furnace.api.entity.Entity target) { host.entityViews().show(plugin, this, target); }

    @Override
    public boolean canSee(org.domi.furnace.api.entity.Entity target) { return host.entityViews().canSee(this, target); }

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
    public Text customDisplayName() { return p.customDisplayName(); }

    @Override
    public void customDisplayName(Text name) { p.customDisplayName(name); }

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
    public org.domi.furnace.api.net.PlayerConnection connection() { return p.connection(); }

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

    private static String soundKey(String key) {
        return key.indexOf(':') < 0 ? "minecraft:" + key : key;
    }

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound s) { playSound(s, p.x(), p.y(), p.z()); }

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound s, double x, double y, double z) {
        java.util.Objects.requireNonNull(s, "sound");
        p.send(new org.domi.furnace.server.net.protocol.play.SoundPacket(-1, soundKey(s.key()), s.source().ordinal(), x, y, z, s.volume(), s.pitch(),
                java.util.concurrent.ThreadLocalRandom.current().nextLong()));
    }

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound s, double x, double y, double z, long seed) {
        java.util.Objects.requireNonNull(s, "sound");
        p.send(ApiEffects.soundAt(s, x, y, z, seed));   // 레지스트리 소리는 번호로(WorldHandle 과 같다)
    }

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound s, org.domi.furnace.api.entity.Entity emitter, long seed) {
        java.util.Objects.requireNonNull(s, "sound");
        java.util.Objects.requireNonNull(emitter, "emitter");
        int eid;
        if (emitter instanceof PlayerHandle ph) {
            if (!ph.p.dimension().equals(p.dimension())) return;
            eid = ph.p.entityId();
        } else if (emitter instanceof EntityHandle eh && eh.tasks().owner() instanceof org.domi.furnace.server.entity.Entity e) {
            if (e.isRemoved() || !e.dimensionKey().equals(p.dimension())) return;
            eid = e.id();
        } else {
            return;
        }
        p.send(ApiEffects.soundOn(s, eid, seed));
    }

    @Override
    public void spawnParticle(org.domi.furnace.api.world.ParticleEffect particle, double x, double y, double z, int count,
                              double offsetX, double offsetY, double offsetZ, double speed, boolean force) {
        var w = core().world(p.dimension());
        var blocks = w != null ? w.blockRegistry() : core().world().blockRegistry();
        var items = w != null ? w.itemRegistry() : core().world().itemRegistry();
        var opts = ApiEffects.particle(particle, blocks, items);
        p.send(new org.domi.furnace.server.net.protocol.play.LevelParticles(force, x, y, z, (float) offsetX, (float) offsetY, (float) offsetZ,
                (float) speed, count, opts));
    }

    @Override
    public void stopSound(org.domi.furnace.api.audience.Sound.Source source, String key) {
        // 바닐라 클라이언트는 stop_sound 의 이름을 ResourceLocation 으로 읽다 실패하면 접속을 끊는다 — playSound 와 같은 규칙으로 막는다
        if (key != null && !org.domi.furnace.api.audience.Sound.isValidKey(key)) throw new IllegalArgumentException("잘못된 소리 이름: " + key);
        p.send(new org.domi.furnace.server.net.protocol.play.StopSound(source == null ? -1 : source.ordinal(), key == null ? null : soundKey(key)));
    }

    // 플러그인 보스바·사이드바·메뉴

    private final java.util.concurrent.ConcurrentHashMap<org.domi.furnace.api.bossbar.BossBar, BossBarBinding> bossBars = new java.util.concurrent.ConcurrentHashMap<>();
    private SidebarBinding sidebar;   // this 로 동기화

    @Override
    public void showBossBar(org.domi.furnace.api.bossbar.BossBar bar) {
        java.util.Objects.requireNonNull(bar, "bar");
        if (p.isDisconnected()) return;
        BossBarBinding[] fresh = new BossBarBinding[1];
        bossBars.computeIfAbsent(bar, b -> fresh[0] = new BossBarBinding(p, b));
        if (fresh[0] != null) fresh[0].show();
    }

    @Override
    public void hideBossBar(org.domi.furnace.api.bossbar.BossBar bar) {
        BossBarBinding b = bossBars.remove(bar);
        if (b != null) b.hide();
    }

    @Override
    public synchronized void showSidebar(org.domi.furnace.api.scoreboard.Sidebar s) {
        java.util.Objects.requireNonNull(s, "sidebar");
        if (p.isDisconnected()) return;
        if (sidebar != null) {
            if (sidebar.sidebar() == s) return;
            sidebar.hide(null);   // 곧 다른 사이드바가 걸리니 서버 것을 되살리지 않는다
        }
        sidebar = new SidebarBinding(p, s);
        sidebar.show();
    }

    @Override
    public synchronized void hideSidebar(org.domi.furnace.api.scoreboard.Sidebar s) {
        if (sidebar == null || sidebar.sidebar() != s) return;
        var real = host.core().scoreboard().getDisplayObjective(org.domi.furnace.server.scores.DisplaySlot.SIDEBAR);
        sidebar.hide(real);
        sidebar = null;
    }

    @Override
    public void openMenu(org.domi.furnace.api.menu.Menu menu) {
        java.util.Objects.requireNonNull(menu, "menu");
        if (p.isDisconnected()) return;
        var session = new MenuSession(this, menu, host);   // 모르는 아이템이면 여기서 IllegalArgumentException
        var ih = p.inventoryHost();
        if (ih == null) return;
        lastSession = session;
        ih.openPluginMenu(session);
    }

    /** 마지막으로 연(또는 여는 중인) 메뉴 — 닫혔거나 열리지 못했으면 ended. */
    private volatile MenuSession lastSession;

    private MenuSession liveSession() {
        MenuSession s = lastSession;
        return s == null || s.ended() ? null : s;
    }

    @Override
    public void retitleMenu(org.domi.furnace.api.text.Text title) {
        java.util.Objects.requireNonNull(title, "title");
        MenuSession s = liveSession();
        if (s != null) s.setViewerTitle(title);
    }

    @Override
    public org.domi.furnace.api.text.Text menuTitle(org.domi.furnace.api.menu.Menu menu) {
        MenuSession s = liveSession();
        return s != null && s.api() == menu ? s.title() : null;
    }

    @Override
    public void closeMenu() {
        var ih = p.inventoryHost();
        if (ih == null) return;
        ih.execute(() -> {
            var m = p.containerMenu();
            if (m != null && m.pluginOwner() instanceof MenuSession ms && ms.player() == this) ih.closeContainer(m, org.domi.furnace.api.event.inventory.InventoryCloseEvent.Reason.PLUGIN);
        });
    }

    @Override
    public void closeContainer() {
        var ih = p.inventoryHost();
        if (ih == null) return;
        ih.execute(() -> {
            var m = p.containerMenu();
            if (m != null) ih.closeContainer(m, org.domi.furnace.api.event.inventory.InventoryCloseEvent.Reason.PLUGIN);
        });
    }

    @Override
    public String openContainerType() {
        var m = p.containerMenu();
        return m == null || m == p.inventoryMenu() ? null : ApiConvert.menuType(m);
    }

    @Override
    public org.domi.furnace.api.menu.Menu currentMenu() {
        var m = p.containerMenu();
        return m != null && m.pluginOwner() instanceof MenuSession ms && ms.player() == this ? ms.api() : null;
    }

    /** 접속이 끊겼다 — 보스바·사이드바가 쥔 구독을 푼다(패킷 없이). */
    public void dispose() {
        for (BossBarBinding b : bossBars.values()) b.detach();
        bossBars.clear();
        synchronized (this) {
            if (sidebar != null) sidebar.detach();
            sidebar = null;
        }
    }

    @Override
    public void sendPlayerListHeaderAndFooter(Text header, Text footer) { p.send(new TabListHeaderFooter(p.localize(header), p.localize(footer))); }

    // ------------------------------------------------------------------ 권한

    @Override
    public boolean isOp() { return core().lists().isOp(ServerCore.profileOf(p)); }

    @Override
    public boolean hasPermission(String node) {
        Boolean o = perms.manager().overrideHas(this, node);   // 권한 플러그인(판정 공급자)이 맡으면 그 값
        return o != null ? o : perms.hasPermission(node);
    }

    @Override
    public boolean isPermissionSet(String node) {
        Boolean o = perms.manager().overrideIsSet(this, node);
        return o != null ? o : perms.isPermissionSet(node);
    }

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
        return host.profiled().wrap(w.handle().chunkScheduler(Math.floorDiv((int) Math.floor(p.x()), 16), Math.floorDiv((int) Math.floor(p.z()), 16)));
    }

    /** 같은 차원 안만. 서버 텔레포트는 연결 스레드가 보정 패킷을 보내는 스레드 안전 경로다. 결과는 PlayerTeleportEvent 취소까지 반영한 진짜 값. */
    @Override
    public java.util.concurrent.CompletableFuture<Boolean> teleport(Location to) {
        return java.util.concurrent.CompletableFuture.completedFuture(teleportNow(to));
    }

    /** 이벤트가 결과를 정하므로 어느 스레드에서나 곧바로(탈것 내리기·깨우기만 소유 스레드로 넘어간다). 다른 차원·끊긴 접속·이벤트 취소는 false. */
    @Override
    public boolean teleportNow(Location to) {
        if (!to.world().equals(p.dimension())) return false;
        return p.pluginTeleport(to.x(), to.y(), to.z(), to.yaw(), to.pitch());
    }

    private volatile EntitySchedulerImpl entityScheduler;

    @Override
    public org.domi.furnace.api.entity.EntityScheduler entityScheduler() {
        EntitySchedulerImpl s = entityScheduler;
        if (s == null) entityScheduler = s = new EntitySchedulerImpl(p.apiTasks(), host.profiled());
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

    /** 플레이어 영속 데이터는 서버가 잠가 두므로 어느 스레드에서나 곧바로(접속이 끊겼으면 false). */
    @Override
    public boolean editPersistentDataNow(java.util.function.Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        if (p.isDisconnected()) return false;
        p.withPersistentData(tag -> {
            edit.accept(new NbtPersistentData(tag, false));
            return null;
        });
        return true;
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

    @Override
    public int heldSlot() { return p.inventory().selected(); }

    /** 선택은 소유 스레드에서 바꾸고(그 스레드면 곧바로) 클라이언트에 SetHeldSlot 을 보낸다. */
    @Override
    public java.util.concurrent.CompletableFuture<Void> setHeldSlot(int slot) {
        if (slot < 0 || slot > 8) return RegionFuture.wrap(java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException("단축 칸은 0..8: " + slot)));
        if (p.isDisconnected()) return RegionFuture.wrap(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("접속 중이 아니다")));
        RegionFuture<Void> f = new RegionFuture<>();
        p.runOnOwner(() -> {
            try {
                p.inventory().setSelected(slot);
                ClientResync.heldSlot(p);
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    // ------------------------------------------------------------------ 레시피 북

    private static java.util.List<org.domi.furnace.server.data.ResourceId> recipeIds(java.util.Collection<String> keys) {
        java.util.List<org.domi.furnace.server.data.ResourceId> out = new java.util.ArrayList<>();
        for (String k : keys) {
            var id = org.domi.furnace.server.data.ResourceId.tryParse(k);
            if (id != null) out.add(id);
        }
        return out;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Integer> discoverRecipes(java.util.Collection<String> keys) {
        var rm = host.core().recipes();
        if (rm == null || p.isDisconnected()) return RegionFuture.wrap(java.util.concurrent.CompletableFuture.completedFuture(0));
        var ids = recipeIds(keys);
        RegionFuture<Integer> f = new RegionFuture<>();
        p.runOnOwner(() -> {
            try {
                f.complete(p.awardRecipes(ids, rm));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Integer> undiscoverRecipes(java.util.Collection<String> keys) {
        if (p.isDisconnected()) return RegionFuture.wrap(java.util.concurrent.CompletableFuture.completedFuture(0));
        var ids = recipeIds(keys);
        RegionFuture<Integer> f = new RegionFuture<>();
        p.runOnOwner(() -> {
            try {
                f.complete(p.resetRecipes(ids));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    @Override
    public org.domi.furnace.api.entity.Pose pose() { return org.domi.furnace.api.entity.Pose.byId(p.pose()); }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> setPose(org.domi.furnace.api.entity.Pose pose) {
        if (p.isDisconnected()) return RegionFuture.wrap(java.util.concurrent.CompletableFuture.completedFuture(false));
        RegionFuture<Boolean> f = new RegionFuture<>();
        p.runOnOwner(() -> {
            p.poseOverride(pose == null ? -1 : pose.id());
            f.complete(true);
        });
        return f;
    }

    @Override
    public boolean hasDiscoveredRecipe(String key) {
        var id = org.domi.furnace.server.data.ResourceId.tryParse(key);
        return id != null && p.recipeBook().contains(id);
    }
}
