package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit 처리기 사이에 Furnace 처리기가 값을 바꿀 때를 보는 플러그인(시험용): LOW·HIGH 는 값을 읽기만 하고, 채팅 LOW 만 서식을 바꾼다. */
public final class InterleavePlugin extends JavaPlugin implements Listener {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @EventHandler(priority = EventPriority.LOW) public void expLow(PlayerExpChangeEvent e) { LOG.add("exp LOW " + e.getAmount()); }

    @EventHandler(priority = EventPriority.HIGH) public void expHigh(PlayerExpChangeEvent e) { LOG.add("exp HIGH " + e.getAmount()); }

    @EventHandler(priority = EventPriority.LOW) public void dmgLow(EntityDamageEvent e) { LOG.add("dmg LOW " + e.getDamage()); }

    @EventHandler(priority = EventPriority.HIGH) public void dmgHigh(EntityDamageEvent e) { LOG.add("dmg HIGH " + e.getDamage()); }

    @EventHandler(priority = EventPriority.LOW) public void joinLow(PlayerJoinEvent e) { LOG.add("join LOW " + e.getJoinMessage()); }

    @EventHandler(priority = EventPriority.HIGH) public void joinHigh(PlayerJoinEvent e) { LOG.add("join HIGH " + e.getJoinMessage()); }

    @EventHandler(priority = EventPriority.LOW) public void cmdLow(PlayerCommandPreprocessEvent e) { LOG.add("cmd LOW " + e.getMessage()); }

    @EventHandler(priority = EventPriority.HIGH) public void cmdHigh(PlayerCommandPreprocessEvent e) { LOG.add("cmd HIGH " + e.getMessage()); }

    @EventHandler(priority = EventPriority.LOW) public void respawnLow(PlayerRespawnEvent e) { LOG.add("respawn LOW " + e.getRespawnLocation().getX()); }

    @EventHandler(priority = EventPriority.HIGH) public void respawnHigh(PlayerRespawnEvent e) { LOG.add("respawn HIGH " + e.getRespawnLocation().getX()); }

    @EventHandler(priority = EventPriority.LOW) public void chatLow(AsyncPlayerChatEvent e) { e.setFormat("[%1$s] %2$s"); LOG.add("chat LOW " + e.getMessage()); }

    @EventHandler(priority = EventPriority.HIGH) public void chatHigh(AsyncPlayerChatEvent e) { LOG.add("chat HIGH " + e.getMessage()); }
}
