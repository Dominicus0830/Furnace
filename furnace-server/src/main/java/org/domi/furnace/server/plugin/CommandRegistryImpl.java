// 플러그인 명령을 서버 명령 트리(Brigadier)에 붙인다: 이름(literal) + 나머지 인자 하나(greedy, 자동완성은 서버에 물음).
// 바닐라·다른 플러그인 명령과 겹치는 이름은 "플러그인:이름" 형태로만 등록된다(Paper 와 같다).
package org.domi.furnace.server.plugin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.command.BasicCommand;
import org.domi.furnace.api.command.CommandRegistry;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CommandRegistryImpl implements CommandRegistry {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    private final PluginHost host;
    /** 플러그인 → 그 플러그인이 등록한 루트 노드 이름들. */
    private final Map<FurnacePlugin, Set<String>> owned = new ConcurrentHashMap<>();

    CommandRegistryImpl(PluginHost host) { this.host = host; }

    private ServerCore core() { return host.core(); }

    private CommandDispatcher<CommandSource> dispatcher() { return core().commands().getDispatcher(); }

    @Override
    public synchronized Collection<String> register(FurnacePlugin plugin, String label, String description, List<String> aliases,
                                                    BasicCommand command) {
        String name = normalize(label);
        List<String> names = new ArrayList<>();
        names.add(name);
        List<String> extra = new ArrayList<>(aliases);
        PluginDescription.CommandInfo info = plugin.description().commands().get(name);
        if (info != null) extra.addAll(info.aliases());
        for (String a : extra) names.add(normalize(a));
        String namespace = plugin.name().toLowerCase(Locale.ROOT).replace(' ', '_');
        BasicCommand effective = info != null && info.permission() != null && command.permission() == null ? withPermission(command, info.permission()) : command;
        Set<String> registered = new LinkedHashSet<>();
        for (String n : names) {
            String ns = namespace + ":" + n;
            if (dispatcher().getRoot().getChild(ns) == null) {
                dispatcher().register(node(ns, effective));
                registered.add(ns);
            }
            if (dispatcher().getRoot().getChild(n) == null) {
                dispatcher().register(node(n, effective));
                registered.add(n);
            } else {
                log.warn("[{}] 명령 '{}' 은(는) 이미 있어 '{}' 로만 등록한다", plugin.name(), n, ns);
            }
        }
        owned.computeIfAbsent(plugin, k -> ConcurrentHashMap.newKeySet()).addAll(registered);
        resendTrees();
        return List.copyOf(registered);
    }

    private static BasicCommand withPermission(BasicCommand c, String permission) {
        return new BasicCommand() {
            public void execute(CommandSender sender, String[] args) { c.execute(sender, args); }
            public List<String> suggest(CommandSender sender, String[] args) { return c.suggest(sender, args); }
            public String permission() { return permission; }
        };
    }

    private static String normalize(String label) {
        String n = label.toLowerCase(Locale.ROOT).trim();
        if (n.isEmpty() || n.indexOf(' ') >= 0 || n.indexOf(':') >= 0) throw new IllegalArgumentException("명령 이름이 잘못됐다: '" + label + "'");
        return n;
    }

    private LiteralArgumentBuilder<CommandSource> node(String name, BasicCommand cmd) {
        return literal(name)
                .requires(src -> cmd.canUse(host.sender(src)))
                .executes(ctx -> run(cmd, ctx, new String[0]))
                .then(argument("args", Arguments.greedyString())
                        .suggests((ctx, b) -> suggest(cmd, ctx, b))
                        .executes(ctx -> run(cmd, ctx, ctx.getArgument("args", String.class).split(" ", -1))));
    }

    private int run(BasicCommand cmd, CommandContext<CommandSource> ctx, String[] args) {
        CommandSender sender = host.sender(ctx.getSource());
        try {
            cmd.execute(sender, args);
        } catch (Throwable t) {
            log.error("Command exception: /{}", ctx.getInput(), t);
            sender.sendMessage(Text.translatable("command.failed").color(org.domi.furnace.api.text.NamedTextColor.RED));
            return 0;
        }
        return 1;
    }

    private CompletableFuture<Suggestions> suggest(BasicCommand cmd, CommandContext<CommandSource> ctx, SuggestionsBuilder b) {
        String remaining = b.getRemaining();
        String[] args = remaining.split(" ", -1);
        List<String> candidates;
        try {
            candidates = cmd.suggest(host.sender(ctx.getSource()), args);
        } catch (Throwable t) {
            log.error("자동완성 실패: /{}", ctx.getInput(), t);
            return Suggestions.empty();
        }
        int lastSpace = remaining.lastIndexOf(' ');
        SuggestionsBuilder off = b.createOffset(b.getStart() + lastSpace + 1);
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(last)) off.suggest(c);
        return off.buildFuture();
    }

    @Override
    public boolean dispatch(CommandSender sender, String commandLine) {
        CommandSource src = host.source(sender);
        return core().commands().performPrefixedCommand(src, commandLine) > 0;
    }

    @Override
    public synchronized void unregisterAll(FurnacePlugin plugin) {
        Set<String> names = owned.remove(plugin);
        if (names == null || names.isEmpty()) return;
        for (String n : names) dispatcher().getRoot().removeChild(n);
        resendTrees();
    }

    /** 접속자 모두에게 바뀐 명령 트리를 다시 보낸다(바닐라 sendCommands). */
    private void resendTrees() {
        for (Player p : core().players()) core().commands().sendCommands(p);
    }
}
