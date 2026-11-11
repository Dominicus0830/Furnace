package org.bukkit.craftbukkit.entity;

import java.net.InetSocketAddress;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatPlayerInventory;

/** Furnace 플레이어 위의 Bukkit Player. 접속이 끊긴 뒤에도 객체는 남지만 isOnline() 이 false 다. */
public class CraftPlayer extends CraftHumanEntity implements Player, org.domi.furnace.compat.paper.FurnacePermissibleSource {

    private final org.domi.furnace.api.entity.Player handle;

    /** 호환 모듈 전용(Compat 캐시가 만든다). 접속마다 새 Furnace 플레이어 핸들이므로 새 CraftPlayer 다. */
    public CraftPlayer(org.domi.furnace.api.entity.Player handle) {
        super(handle);
        this.handle = handle;
    }

    /** 호환 모듈 전용 — 이 플레이어의 Furnace 핸들(접속 동안 같다). */
    @Override
    public org.domi.furnace.api.entity.Player furnaceHandle() { return handle; }

    @Override
    public void furnaceHandle(org.domi.furnace.api.entity.Entity fresh) {
        if (fresh != handle) throw new IllegalArgumentException("플레이어 핸들은 접속 동안 바뀌지 않는다");
    }

    @Override
    public net.minecraft.server.level.ServerPlayer getHandle() { return (net.minecraft.server.level.ServerPlayer) super.getHandle(); }

    /** CraftBukkit getProfile: 핸들의 authlib 프로필(같은 객체). */
    public com.mojang.authlib.GameProfile getProfile() { return getHandle().getGameProfile(); }

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

    @Override
    public String getDisplayName() {
        org.domi.furnace.api.text.Text custom = handle.customDisplayName();
        return custom != null ? Compat.legacy(custom) : handle.name();
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

    // ---- CommandSender / Permissible

    @Override
    public void sendMessage(String message) { handle.sendMessage(Compat.message(message)); }

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

    /** 호환 모듈 전용 — 이 접속의 객체를 더 쓰지 않는다(재접속으로 바뀜): Bukkit 권한 계산·구독·Furnace 거울을 걷는다(CraftBukkit disconnect 의 perm.clearPermissions). */
    public void retire() {
        try {
            perm.clearPermissions();
        } catch (RuntimeException ignored) {
            // 권한 플러그인이 바꿔 끼운 객체가 던져도 새 접속을 막지 않는다
        }
    }

    @Override
    public String toString() { return "CraftPlayer{name=" + handle.name() + "}"; }
}
