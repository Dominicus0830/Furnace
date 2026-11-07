package org.bukkit.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** plugin.yml 의 {@code commands:} 를 {@link PluginCommand} 로 만든다(Bukkit PluginCommandYamlParser). */
public final class PluginCommandYamlParser {

    private PluginCommandYamlParser() {}

    public static List<Command> parse(Plugin plugin) {
        List<Command> out = new ArrayList<>();
        Map<String, Map<String, Object>> map = plugin.getDescription().getCommands();
        if (map == null) return out;
        for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
            if (entry.getKey().contains(":")) {
                Bukkit.getServer().getLogger().severe("Could not load command " + entry.getKey() + " for plugin " + plugin.getName() + ": Illegal Characters");
                continue;
            }
            Command newCmd = new PluginCommand(entry.getKey(), plugin);
            Object description = entry.getValue().get("description");
            Object usage = entry.getValue().get("usage");
            Object aliases = entry.getValue().get("aliases");
            Object permission = entry.getValue().get("permission");
            Object permissionMessage = entry.getValue().get("permission-message");
            if (description != null) newCmd.setDescription(description.toString());
            if (usage != null) newCmd.setUsage(usage.toString());
            if (aliases != null) {
                List<String> aliasList = new ArrayList<>();
                if (aliases instanceof List<?> list) {
                    for (Object o : list) {
                        if (o.toString().contains(":")) {
                            Bukkit.getServer().getLogger().severe("Could not load alias " + o + " for plugin " + plugin.getName() + ": Illegal Characters");
                            continue;
                        }
                        aliasList.add(o.toString());
                    }
                } else if (aliases.toString().contains(":")) {
                    Bukkit.getServer().getLogger().severe("Could not load alias " + aliases + " for plugin " + plugin.getName() + ": Illegal Characters");
                } else {
                    aliasList.add(aliases.toString());
                }
                newCmd.setAliases(aliasList);
            }
            if (permission != null) newCmd.setPermission(permission.toString());
            if (permissionMessage != null) newCmd.setPermissionMessage(permissionMessage.toString());
            out.add(newCmd);
        }
        return out;
    }
}
