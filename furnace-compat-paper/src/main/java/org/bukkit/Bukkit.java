package org.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;

/** 정적 입구 — Server 의 같은 메서드를 부른다. 서버가 정해지기 전에는 getServer() 가 null. */
public final class Bukkit {

    private static volatile Server server;

    private Bukkit() {}

    public static Server getServer() { return server; }

    /** 호환 모듈 전용 — 한 번만 정할 수 있다(같은 객체를 다시 정하는 것은 괜찮다). */
    public static void setServer(Server s) {
        synchronized (Bukkit.class) {
            if (server != null && server != s) throw new UnsupportedOperationException("Cannot redefine singleton Server");
            server = s;
        }
    }

    private static Server s() {
        Server x = server;
        if (x == null) throw new IllegalStateException("서버가 아직 준비되지 않았다");
        return x;
    }

    public static String getName() { return s().getName(); }

    public static String getVersion() { return s().getVersion(); }

    public static String getBukkitVersion() { return s().getBukkitVersion(); }

    public static String getMinecraftVersion() { return s().getMinecraftVersion(); }

    public static Logger getLogger() { return s().getLogger(); }

    public static PluginManager getPluginManager() { return s().getPluginManager(); }

    public static Collection<? extends Player> getOnlinePlayers() { return s().getOnlinePlayers(); }

    public static int getMaxPlayers() { return s().getMaxPlayers(); }

    public static Player getPlayerExact(String name) { return s().getPlayerExact(name); }

    public static Player getPlayer(String name) { return s().getPlayer(name); }

    public static Player getPlayer(UUID id) { return s().getPlayer(id); }

    public static List<World> getWorlds() { return s().getWorlds(); }

    public static World getWorld(String name) { return s().getWorld(name); }

    public static World getWorld(UUID id) { return s().getWorld(id); }

    public static int broadcastMessage(String message) { return s().broadcastMessage(message); }

    public static int broadcast(String message, String permission) { return s().broadcast(message, permission); }

    public static ConsoleCommandSender getConsoleSender() { return s().getConsoleSender(); }

    public static boolean dispatchCommand(CommandSender sender, String commandLine) { return s().dispatchCommand(sender, commandLine); }

    public static String getMotd() { return s().getMotd(); }

    public static int getViewDistance() { return s().getViewDistance(); }

    public static int getSimulationDistance() { return s().getSimulationDistance(); }

    public static BukkitScheduler getScheduler() { return s().getScheduler(); }

    public static ServicesManager getServicesManager() { return s().getServicesManager(); }

    public static org.bukkit.plugin.messaging.Messenger getMessenger() { return s().getMessenger(); }

    public static boolean isPrimaryThread() { return s().isPrimaryThread(); }

    public static org.bukkit.command.CommandMap getCommandMap() { return s().getCommandMap(); }

    public static org.bukkit.command.PluginCommand getPluginCommand(String name) { return s().getPluginCommand(name); }

    public static org.bukkit.block.data.BlockData createBlockData(Material material) { return s().createBlockData(material); }

    public static org.bukkit.block.data.BlockData createBlockData(String data) { return s().createBlockData(data); }

    public static org.bukkit.block.data.BlockData createBlockData(Material material, String data) { return s().createBlockData(material, data); }

    public static org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size) { return s().createInventory(owner, size); }

    public static org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size, String title) { return s().createInventory(owner, size, title); }

    public static org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type) { return s().createInventory(owner, type); }
}
