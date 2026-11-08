package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 플러그인 메시징 시험용: 채널 등록은 시험이 이 플러그인 이름으로 하고, 이것은 PlayerRegisterChannelEvent 만 받아 적는다. */
public final class MessagingPlugin extends JavaPlugin implements Listener {

    /** "플레이어 이름 채널" 순서대로. */
    public static final List<String> REGISTERED = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    /** 같은 처리기 목록을 쓰는 PlayerUnregisterChannelEvent 는 오지 않아야 한다(실행기가 걸러 낸다). */
    @EventHandler
    public void on(PlayerRegisterChannelEvent e) { REGISTERED.add(e.getPlayer().getName() + " " + e.getChannel()); }
}
