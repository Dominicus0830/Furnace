// Bukkit TabCompleter.
package org.domi.furnace.compat.command;

import java.util.List;
import org.domi.furnace.api.command.CommandSender;

@FunctionalInterface
public interface LegacyTabCompleter {

    /** @return 후보. null 이면 접속 중인 플레이어 이름으로 기본 완성한다(Bukkit 과 같다). */
    List<String> onTabComplete(CommandSender sender, LegacyCommand command, String alias, String[] args);
}
