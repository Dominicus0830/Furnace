// Furnace 전용 명령 /version, /plugins — 바닐라에 없는 명령이라 BuiltinCommands(바닐라 트리 순서·대조 대상)에 넣지 않고
// 플러그인 명령처럼 서버 기동 때 따로 등록한다(Paper 의 /version·/plugins 와 같은 쓰임, 권한 제한 없음).
// /plugins reload <이름> 은 개발 모드(plugins.dev-mode=true)에서만 보이고 권한 3 이 필요하다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
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
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.Arguments;
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
        })
                // 개발 모드(plugins.dev-mode=true)에서만 보이는 핫 리로드 — 기본 서버의 명령 트리는 그대로다
                .then(literal("reload").requires(s -> server.plugins().devMode() && s.hasPermission(3))
                        .then(argument("plugin", Arguments.word())
                                .suggests((c, b) -> SharedSuggestions.suggest(pluginNames(server), b))
                                .executes(c -> reload(c.getSource(), server, c.getArgument("plugin", String.class))))));
    }

    private static List<String> pluginNames(Furnace server) {
        List<String> out = new ArrayList<>();
        for (FurnacePlugin p : server.plugins().plugins()) out.add(p.name());
        return out;
    }

    private static int reload(CommandSource src, Furnace server, String name) {
        src.sendSuccess(() -> Text.text("플러그인 " + name + " 을(를) 다시 읽는다…"), true);
        server.plugins().reload(name).whenComplete((names, err) -> {
            if (err != null) {
                Throwable t = err instanceof java.util.concurrent.CompletionException && err.getCause() != null ? err.getCause() : err;
                src.sendFailure(Text.text("다시 읽기 실패: " + t.getMessage()));
            } else {
                src.sendSuccess(() -> Text.text(reloadLine(name, names)), true);
            }
        });
        return 1;
    }

    /** 다시 읽기 결과 한 줄. */
    public static String reloadLine(String name, List<String> enabled) {
        if (enabled.isEmpty()) return "플러그인 " + name + " 을(를) 내렸다(켜진 플러그인 없음)";
        return "다시 읽음: " + String.join(", ", enabled);
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
