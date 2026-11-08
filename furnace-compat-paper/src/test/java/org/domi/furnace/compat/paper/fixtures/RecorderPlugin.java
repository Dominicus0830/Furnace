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

    @EventHandler public void on(org.bukkit.event.inventory.InventoryClickEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryDragEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryOpenEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.inventory.InventoryCloseEvent e) { rec(e); }

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

    @EventHandler public void on(ServerLoadEvent e) { rec(e); }

    @EventHandler public void on(WorldLoadEvent e) { rec(e); }

    @EventHandler public void on(WorldUnloadEvent e) { rec(e); }

    @EventHandler public void on(ChunkLoadEvent e) { rec(e); }

    @EventHandler public void on(ChunkUnloadEvent e) { rec(e); }

    @EventHandler public void on(org.bukkit.event.player.PlayerInteractEntityEvent e) { rec(e); }
}
