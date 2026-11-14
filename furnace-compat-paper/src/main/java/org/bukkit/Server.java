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

    /** 내부 값 창구(데이터 버전·프로토콜 번호 등). 지원 범위는 {@link UnsafeValues}. */
    @Deprecated
    UnsafeValues getUnsafe();

    /** 그 형의 레지스트리(이 호환 계층에 없는 형이면 null). */
    default <T extends Keyed> Registry<T> getRegistry(Class<T> tClass) { return org.domi.furnace.compat.paper.CompatRegistries.forType(tClass); }

    /** 태그(없는 태그면 null). */
    default <T extends Keyed> Tag<T> getTag(String registry, NamespacedKey tag, Class<T> clazz) { return org.domi.furnace.compat.paper.CompatTags.get(registry, tag, clazz); }

    /** 그 레지스트리의 태그 전부. */
    default <T extends Keyed> Iterable<Tag<T>> getTags(String registry, Class<T> clazz) { return org.domi.furnace.compat.paper.CompatTags.all(registry, clazz); }

    Logger getLogger();

    PluginManager getPluginManager();

    Collection<? extends Player> getOnlinePlayers();

    int getMaxPlayers();

    /** 접속 중인 플레이어를 이름(대소문자 무시)으로, 없으면 null. */
    Player getPlayerExact(String name);

    /** 접속 중인 플레이어를 이름 일부로(Bukkit 처럼 정확히 같은 이름을 먼저, 없으면 앞부분이 같은 가장 짧은 이름), 없으면 null. */
    Player getPlayer(String name);

    Player getPlayer(UUID id);

    /** Bukkit Server#getEntity(UUID): 모든 월드에서 찾는다(없으면 null). */
    default org.bukkit.entity.Entity getEntity(UUID uuid) { return null; }

    /** 접속 중이면 그 Player, 아니면 UUID 로 가리키는 OfflinePlayer(한 번도 못 본 UUID 도). */
    org.bukkit.OfflinePlayer getOfflinePlayer(UUID id);

    /**
     * 접속 중이면 그 Player, 아니면 이름의 OfflinePlayer. online-mode 면 서버가 아는 이름(usercache)의 UUID 를,
     * 모르거나 offline-mode 면 오프라인 UUID("OfflinePlayer:" + 이름)를 쓴다. Mojang 에 묻지는 않는다(Bukkit 은 묻고 기다린다 — 차이).
     */
    @Deprecated
    org.bukkit.OfflinePlayer getOfflinePlayer(String name);

    /** ops.json 의 모든 플레이어. */
    java.util.Set<org.bukkit.OfflinePlayer> getOperators();

    /** server.properties 의 online-mode. */
    boolean getOnlineMode();

    List<World> getWorlds();

    World getWorld(String name);

    World getWorld(UUID id);

    int broadcastMessage(String message);

    /** 그 권한을 가진(값이 정해져 참인) 접속자·콘솔에게 보낸다. 받은 수를 돌려준다. */
    int broadcast(String message, String permission);

    ConsoleCommandSender getConsoleSender();

    boolean dispatchCommand(CommandSender sender, String commandLine);

    String getMotd();

    int getViewDistance();

    int getSimulationDistance();

    /** 옛 스케줄러(틱 단위). 동기 작업은 전역(주) 스레드에서 한 줄로 돈다. */
    BukkitScheduler getScheduler();

    /** Paper 비동기 스케줄러(벽시계 시간, 서버 스레드 밖 풀). */
    io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler();

    /** Paper 전역 스케줄러(서버 틱, 전역 스레드). */
    io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler();

    /** Paper region 스케줄러(청크를 가진 region 스레드). */
    io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler();

    /** 플러그인끼리 서비스를 주고받는 곳. */
    ServicesManager getServicesManager();

    /** 플러그인 채널 메신저(서버마다 하나). */
    org.bukkit.plugin.messaging.Messenger getMessenger();

    /** /help 항목 모음. 넣은 항목은 보관되지만 Furnace 의 /help 는 아직 보이지 않는다. */
    org.bukkit.help.HelpMap getHelpMap();

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
     * 플러그인 인벤토리(창으로 열 수 있는 가상 보관함). Furnace 보관함 메뉴(org.domi.furnace.api.menu.Menu#storage) 위에 있다.
     * 크기는 9..54 의 9 배수. 종류로 만들 때는 상자·발사기·깔때기 모양만 된다(나머지는 UnsupportedFeature).
     */
    Inventory createInventory(InventoryHolder owner, int size);

    Inventory createInventory(InventoryHolder owner, int size, String title);

    Inventory createInventory(InventoryHolder owner, InventoryType type);

    Inventory createInventory(InventoryHolder owner, InventoryType type, String title);

    /** 보스 막대(처음엔 보이고 보는 사람이 없다). Furnace 보스 막대(org.domi.furnace.api.bossbar.BossBar) 위에 있다. */
    org.bukkit.boss.BossBar createBossBar(String title, org.bukkit.boss.BarColor color, org.bukkit.boss.BarStyle style, org.bukkit.boss.BarFlag... flags);
}
