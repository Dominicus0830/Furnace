package org.bukkit.command;

import java.util.List;
import java.util.Map;
import org.bukkit.Location;

/** 명령 지도(Bukkit CommandMap). 이 서버에서는 {@link SimpleCommandMap} 하나가 Furnace 명령 트리에 이어져 있다. */
public interface CommandMap {

    void registerAll(String fallbackPrefix, List<Command> commands);

    boolean register(String label, String fallbackPrefix, Command command);

    boolean register(String fallbackPrefix, Command command);

    boolean dispatch(CommandSender sender, String cmdLine) throws CommandException;

    void clearCommands();

    Command getCommand(String name);

    List<String> tabComplete(CommandSender sender, String cmdLine) throws IllegalArgumentException;

    List<String> tabComplete(CommandSender sender, String cmdLine, Location location) throws IllegalArgumentException;

    Map<String, Command> getKnownCommands();
}
