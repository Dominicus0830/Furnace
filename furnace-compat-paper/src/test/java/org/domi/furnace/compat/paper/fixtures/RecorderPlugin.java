package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerChannelEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 지원하는 모든 이벤트를 받아 기록하는 Bukkit 스타일 플러그인(시험용). 동작은 ACTIONS 로 바꾼다. */
public final class RecorderPlugin extends JavaPlugin implements Listener {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    public static final List<Event> SEEN = new CopyOnWriteArrayList<>();
    /** 이벤트 클래스 단순 이름 → 처리기가 하는 일. */
    public static final Map<String, Consumer<Event>> ACTIONS = new ConcurrentHashMap<>();

    public static void reset() {
        LOG.clear();
        SEEN.clear();
        ACTIONS.clear();
    }

    @Override
    public void onLoad() { LOG.add("recorder:load"); }

    @Override
    public void onEnable() {
        LOG.add("recorder:enable");
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() { LOG.add("recorder:disable"); }

    private void rec(Event e) {
        SEEN.add(e);
        Consumer<Event> a = ACTIONS.get(e.getClass().getSimpleName());
        if (a != null) a.accept(e);
    }

    @EventHandler public void on(PlayerJoinEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.player.PlayerInteractEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.player.PlayerItemHeldEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockPhysicsEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockFromToEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockBurnEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockIgniteEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockRedstoneEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.LeavesDecayEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.FurnaceBurnEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockCookEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockPlaceEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockGrowEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockFadeEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockExplodeEvent e) { rec(e); }

    // 보호 이벤트(T-040a)
    @EventHandler public void on(org.bukkit.event.entity.EntityExplodeEvent e) { rec(e); }

    // 명령 트리·자동완성
    @EventHandler public void on(org.bukkit.event.player.PlayerCommandSendEvent e) { rec(e); }

    @EventHandler public void on(com.destroystokyo.paper.event.server.AsyncTabCompleteEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockPistonExtendEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockPistonRetractEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockFormEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.block.BlockSpreadEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.entity.ProjectileHitEvent e) { rec(e); }

    /** EntityBreakDoorEvent 도 여기로 온다(Bukkit 에서 같은 HandlerList, T-040b). */
    @EventHandler public void on(org.bukkit.event.entity.EntityChangeBlockEvent e) { rec(e); }

    /** ItemSpawnEvent·CreatureSpawnEvent 도 여기로 온다(Bukkit 에서 같은 HandlerList). */
    @EventHandler public void on(org.bukkit.event.entity.EntitySpawnEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.player.PlayerDropItemEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryClickEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryDragEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryOpenEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryCloseEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.PrepareItemCraftEvent e) { rec(e); }

    /** PrepareAnvilEvent 는 PrepareInventoryResultEvent 의 처리기 목록으로 간다(Paper 와 같다). */
    @EventHandler public void on(org.bukkit.event.inventory.PrepareAnvilEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.enchantment.PrepareItemEnchantEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.enchantment.EnchantItemEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.BrewEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.BrewingStandFuelEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryMoveItemEvent e) { rec(e); }

    /** PlayerDeathEvent 도 여기로 온다(Bukkit 에서 같은 HandlerList). */
    @EventHandler public void on(org.bukkit.event.entity.EntityDeathEvent e) { rec(e); }

    @EventHandler public void on(PlayerChannelEvent e) { rec(e); }

    @EventHandler public void on(PlayerQuitEvent e) { rec(e); }

    @EventHandler public void on(PlayerKickEvent e) { rec(e); }

    @EventHandler public void on(AsyncPlayerChatEvent e) { rec(e); }

    @EventHandler public void on(PlayerMoveEvent e) { rec(e); }

    @EventHandler public void on(PlayerTeleportEvent e) { rec(e); }

    @EventHandler public void on(PlayerRespawnEvent e) { rec(e); }

    @EventHandler public void on(PlayerChangedWorldEvent e) { rec(e); }

    @EventHandler public void on(PlayerGameModeChangeEvent e) { rec(e); }

    @EventHandler public void on(PlayerToggleSneakEvent e) { rec(e); }

    @EventHandler public void on(PlayerToggleSprintEvent e) { rec(e); }

    @EventHandler public void on(PlayerToggleFlightEvent e) { rec(e); }

    @EventHandler public void on(PlayerCommandPreprocessEvent e) { rec(e); }

    @EventHandler public void on(PlayerLevelChangeEvent e) { rec(e); }

    @EventHandler public void on(PlayerExpChangeEvent e) { rec(e); }

    @EventHandler public void on(BlockBreakEvent e) { rec(e); }

    @EventHandler public void on(EntityDamageEvent e) { rec(e); }

    @EventHandler public void on(PluginEnableEvent e) { rec(e); }

    @EventHandler public void on(PluginDisableEvent e) { rec(e); }

    @EventHandler public void on(ServerCommandEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.server.RemoteServerCommandEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.player.AsyncPlayerPreLoginEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.player.PlayerLoginEvent e) { rec(e); }

    @EventHandler public void on(ServerLoadEvent e) { rec(e); }

    @EventHandler public void on(WorldLoadEvent e) { rec(e); }

    @EventHandler public void on(WorldUnloadEvent e) { rec(e); }

    @EventHandler public void on(ChunkLoadEvent e) { rec(e); }

    @EventHandler public void on(ChunkUnloadEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.player.PlayerInteractEntityEvent e) { rec(e); }
}
