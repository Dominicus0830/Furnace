// Bukkit JavaPlugin 하나를 Furnace 플러그인으로 감싼다. Furnace 는 이것을 다른 플러그인처럼 init·onLoad·onEnable·onDisable 로 다루고,
// 꺼질 때 구독·예약 작업·명령·권한 첨부를 알아서 정리한다(Bukkit 처리기 목록은 여기서 비운다).
package org.domi.furnace.compat.paper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.api.plugin.FurnacePlugin;

public final class BukkitPluginAdapter extends FurnacePlugin {

    private final PluginDescriptionFile bukkitDescription;
    private volatile JavaPlugin plugin;
    /** 이미 Furnace 이벤트 버스에 구독한 (Bukkit 이벤트, 우선순위) 쌍. */
    private final Set<String> bridged = ConcurrentHashMap.newKeySet();

    BukkitPluginAdapter(PluginDescriptionFile bukkitDescription) { this.bukkitDescription = bukkitDescription; }

    void attach(JavaPlugin plugin) { this.plugin = plugin; }

    /** 이 껍데기가 감싼 Bukkit 플러그인. */
    public JavaPlugin javaPlugin() { return plugin; }

    public PluginDescriptionFile bukkitDescription() { return bukkitDescription; }

    boolean markBridged(String key) { return bridged.add(key); }

    @Override
    public void onLoad() {
        if (bukkitDescription.getAPIVersion() == null) {
            logger().warning("plugin.yml 에 api-version 이 없다 — 1.13 이전 레거시 플러그인으로 보이지만 Furnace 는 레거시 Material 변환을 하지 않는다. "
                    + "api-version: '1.21' 을 적어 다시 빌드하라");
        }
        plugin.onLoad();
    }

    /** Bukkit 처럼 켜기 직전에 plugin.yml 의 명령을 만들어 명령 지도에 올린다 — 그래서 onLoad 에서는 getCommand 가 null 이다. */
    @Override
    public void onEnable() {
        List<Command> declared = PluginCommandYamlParser.parse(plugin);
        if (!declared.isEmpty()) Compat.commandMap().registerAll(bukkitDescription.getName(), declared);
        plugin.onEnable();
    }

    @Override
    public void onDisable() {
        try {
            plugin.onDisable();
        } finally {
            HandlerList.unregisterAll(plugin);
            bridged.clear();
            PluginLoaders.unbind(this);
        }
    }
}
