// Furnace 전용 명령 /version, /plugins — 바닐라에 없는 명령이라 BuiltinCommands(바닐라 트리 순서·대조 대상)에 넣지 않고
// 플러그인 명령처럼 서버 기동 때 따로 등록한다(Paper 의 /version·/plugins 와 같은 쓰임, 권한 제한 없음).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginManager;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;

public final class FurnaceCommands {

    private FurnaceCommands() {}

    public static void register(CommandDispatcher<CommandSource> d, Furnace server) {
        d.register(literal("version").executes(c -> {
            c.getSource().sendSuccess(() -> Text.text(versionLine(server)), false);
            return 1;
        }));
        d.register(literal("plugins").executes(c -> {
            PluginManager pm = server.plugins();
            List<FurnacePlugin> list = new ArrayList<>(pm.plugins());
            list.sort(Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT)));
            c.getSource().sendSuccess(() -> Text.text(pluginsLine(list, pm)), false);
            return list.size();
        }));
    }

    public static String versionLine(Furnace server) {
        return server.name() + " " + server.version() + " (Minecraft " + server.minecraftVersion() + ")";
    }

    /** "플러그인 (2): A, B" 한 줄. 꺼진 플러그인은 이름 뒤에 (꺼짐). */
    public static String pluginsLine(List<FurnacePlugin> list, PluginManager pm) {
        StringBuilder sb = new StringBuilder("플러그인 (").append(list.size()).append(")");
        for (int i = 0; i < list.size(); i++) {
            FurnacePlugin p = list.get(i);
            sb.append(i == 0 ? ": " : ", ").append(p.name());
            if (!pm.isEnabled(p.name())) sb.append("(꺼짐)");
        }
        return sb.toString();
    }
}
