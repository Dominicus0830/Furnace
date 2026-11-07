package org.bukkit.command;

import org.bukkit.plugin.Plugin;

/** 어느 플러그인의 명령인지 아는 명령. 명령 지도는 이것으로 소유 플러그인을 정한다. */
public interface PluginIdentifiableCommand {

    Plugin getPlugin();
}
