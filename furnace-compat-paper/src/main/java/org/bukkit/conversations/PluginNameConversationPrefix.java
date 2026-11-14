// 플러그인 이름 머리말: "<색><이름><구분자>§f". 기본 구분자 " > ", 색 연보라.
package org.bukkit.conversations;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

public class PluginNameConversationPrefix implements ConversationPrefix {

    protected String separator;
    protected ChatColor prefixColor;
    protected Plugin plugin;
    private final String text;

    public PluginNameConversationPrefix(Plugin plugin) { this(plugin, " > ", ChatColor.LIGHT_PURPLE); }

    public PluginNameConversationPrefix(Plugin plugin, String separator, ChatColor prefixColor) {
        this.plugin = plugin;
        this.separator = separator;
        this.prefixColor = prefixColor;
        this.text = prefixColor + plugin.getDescription().getName() + separator + ChatColor.WHITE;   // 만들 때 한 번 정한다
    }

    @Override
    public String getPrefix(ConversationContext context) { return text; }
}
