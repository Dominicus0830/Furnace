package org.bukkit.craftbukkit.entity;

import java.net.InetSocketAddress;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatPlayerInventory;

/** Furnace 플레이어 위의 Bukkit Player. 접속이 끊긴 뒤에도 객체는 남지만 isOnline() 이 false 다. */
@org.bukkit.configuration.serialization.DelegateDeserialization(org.bukkit.craftbukkit.CraftOfflinePlayer.class)
public class CraftPlayer extends CraftHumanEntity implements Player, org.domi.furnace.compat.paper.FurnacePermissibleSource {

    private final org.domi.furnace.api.entity.Player handle;

    /** 호환 모듈 전용(Compat 캐시가 만든다). 접속마다 새 Furnace 플레이어 핸들이므로 새 CraftPlayer 다. */
    public CraftPlayer(org.domi.furnace.api.entity.Player handle) {
        super(handle);
        this.handle = handle;
    }

    @Override
    public com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile() { return new com.destroystokyo.paper.profile.CraftPlayerProfile(this); }

    /**
     * Paper setPlayerProfile: asAuthlibCopy 처럼 빈 id 는 NIL UUID, 빈 이름은 "" 로 복사해 넘긴다(고친 프로필 객체는 이어지지 않는다).
     * 적용(관찰자 재등록·본인 갱신)은 서버가 소유 스레드에서 한다.
     */
    @Override
    public void setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        java.util.UUID id = profile.getId() != null ? profile.getId() : new java.util.UUID(0L, 0L);
        String name = profile.getName() != null ? profile.getName() : "";
        java.util.List<org.domi.furnace.api.entity.ProfileProperty> props = new java.util.ArrayList<>();
        for (var q : profile.getProperties()) props.add(new org.domi.furnace.api.entity.ProfileProperty(q.getName(), q.getValue(), q.getSignature()));
        if (!id.equals(handle.uniqueId())) warnUuidChangeOnce(id);
        handle.profile(new org.domi.furnace.api.entity.ProfileData(id, name, props));
    }

    private static final java.util.concurrent.atomic.AtomicBoolean UUID_CHANGE_WARNED = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * D1: 프로필 id 를 엔티티 UUID 와 다르게 두면 Paper 와 같은 패킷이 나가 관찰자 화면에 플레이어가 안 보일 수 있다 — 개발자가 원인을 알 수 있게
     * 서버 전체에서 한 번만 콘솔에 알린다(패킷은 이 경고와 무관하게 같다). 호출한 플러그인을 알면 이름을 함께 적는다.
     */
    private void warnUuidChangeOnce(java.util.UUID profileId) {
        if (!UUID_CHANGE_WARNED.compareAndSet(false, true)) return;
        String plugin = org.domi.furnace.compat.nms.NmsCaller.pluginName();
        var log = org.slf4j.LoggerFactory.getLogger("Furnace");
        if (plugin != null) log.warn(org.domi.furnace.compat.i18n.CompatLang.t("compat.profile-uuid-changed", plugin, handle.name(), profileId, handle.uniqueId()));
        else log.warn(org.domi.furnace.compat.i18n.CompatLang.t("compat.profile-uuid-changed-unknown", handle.name(), profileId, handle.uniqueId()));
    }

    /** 시험용: "한 번만" 기록을 비운다. */
    public static void resetUuidChangeWarning() { UUID_CHANGE_WARNED.set(false); }

    /** 호환 모듈 전용 — 이 플레이어의 Furnace 핸들(접속 동안 같다). */
    @Override
    public org.domi.furnace.api.entity.Player furnaceHandle() { return handle; }

    @Override
    public void furnaceHandle(org.domi.furnace.api.entity.Entity fresh) {
        if (fresh != handle) throw new IllegalArgumentException("the player handle does not change while connected");
    }

    @Override
    public net.minecraft.server.level.ServerPlayer getHandle() { return (net.minecraft.server.level.ServerPlayer) super.getHandle(); }

    @Override
    protected net.minecraft.world.entity.Entity createHandle() { return new net.minecraft.server.level.ServerPlayer(this); }

    private CompatPlayerInventory inventory;

    @Override
    public org.bukkit.inventory.PlayerInventory getInventory() {
        CompatPlayerInventory i = inventory;
        if (i == null) inventory = i = new CompatPlayerInventory(this);
        return i;
    }

    @Override
    public org.bukkit.inventory.ItemStack getItemInHand() { return getInventory().getItemInHand(); }

    @Override
    public void setItemInHand(org.bukkit.inventory.ItemStack item) { getInventory().setItemInHand(item); }

    @Override
    public void updateInventory() {}

    @Override
    public org.bukkit.inventory.InventoryView openInventory(org.bukkit.inventory.Inventory inventory) {
        java.util.Objects.requireNonNull(inventory, "inventory");
        return org.domi.furnace.compat.paper.CompatMenuInventory.open(this, inventory);
    }

    @Override
    public void closeInventory() { handle.closeContainer(); }

    @Override
    public org.bukkit.inventory.InventoryView getOpenInventory() { return org.domi.furnace.compat.paper.CompatMenuInventory.openView(this); }

    @Override
    public String getName() { return handle.name(); }

    /** Paper CraftPlayer#getPersistentDataContainer: 반환형이 CraftPersistentDataContainer(플러그인이 이 서술자로 링크한다). */
    @Override
    public org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer getPersistentDataContainer() {
        return (org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer) super.getPersistentDataContainer();
    }

    /**
     * Paper displayName(): 표시 이름을 Adventure 글로. 바꾼 이름이 있으면 그 글, 없으면 {@link #getDisplayName()} 의 이름 그대로.
     * Adventure 라이브러리는 서버가 쓰는 플러그인이 있을 때 받아 둔다(이 호출이 닿는 플러그인은 이미 Adventure 를 쓴다).
     */
    public net.kyori.adventure.text.Component displayName() {
        org.domi.furnace.api.text.Text custom = handle.customDisplayName();
        org.domi.furnace.api.text.Text text = custom != null ? custom : org.domi.furnace.api.text.Text.text(getDisplayName());
        return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(text.toJson());
    }

    @Override
    public String getDisplayName() {
        org.domi.furnace.api.text.Text custom = handle.customDisplayName();
        if (custom != null) return Compat.legacy(custom);
        // Paper adventure$displayName: 접속 때의 이름 — setPlayerProfile 로 이름이 바뀌어도 그대로다(설계 R4)
        var c = handle.connection();
        return c != null && c.name() != null ? c.name() : handle.name();
    }

    @Override
    public void setDisplayName(String name) { handle.customDisplayName(name == null ? null : Compat.text(name)); }

    @Override
    public boolean isOnline() { return handle.isOnline(); }

    @Override
    public Player getPlayer() { return handle.isOnline() ? this : null; }

    @Override
    public InetSocketAddress getAddress() { return handle.address(); }

    @Override
    public void kickPlayer(String message) { handle.kick(message == null ? org.domi.furnace.api.text.Text.empty() : Compat.text(message)); }

    /** Paper kick(Component)(NMS shim 5e-3 — Axiom 이 쓴다): Adventure 글을 JSON 으로 옮겨 그대로 이유로 보낸다. */
    @Override
    public void kick(net.kyori.adventure.text.Component message) {
        handle.kick(message == null ? org.domi.furnace.api.text.Text.empty()
                : org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(
                        net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(message)));
    }

    @Override
    public int getLevel() { return handle.level(); }

    @Override
    public void setLevel(int level) { handle.setLevel(level); }

    @Override
    public void giveExp(int amount) { handle.giveExperience(amount); }

    @Override
    public int getFoodLevel() { return handle.foodLevel(); }

    @Override
    public void setFoodLevel(int value) { handle.setFoodLevel(value); }

    @Override
    public boolean performCommand(String command) { return handle.performCommand(command); }

    @Override
    public int getPing() { return handle.ping(); }

    @Override
    public String getLocale() { return handle.locale() == null ? "en_us" : handle.locale().toString().toLowerCase(Locale.ROOT); }

    @Override
    public int getClientViewDistance() { return handle.clientViewDistance(); }

    @Override
    public int getViewDistance() { return handle.viewDistance(); }

    /** Paper: 불러오는 거리를 값 + 1 로(검사 없음). */
    @Override
    public void setViewDistance(int viewDistance) { handle.setViewDistance(viewDistance); }

    @Override
    public int getSimulationDistance() { return handle.simulationDistance(); }

    @Override
    public void setSimulationDistance(int simulationDistance) { handle.setSimulationDistance(simulationDistance); }

    @Override
    public int getSendViewDistance() { return handle.sendViewDistance(); }

    @Override
    public void setSendViewDistance(int viewDistance) { handle.setSendViewDistance(viewDistance); }

    @Override
    public Locale locale() { return handle.locale() == null ? Locale.US : handle.locale(); }   // Paper ServerPlayer.adventure$locale 기본값 Locale.US

    @Override
    public void updateCommands() {
        if (handle.isOnline()) Compat.furnace().commands().updateCommands(handle);   // CraftBukkit: 접속 전(연결이 없을 때)이면 아무것도 안 한다
    }

    @Override
    public GameMode getGameMode() { return GameMode.valueOf(handle.gameMode().name()); }

    @Override
    public void setGameMode(GameMode mode) {
        handle.setGameMode(org.domi.furnace.api.entity.GameMode.valueOf(mode.name()));
    }

    // ---- 소리·입자(H4 묶음 C) — 이 사람에게만. Paper CraftPlayer 처럼 null 인자는 조용히 넘어간다

    @Override
    public void playSound(org.bukkit.Location loc, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (sound == null) return;
        playSound(loc, org.domi.furnace.compat.paper.CompatEffects.key(sound), category, volume, pitch, seed);
    }

    @Override
    public void playSound(org.bukkit.Location loc, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (loc == null || sound == null || category == null || !handle.isOnline()) return;
        handle.playSound(org.domi.furnace.compat.paper.CompatEffects.sound(sound, category, volume, pitch), loc.getX(), loc.getY(), loc.getZ(), seed);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (sound == null) return;
        playSound(entity, org.domi.furnace.compat.paper.CompatEffects.key(sound), category, volume, pitch, seed);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (!(entity instanceof CraftEntity ce) || sound == null || category == null || !handle.isOnline()) return;
        handle.playSound(org.domi.furnace.compat.paper.CompatEffects.sound(sound, category, volume, pitch), ce.furnaceHandle(), seed);
    }

    // ---- 레벨 이벤트(Paper CraftPlayer#playEffect) — 이 사람에게만, 거리와 관계없이

    @Override
    public void playEffect(org.bukkit.Location loc, org.bukkit.Effect effect, int data) {
        if (effect == null) throw new IllegalArgumentException("Effect cannot be null");
        if (loc == null) throw new IllegalArgumentException("Location cannot be null");
        if (!handle.isOnline()) return;
        handle.sendPacket(org.bukkit.craftbukkit.CraftEffect.packet(loc, effect, data));
    }

    @Override
    public <T> void playEffect(org.bukkit.Location loc, org.bukkit.Effect effect, T data) {
        org.bukkit.craftbukkit.CraftEffect.checkData(effect, data);
        playEffect(loc, effect, org.bukkit.craftbukkit.CraftEffect.getDataValue(effect, data));
    }

    // ---- 웅크림·달리기(Paper CraftPlayer: isShiftKeyDown·isSprinting)

    @Override
    public boolean isSneaking() { return handle.isSneaking(); }

    @Override
    public boolean isSprinting() { return handle.isSprinting(); }

    /** category 가 null 이면 MASTER(CraftPlayer 와 같다 — 모든 분류가 아니다). */
    @Override
    public void stopSound(String sound, org.bukkit.SoundCategory category) {
        if (!handle.isOnline()) return;
        org.bukkit.SoundCategory c = category == null ? org.bukkit.SoundCategory.MASTER : category;
        handle.stopSound(org.domi.furnace.compat.paper.CompatEffects.source(c), sound);
    }

    @Override
    public void stopSound(org.bukkit.SoundCategory category) {
        java.util.Objects.requireNonNull(category, "category");   // CraftPlayer 도 SoundSource.valueOf(category.name()) 에서 NPE
        if (handle.isOnline()) handle.stopSound(org.domi.furnace.compat.paper.CompatEffects.source(category), null);
    }

    @Override
    public void stopAllSounds() {
        if (handle.isOnline()) handle.stopSound(null, null);
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ,
                                  double extra, T data, boolean force) {
        org.domi.furnace.api.world.ParticleEffect effect = org.domi.furnace.compat.paper.CompatEffects.particle(particle, data);
        if (handle.isOnline()) handle.spawnParticle(effect, x, y, z, count, offsetX, offsetY, offsetZ, extra, force);
    }

    // ---- 플러그인 메시지

    /**
     * CraftPlayer.sendPluginMessage 와 같다: 검사(켜진 플러그인·보내는 채널 등록·1 MiB) 뒤, 클라이언트가 그 채널을 알렸을 때만 보낸다.
     * "BungeeCord" 는 "bungeecord:main" 으로 나간다. 접속이 끊겼으면 아무 일도 없다.
     */
    @Override
    public void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message) {
        org.bukkit.plugin.messaging.StandardMessenger.validatePluginMessage(Compat.messenger(), source, channel, message);
        if (!handle.isOnline()) return;
        // 선 이름("bungeecord:main")으로 바꿔 찾는다 — getListeningPluginChannels().contains(channel) 과 같은 결과, 집합을 만들지 않는다
        String wire = org.bukkit.plugin.messaging.StandardMessenger.validateAndCorrectChannel(channel);
        if (handle.listeningPluginChannels().contains(wire)) handle.sendPluginMessage(wire, message);
    }

    /** 클라이언트가 minecraft:register 로 알린 채널(Bukkit 이름 — "bungeecord:main" 은 "BungeeCord", Bukkit 이 받지 않는 이름은 뺀다). */
    @Override
    public java.util.Set<String> getListeningPluginChannels() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String wire : handle.listeningPluginChannels()) {
            String name = org.domi.furnace.compat.paper.CompatMessenger.bukkitName(wire);
            if (name != null) out.add(name);
        }
        return java.util.Collections.unmodifiableSet(out);
    }

    // ---- 비행·제목·가상 블록 (Paper CraftPlayer 와 같은 순서·검사)

    @Override
    public boolean getAllowFlight() { return handle.allowFlight(); }

    @Override
    public void setAllowFlight(boolean flight) { handle.setAllowFlight(flight); }

    @Override
    public boolean isFlying() { return handle.isFlying(); }

    @Override
    public void setFlying(boolean value) { handle.setFlying(value); }

    /** 다른 월드 위치여도 좌표만 보낸다(Paper 도 월드를 보지 않는다). 접속이 끊겼으면 아무것도 안 한다. */
    @Override
    public void sendBlockChange(org.bukkit.Location loc, org.bukkit.block.data.BlockData block) {
        if (loc == null) throw new IllegalArgumentException("Location cannot be null");
        if (block == null) throw new IllegalArgumentException("BlockData cannot be null");
        if (!handle.isOnline()) return;
        var pos = new org.domi.furnace.api.world.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        handle.sendPacket(new org.domi.furnace.api.packet.clientbound.ClientboundBlockUpdatePacket(pos,
                ((org.domi.furnace.compat.paper.CompatBlockData) block).furnace()));
    }

    @Override
    public void sendTitle(String title, String subtitle) { sendTitle(title, subtitle, 10, 70, 20); }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        handle.sendPacket(new org.domi.furnace.api.packet.clientbound.ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        if (title != null)
            handle.sendPacket(new org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket(firstLine(title)));
        if (subtitle != null)
            handle.sendPacket(new org.domi.furnace.api.packet.clientbound.ClientboundSetSubtitleTextPacket(firstLine(subtitle)));
    }

    /** CraftChatMessage.fromString(s)[0] — 레거시 문자열의 첫 줄만. */
    private static org.domi.furnace.api.text.Text firstLine(String legacy) {
        return net.minecraft.network.chat.Component.furnace(org.bukkit.craftbukkit.util.CraftChatMessage.fromString(legacy)[0]);
    }

    @Override
    public void resetTitle() { handle.sendPacket(new org.domi.furnace.api.packet.clientbound.ClientboundClearTitlesPacket(true)); }

    // ---- Spigot(BungeeCord 채팅 컴포넌트)

    private volatile Player.Spigot spigot;

    /** 처음 부를 때 만든다 — 컴포넌트 클래스(net.md_5)는 그 라이브러리를 쓰는 플러그인이 있을 때만 있다. */
    @Override
    public Player.Spigot spigot() {
        Player.Spigot s = spigot;
        if (s == null) spigot = s = org.domi.furnace.compat.paper.BungeeChat.playerSpigot(this);
        return s;
    }

    // ---- CommandSender / Permissible

    /** 모달 대화 중이면 플러그인 메시지를 보내지 않는다(CraftPlayer.sendMessage — 대화 문구는 sendRawMessage 로 간다). */
    @Override
    public void sendMessage(String message) {
        var t = conversations;
        if (t == null || !t.isConversingModaly()) sendRawMessage(message);
    }

    // ---- Conversable (CraftBukkit ConversationTracker)

    private volatile org.domi.furnace.compat.paper.ConversationTracker conversations;

    /** 이 접속의 대화 줄 — 대화가 있는 동안 채팅을 가로챈다(가로챈 채팅은 전역 스레드에서 온다). 처음 대화를 쓸 때 만든다. */
    private org.domi.furnace.compat.paper.ConversationTracker conversations() {
        var t = conversations;
        if (t == null) {
            synchronized (this) {
                t = conversations;
                if (t == null) conversations = t = new org.domi.furnace.compat.paper.ConversationTracker(h -> player().chatInputHandler(h));
            }
        }
        return t;
    }

    @Override
    public boolean isConversing() { return conversations != null && conversations.isConversing(); }

    @Override
    public void acceptConversationInput(String input) { conversations().acceptConversationInput(input); }

    @Override
    public boolean beginConversation(org.bukkit.conversations.Conversation conversation) { return conversations().beginConversation(conversation); }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation) {
        abandonConversation(conversation, new org.bukkit.conversations.ConversationAbandonedEvent(conversation,
                new org.bukkit.conversations.ManuallyAbandonedConversationCanceller()));
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation, org.bukkit.conversations.ConversationAbandonedEvent details) {
        conversations().abandonConversation(conversation, details);
    }

    @Override
    public void sendRawMessage(String message) { sendRawMessage(null, message); }

    @Override
    public void sendRawMessage(java.util.UUID sender, String message) {
        if (message == null) throw new IllegalArgumentException("message cannot be null");   // CraftPlayer 와 같다
        handle.sendMessage(Compat.message(message));
    }

    @Override
    public void sendMessage(String... messages) {
        for (String m : messages) sendMessage(m);
    }

    /** 상위 생성자(권한 계산 객체 생성) 도중에도 쓸 수 있는 핸들 — {@code handle} 필드는 그때 아직 비어 있다. */
    private org.domi.furnace.api.entity.Player player() { return (org.domi.furnace.api.entity.Player) super.furnaceHandle(); }

    @Override
    public org.domi.furnace.api.permission.Permissible furnacePermissible() { return player(); }

    @Override
    public boolean isOp() { return player().isOp(); }

    /** CraftBukkit 처럼 값이 같으면 아무것도 안 하고, 바꾼 뒤 권한을 다시 계산한다. */
    @Override
    public void setOp(boolean value) {
        if (value == isOp()) return;
        Compat.furnace().offlinePlayer(handle.uniqueId()).setOpLevel(value ? 4 : 0);
        perm.recalculatePermissions();
    }

    /**
     * CraftBukkit 처럼: OfflinePlayer 와는 UUID 로, 다른 CraftPlayer 와는 같은 접속(엔티티)이어야 같다 — 재접속한 새 객체는 옛 객체와 다르다
     * (권한 구독 WeakHashMap 이 옛 객체를 키로 붙들어 새 객체가 빠지는 일을 막는다). 해시는 CraftOfflinePlayer 와 같은 식.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof org.bukkit.OfflinePlayer other) || other.getUniqueId() == null) return false;
        if (!getUniqueId().equals(other.getUniqueId())) return false;
        return !(o instanceof CraftPlayer cp) || cp.player() == player();
    }

    @Override
    public int hashCode() { return 97 * 5 + getUniqueId().hashCode(); }

    /** 설정에 쓸 값: {@code name} 하나(Paper 와 같다 — 되읽을 때는 이름으로 OfflinePlayer 를 찾는다). */
    @Override
    public java.util.Map<String, Object> serialize() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("name", getName());
        return result;
    }

    /** 호환 모듈 전용 — 접속이 끝났다: NMS 연결의 가상 채널을 닫는다(이미 만든 shim 이 있을 때만). */
    public void disconnected() {
        // 바닐라 생성자로 만든 월드에 없는 플레이어는 연결이 없다(connection null)
        if (peekHandle() instanceof net.minecraft.server.level.ServerPlayer sp && sp.connection != null) sp.connection.compatDisconnected();
        var t = conversations;
        if (t != null) t.abandonAllConversations();   // CraftBukkit PlayerList.remove: 퇴장하면 대화를 모두 끝낸다
    }

    /** 호환 모듈 전용 — 이 접속의 객체를 더 쓰지 않는다(재접속으로 바뀜): Bukkit 권한 계산·구독·Furnace 거울을 걷는다(CraftBukkit disconnect 의 perm.clearPermissions). */
    public void retire() {
        disconnected();
        clearPermissionState();
    }

    /** 호환 모듈 전용 — Bukkit 권한 계산·구독·Furnace 거울만 걷는다(멱등). 퇴장 때도 부른다(CraftBukkit disconnect). */
    public void clearPermissionState() {
        try {
            perm.clearPermissions();
        } catch (RuntimeException ignored) {
            // 권한 플러그인이 바꿔 끼운 객체가 던져도 새 접속을 막지 않는다
        }
    }

    @Override
    public String toString() { return "CraftPlayer{name=" + handle.name() + "}"; }
}
