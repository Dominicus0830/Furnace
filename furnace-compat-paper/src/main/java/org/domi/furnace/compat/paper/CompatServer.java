package org.domi.furnace.compat.paper;

import java.nio.charset.StandardCharsets;
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
import org.domi.furnace.api.Furnace;

/** Furnace 서버를 Bukkit Server 로 보여 준다. 상태는 없고 부를 때마다 지금 떠 있는 Furnace 서버에 물어본다. */
public final class CompatServer implements Server {

    public static final CompatServer INSTANCE = new CompatServer();

    private static final Logger LOG = Logger.getLogger("Minecraft");

    private CompatServer() {}

    private static Furnace f() { return Compat.furnace(); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material) { return CompatBlockData.create(material); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(String data) { return CompatBlockData.create(data); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material, String data) { return CompatBlockData.create(material, data); }

    private static UnsupportedFeature noVirtualInventory() {
        return new UnsupportedFeature("Bukkit.createInventory", "가상 인벤토리(메뉴)는 클릭·열기·닫기 이벤트와 함께 옮겨야 한다 — Furnace 메뉴 프레임워크(org.domi.furnace.api.menu.Menu)를 쓴다");
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size) { throw noVirtualInventory(); }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size, String title) { throw noVirtualInventory(); }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type) { throw noVirtualInventory(); }

    @Override
    public String getName() { return "Furnace"; }

    @Override
    public String getVersion() { return "git-Furnace-" + f().version() + " (MC: " + f().minecraftVersion() + ")"; }

    @Override
    public String getBukkitVersion() { return f().minecraftVersion() + "-R0.1-SNAPSHOT"; }

    @Override
    public String getMinecraftVersion() { return f().minecraftVersion(); }

    @Override
    public Logger getLogger() { return LOG; }

    @Override
    public PluginManager getPluginManager() { return CompatPluginManager.INSTANCE; }

    @Override
    public Collection<? extends Player> getOnlinePlayers() {
        List<Player> out = new ArrayList<>();
        for (var p : f().onlinePlayers()) out.add(Compat.player(p));
        return out;
    }

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
        f().broadcast(Compat.text(message));
        return f().onlinePlayers().size();
    }

    @Override
    public ConsoleCommandSender getConsoleSender() { return CompatConsole.INSTANCE; }

    /** 옛 방식 명령이면 Bukkit 처럼 지금 스레드가 주 스레드일 때 바로 실행해 알리고(예외는 CommandException), 아니면 Furnace 명령 큐에 넣는다(결과는 알 수 없어 true). */
    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        if (Compat.commandMap().dispatch(sender, line)) return true;
        return f().dispatchCommand(Compat.furnaceSender(sender), line);
    }

    @Override
    public BukkitScheduler getScheduler() { return CompatScheduler.INSTANCE; }

    @Override
    public ServicesManager getServicesManager() { return Compat.services(); }

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

    /** 자바 쪽에서 쓰는 UTF-8 이름 → UUID(Bukkit 의 오프라인 UUID 규칙: OfflinePlayer:이름). */
    static UUID offlineUuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
}
