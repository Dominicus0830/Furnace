package org.bukkit.craftbukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import net.minecraft.server.dedicated.DedicatedServer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatBlockData;
import org.domi.furnace.compat.paper.CompatConsole;
import org.domi.furnace.compat.paper.CompatScheduler;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Furnace 서버를 Bukkit Server 로 보여 준다. 상태는 없고 부를 때마다 지금 떠 있는 Furnace 서버에 물어본다.
 * 클래스 이름이 Paper 1.20.5 이후와 같은 접미사 없는 {@code org.bukkit.craftbukkit.CraftServer} 라서, 서버 클래스·패키지 이름으로
 * 구현을 고르는 라이브러리가 Paper 로 본다. {@link #getServer()} 가 NMS 서버 shim 을 준다(H6).
 */
public final class CraftServer implements Server {

    public static final CraftServer INSTANCE = new CraftServer();

    private static final Logger LOG = Logger.getLogger("Minecraft");

    private CraftServer() {}

    /** NMS 서버 shim(Paper·CraftBukkit 의 {@code getServer()} 처럼 DedicatedServer). 늘 같은 객체다. */
    public DedicatedServer getServer() { return DedicatedServer.INSTANCE; }

    private static Furnace f() { return Compat.furnace(); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material) { return CompatBlockData.create(material); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(String data) { return CompatBlockData.create(data); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material, String data) { return CompatBlockData.create(material, data); }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.chest(owner, size, null);
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size, String title) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.chest(owner, size, title);
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.ofType(owner, type, null);
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type, String title) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.ofType(owner, type, title);
    }

    @Override
    public org.bukkit.boss.BossBar createBossBar(String title, org.bukkit.boss.BarColor color, org.bukkit.boss.BarStyle style, org.bukkit.boss.BarFlag... flags) {
        return new org.bukkit.craftbukkit.boss.CraftBossBar(title, color, style, flags);
    }

    @Override
    public String getName() { return "Furnace"; }

    @Override
    public String getVersion() { return "git-Furnace-" + f().version() + " (MC: " + f().minecraftVersion() + ")"; }

    @Override
    public String getBukkitVersion() { return f().minecraftVersion() + "-R0.1-SNAPSHOT"; }

    @Override
    public String getMinecraftVersion() { return f().minecraftVersion(); }

    @Override
    @Deprecated
    public org.bukkit.UnsafeValues getUnsafe() { return org.bukkit.craftbukkit.util.CraftMagicNumbers.INSTANCE; }

    @Override
    public Logger getLogger() { return LOG; }

    @Override
    public PluginManager getPluginManager() { return Compat.pluginManager(); }

    /**
     * Paper 의 playerView 처럼 고칠 수 없는 살아 있는 보기(A15-08): 받아 둔 컬렉션도 지금 접속자를 보이고, add·remove·clear 는
     * UnsupportedOperationException. 순회는 그때의 접속자 목록을 하나씩 어댑터로 바꾼다(목록을 미리 복사하지 않는다).
     */
    /** Paper: Collections.unmodifiableList(Lists.transform(...)) — 살아 있는 고칠 수 없는 List. 고치는 메서드는 비어 있어도 언제나 던진다. */
    private final List<Player> onlineView = new OnlineView();

    private final class OnlineView extends java.util.AbstractList<Player> implements java.util.RandomAccess {
        @Override
        public Player get(int index) {
            var all = f().onlinePlayers();
            if (all instanceof List<?> l) return Compat.player((org.domi.furnace.api.entity.Player) l.get(index));
            return Compat.player(List.copyOf(all).get(index));
        }

        @Override
        public java.util.Iterator<Player> iterator() {
            var it = f().onlinePlayers().iterator();
            return new java.util.Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }

                @Override
                public Player next() { return Compat.player(it.next()); }
            };
        }

        @Override
        public int size() { return f().onlinePlayers().size(); }

        private static UnsupportedOperationException ro() { return new UnsupportedOperationException(); }

        @Override public boolean add(Player p) { throw ro(); }
        @Override public void add(int i, Player p) { throw ro(); }
        @Override public boolean addAll(Collection<? extends Player> c) { throw ro(); }
        @Override public boolean addAll(int i, Collection<? extends Player> c) { throw ro(); }
        @Override public Player set(int i, Player p) { throw ro(); }
        @Override public Player remove(int i) { throw ro(); }
        @Override public boolean remove(Object o) { throw ro(); }
        @Override public boolean removeAll(Collection<?> c) { throw ro(); }
        @Override public boolean retainAll(Collection<?> c) { throw ro(); }
        @Override public boolean removeIf(java.util.function.Predicate<? super Player> f) { throw ro(); }
        @Override public void replaceAll(java.util.function.UnaryOperator<Player> op) { throw ro(); }
        @Override public void sort(java.util.Comparator<? super Player> c) { throw ro(); }
        @Override public void clear() { throw ro(); }
    }

    @Override
    public Collection<? extends Player> getOnlinePlayers() { return onlineView; }

    @Override
    public int getMaxPlayers() { return f().maxPlayers(); }

    @Override
    public Player getPlayerExact(String name) {
        if (name == null) return null;
        return Compat.player(f().player(name));
    }

    @Override
    public Player getPlayer(String name) {
        if (name == null) return null;
        Player exact = getPlayerExact(name);
        if (exact != null) return exact;
        String lower = name.toLowerCase(Locale.ROOT);
        Player found = null;
        int delta = Integer.MAX_VALUE;
        for (var p : f().onlinePlayers()) {
            String n = p.name();
            if (n.toLowerCase(Locale.ROOT).startsWith(lower)) {
                int d = n.length() - lower.length();
                if (d < delta) {
                    found = Compat.player(p);
                    delta = d;
                }
                if (d == 0) break;
            }
        }
        return found;
    }

    @Override
    public Player getPlayer(UUID id) { return id == null ? null : Compat.player(f().player(id)); }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(UUID id) {
        if (id == null) throw new IllegalArgumentException("UUID id cannot be null");
        Player online = getPlayer(id);
        return online != null ? online : new CraftOfflinePlayer(id, null);
    }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(String name) {
        if (name == null) throw new IllegalArgumentException("name cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be empty");
        Player online = getPlayerExact(name);
        if (online != null) return online;
        if (getOnlineMode()) {
            var known = f().offlinePlayerCached(name);
            if (known != null) return getOfflinePlayer(known.uniqueId(), known.name());
        }
        // 바닐라 UUIDUtil.createOfflinePlayerUUID
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return getOfflinePlayer(offline, name);
    }

    private org.bukkit.OfflinePlayer getOfflinePlayer(UUID id, String name) {
        Player online = getPlayer(id);
        return online != null ? online : new CraftOfflinePlayer(id, name);
    }

    @Override
    public java.util.Set<org.bukkit.OfflinePlayer> getOperators() {
        java.util.Set<org.bukkit.OfflinePlayer> out = new java.util.HashSet<>();
        for (var op : f().access().operators()) out.add(getOfflinePlayer(op.uniqueId(), op.name()));
        return out;
    }

    @Override
    public boolean getOnlineMode() { return f().onlineMode(); }

    @Override
    public List<World> getWorlds() {
        List<World> out = new ArrayList<>();
        for (var w : f().worlds()) out.add(Compat.world(w));
        return out;
    }

    @Override
    public World getWorld(String name) { return name == null ? null : Compat.worldNamed(name); }

    @Override
    public World getWorld(UUID id) {
        for (World w : getWorlds()) if (w.getUID().equals(id)) return w;
        return null;
    }

    @Override
    public int broadcastMessage(String message) {
        f().broadcast(Compat.text(message));   // Paper: legacySection().deserialize — 주소 링크 없음
        return f().onlinePlayers().size();
    }

    /**
     * CraftBukkit 처럼 그 권한을 구독한 대상(=값이 정해진 대상) 가운데 권한이 참인 발신자에게만 보낸다 — 그래서 등록되지 않은 권한이면
     * OP·콘솔도 받지 않는다(Bukkit 과 같다). Paper 의 BroadcastMessageEvent 는 아직 없다.
     */
    @Override
    public int broadcast(String message, String permission) {
        java.util.Set<CommandSender> recipients = new java.util.LinkedHashSet<>();
        for (org.bukkit.permissions.Permissible p : getPluginManager().getPermissionSubscriptions(permission)) {
            if (p instanceof CommandSender s && p.hasPermission(permission)) recipients.add(s);
        }
        for (CommandSender s : recipients) s.sendMessage(message);
        return recipients.size();
    }

    @Override
    public ConsoleCommandSender getConsoleSender() { return CompatConsole.INSTANCE; }

    /**
     * 옛 방식 명령이면 Bukkit 처럼 지금 스레드가 주 스레드일 때 바로 실행해 알리고(예외는 CommandException), 아니면 Furnace 명령 큐에 넣는다.
     * 바닐라·Furnace 명령은 Paper 1.21.1 의 VanillaCommandWrapper 처럼 "이름 + 빈 칸 하나로 이은 인자" 로 다시 만들어 보낸다(빈 칸 규칙 통일, 이름 소문자).
     * 트리에 있는 이름이면 true, 없는 이름이면 false 이고 보낸 사람에게는 바닐라 오류 문장이 간다.
     * 앞의 '/' 는 떼지 않는다(Paper 와 같다): "/say hi" 는 없는 이름이라 false, 플러그인이 "/wand" 로 등록한 명령(WorldEdit 의 //wand)은 돈다.
     */
    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        if (Compat.commandMap().dispatch(sender, commandLine)) return true;
        String[] tokens = org.domi.furnace.compat.command.LegacyCommands.splitLine(commandLine);
        if (tokens.length == 0) return false;
        var commands = f().commands();
        String name = commands.hasCommand(tokens[0]) ? tokens[0] : tokens[0].toLowerCase(java.util.Locale.ROOT);
        if (!commands.hasCommand(name)) {
            // 없는 이름: 바닐라 오류 문장만 나오게 보낸다. 서버 쪽은 앞 '/' 를 하나 떼므로 하나 더 붙여 원래 줄이 그대로 파싱되게 한다
            f().dispatchCommand(Compat.furnaceSender(sender), "/" + commandLine.stripLeading());
            return false;
        }
        String line = tokens.length == 1 ? name : name + " " + String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
        f().dispatchCommand(Compat.furnaceSender(sender), line);
        return true;
    }

    @Override
    public BukkitScheduler getScheduler() { return CompatScheduler.INSTANCE; }

    @Override
    public io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler() { return org.domi.furnace.compat.paper.CompatPaperSchedulers.ASYNC; }

    @Override
    public io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler() { return org.domi.furnace.compat.paper.CompatPaperSchedulers.GLOBAL; }

    @Override
    public io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler() { return org.domi.furnace.compat.paper.CompatPaperSchedulers.REGION; }

    @Override
    public ServicesManager getServicesManager() { return Compat.services(); }

    @Override
    public org.bukkit.plugin.messaging.Messenger getMessenger() { return Compat.messenger(); }

    private final org.domi.furnace.compat.paper.CompatHelpMap helpMap = new org.domi.furnace.compat.paper.CompatHelpMap();

    @Override
    public org.bukkit.help.HelpMap getHelpMap() { return helpMap; }

    /** 모든 접속자에게(각자 그 채널을 알린 클라이언트에만 간다 — CraftServer 와 같다). */
    @Override
    public void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message) {
        org.bukkit.plugin.messaging.StandardMessenger.validatePluginMessage(getMessenger(), source, channel, message);
        for (Player player : getOnlinePlayers()) player.sendPluginMessage(source, channel, message);
    }

    /** 접속자들이 알린 채널의 합. */
    @Override
    public java.util.Set<String> getListeningPluginChannels() {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (Player player : getOnlinePlayers()) result.addAll(player.getListeningPluginChannels());
        return result;
    }

    @Override
    public boolean isPrimaryThread() { return org.domi.furnace.compat.thread.PrimaryThread.isPrimary(); }

    @Override
    public CommandMap getCommandMap() { return Compat.commandMap(); }

    @Override
    public org.bukkit.command.PluginCommand getPluginCommand(String name) {
        return name != null && Compat.commandMap().getCommand(name) instanceof org.bukkit.command.PluginCommand pc ? pc : null;
    }

    @Override
    public String getMotd() { return Compat.legacy(f().motd()); }

    @Override
    public int getViewDistance() { return f().viewDistance(); }

    @Override
    public int getSimulationDistance() { return f().simulationDistance(); }
}
