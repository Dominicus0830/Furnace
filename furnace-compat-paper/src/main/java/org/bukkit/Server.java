package org.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;

/** 서버. 호환 범위는 docs/api/compat-paper.md. */
public interface Server extends org.bukkit.plugin.messaging.PluginMessageRecipient {

    String getName();

    /** 서버 버전 문자열(예: "Furnace 1.0 (MC: 1.21.1)"). */
    String getVersion();

    /** Bukkit API 버전 문자열(예: "1.21.1-R0.1-SNAPSHOT"). */
    String getBukkitVersion();

    /** 마인크래프트 버전(Paper API). */
    String getMinecraftVersion();

    Logger getLogger();

    PluginManager getPluginManager();

    Collection<? extends Player> getOnlinePlayers();

    int getMaxPlayers();

    /** 접속 중인 플레이어를 이름(대소문자 무시)으로, 없으면 null. */
    Player getPlayerExact(String name);

    /** 접속 중인 플레이어를 이름 일부로(Bukkit 처럼 정확히 같은 이름을 먼저, 없으면 앞부분이 같은 가장 짧은 이름), 없으면 null. */
    Player getPlayer(String name);

    Player getPlayer(UUID id);

    List<World> getWorlds();

    World getWorld(String name);

    World getWorld(UUID id);

    int broadcastMessage(String message);

    ConsoleCommandSender getConsoleSender();

    boolean dispatchCommand(CommandSender sender, String commandLine);

    String getMotd();

    int getViewDistance();

    int getSimulationDistance();

    /** 옛 스케줄러(틱 단위). 동기 작업은 전역(주) 스레드에서 한 줄로 돈다. */
    BukkitScheduler getScheduler();

    /** 플러그인끼리 서비스를 주고받는 곳. */
    ServicesManager getServicesManager();

    /** 플러그인 채널 메신저(서버마다 하나). */
    org.bukkit.plugin.messaging.Messenger getMessenger();

    /** 지금 스레드가 "주 스레드"인가: 전역 스레드이거나, 호환 계층이 옛 코드를 돌리려고 표시한 스레드. */
    boolean isPrimaryThread();

    /** 이 서버의 명령 지도. */
    CommandMap getCommandMap();

    /** 이름이나 별칭으로 찾은 플러그인 명령(플러그인 명령이 아니면 null). */
    org.bukkit.command.PluginCommand getPluginCommand(String name);

    /** 기본 상태의 블록 데이터. 블록이 아니면 IllegalArgumentException. */
    org.bukkit.block.data.BlockData createBlockData(Material material);

    /** "oak_stairs[facing=north]" 같은 문자열에서(적지 않은 속성은 기본값). 잘못이면 IllegalArgumentException. */
    org.bukkit.block.data.BlockData createBlockData(String data);

    org.bukkit.block.data.BlockData createBlockData(Material material, String data);

    /**
     * 가상 인벤토리(메뉴)는 아직 없다 — UnsupportedFeature. 클릭 이벤트·열기·닫기를 함께 옮겨야 해서 Furnace 의 메뉴 프레임워크
     * (org.domi.furnace.api.menu.Menu)로 옮기는 것이 맞다.
     */
    Inventory createInventory(InventoryHolder owner, int size);

    Inventory createInventory(InventoryHolder owner, int size, String title);

    Inventory createInventory(InventoryHolder owner, InventoryType type);
}
