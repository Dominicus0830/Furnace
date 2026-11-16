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
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.i18n.Lang;

public final class FurnaceCommands {

    private FurnaceCommands() {}

    public static void register(CommandDispatcher<CommandSource> d, Furnace server) {
        d.register(literal("version").executes(c -> {
            c.getSource().sendSuccess(() -> Text.text(versionLine(server)), false);
            return 1;
        })
                // Paper·Bukkit 의 /version <플러그인>: 이름이 같은 플러그인(없으면 이름에 그 글자가 든 플러그인 전부)의 버전·설명·웹사이트·작성자
                .then(argument("plugin", Arguments.greedyString())
                        .suggests((c, b) -> SharedSuggestions.suggest(pluginNames(server), b))
                        .executes(c -> {
                            List<org.domi.furnace.api.plugin.PluginDescription> descs = new ArrayList<>();
                            for (FurnacePlugin p : server.plugins().plugins()) descs.add(p.description());
                            List<Text> lines = pluginInfo(descs, c.getArgument("plugin", String.class));
                            for (Text line : lines) c.getSource().sendSuccess(() -> line, false);
                            return lines.isEmpty() ? 0 : 1;
                        })));
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

    /**
     * Bukkit {@code VersionCommand} 와 같은 찾기·출력: 이름이 정확히(대소문자 무시, 공백은 '_' 로 본다) 같은 플러그인이 있으면 그것만,
     * 없으면 이름에 입력이 든(소문자) 플러그인 전부, 하나도 없으면 안내 두 줄. 빈 목록이면 안내 줄을 돌려준다.
     */
    public static List<Text> pluginInfo(List<org.domi.furnace.api.plugin.PluginDescription> plugins, String query) {
        String exact = query.replace(' ', '_');
        List<org.domi.furnace.api.plugin.PluginDescription> found = new ArrayList<>();
        for (var p : plugins) {
            if (p.name().replace(' ', '_').equalsIgnoreCase(exact)) {
                found.add(p);
                break;
            }
        }
        if (found.isEmpty()) {
            String lower = query.toLowerCase(Locale.ROOT);
            for (var p : plugins) if (p.name().toLowerCase(Locale.ROOT).contains(lower)) found.add(p);
        }
        List<Text> out = new ArrayList<>();
        if (found.isEmpty()) {
            for (String line : Lang.t("cmd.furnace.version-none").split("\n")) out.add(Text.text(line));
            return out;
        }
        for (var p : found) describe(p, out);
        return out;
    }

    private static void describe(org.domi.furnace.api.plugin.PluginDescription d, List<Text> out) {
        out.add(Text.join(Text.text(d.name().replace(' ', '_'), NamedTextColor.GREEN), Text.text(Lang.t("cmd.furnace.version-of"), NamedTextColor.WHITE),
                Text.text(d.version(), NamedTextColor.GREEN)));
        if (d.description() != null && !d.description().isEmpty()) out.add(Text.text(d.description()));
        if (d.website() != null && !d.website().isEmpty())
            out.add(Text.join(Text.text(Lang.t("cmd.furnace.version-website"), NamedTextColor.WHITE), Text.text(d.website(), NamedTextColor.GREEN)));
        List<String> authors = d.authors();
        if (!authors.isEmpty()) {
            List<Text> parts = new ArrayList<>();
            parts.add(Text.text(Lang.t(authors.size() == 1 ? "cmd.furnace.version-author" : "cmd.furnace.version-authors"), NamedTextColor.WHITE));
            for (int i = 0; i < authors.size(); i++) {
                if (i > 0) parts.add(Text.text(i < authors.size() - 1 ? ", " : Lang.t("cmd.furnace.version-and"), NamedTextColor.WHITE));
                parts.add(Text.text(authors.get(i), NamedTextColor.GREEN));
            }
            out.add(Text.join(parts.toArray(new Text[0])));
        }
    }

    private static List<String> pluginNames(Furnace server) {
        List<String> out = new ArrayList<>();
        for (FurnacePlugin p : server.plugins().plugins()) out.add(p.name());
        return out;
    }

    private static int reload(CommandSource src, Furnace server, String name) {
        src.sendSuccess(() -> Text.text(Lang.t("cmd.furnace.reloading", name)), true);
        server.plugins().reload(name).whenComplete((names, err) -> {
            if (err != null) {
                Throwable t = err instanceof java.util.concurrent.CompletionException && err.getCause() != null ? err.getCause() : err;
                src.sendFailure(Text.text(Lang.t("cmd.furnace.reload-failed", t.getMessage())));
            } else {
                src.sendSuccess(() -> Text.text(reloadLine(name, names)), true);
            }
        });
        return 1;
    }

    /** 다시 읽기 결과 한 줄. */
    public static String reloadLine(String name, List<String> enabled) {
        if (enabled.isEmpty()) return Lang.t("cmd.furnace.unloaded", name);
        return Lang.t("cmd.furnace.reloaded", String.join(", ", enabled));
    }

    public static String versionLine(Furnace server) {
        return server.name() + " " + server.version() + " (Minecraft " + server.minecraftVersion() + ")";
    }

    /** "플러그인 (2): A, B" 한 줄. 꺼진 플러그인은 이름 뒤에 (꺼짐). */
    public static String pluginsLine(List<FurnacePlugin> list, PluginManager pm) {
        StringBuilder sb = new StringBuilder(Lang.t("cmd.furnace.plugins-head", list.size()));
        for (int i = 0; i < list.size(); i++) {
            FurnacePlugin p = list.get(i);
            sb.append(i == 0 ? ": " : ", ").append(p.name());
            if (!pm.isEnabled(p.name())) sb.append(Lang.t("cmd.furnace.plugin-disabled"));
        }
        return sb.toString();
    }
}
