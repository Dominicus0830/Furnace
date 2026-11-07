package org.bukkit.command;

/** 명령 실행기(Bukkit CommandExecutor). false 를 돌려주면 사용법(usage)이 나간다. */
public interface CommandExecutor {

    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}
