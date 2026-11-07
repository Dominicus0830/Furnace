package org.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

/** 서버. 1차 호환 범위 — 스케줄러·명령 등록(H2), 인벤토리·아이템 만들기(H3)는 아직 없다. */
public interface Server {

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
}
