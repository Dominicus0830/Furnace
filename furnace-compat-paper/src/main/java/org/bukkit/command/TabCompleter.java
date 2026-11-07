package org.bukkit.command;

import java.util.List;

/** 자동완성기(Bukkit TabCompleter). null 을 돌려주면 접속자 이름으로 기본 완성한다. */
public interface TabCompleter {

    List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);
}
