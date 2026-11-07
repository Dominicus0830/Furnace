// 서비스 지도(SimpleServicesManager)를 플러그인 수명에 묶는다: 서비스를 등록한 플러그인이 꺼지면 그 등록을 모두 푼다(Bukkit disablePlugin 과 같다).
package org.domi.furnace.compat.paper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimpleServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;

public final class CompatServices {

    private static final Set<Object> HOOKED = ConcurrentHashMap.newKeySet();

    private CompatServices() {}

    /** plugin 이 처음 서비스를 등록할 때 한 번 건다. Furnace 가 관리하는 Bukkit 플러그인이 아니면 걸 곳이 없어 건너뛴다. */
    public static void ownedBy(Plugin plugin, SimpleServicesManager manager) {
        if (!(plugin instanceof JavaPlugin jp)) return;
        FurnacePlugin owner = Furnace.server().plugins().plugin(jp.getName());
        if (!(owner instanceof BukkitPluginAdapter a) || a.javaPlugin() != jp) return;
        if (!HOOKED.add(a)) return;
        a.own(() -> {
            HOOKED.remove(a);
            manager.unregisterAll(jp);
        });
    }
}
