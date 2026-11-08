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
import org.domi.furnace.api.command.CommandBuilder;
import org.domi.furnace.api.command.CommandRegistry;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.Timing;
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
        PluginDescription.CommandInfo info = plugin.description().commands().get(name);
        BasicCommand effective = info != null && info.permission() != null && command.permission() == null ? withPermission(command, info.permission()) : command;
        return registerNames(plugin, name, aliases, (n, ns) -> node(plugin, ns, n, effective));
    }

    @Override
    public synchronized Collection<String> register(FurnacePlugin plugin, CommandBuilder root, String description, List<String> aliases) {
        if (!root.isLiteral()) throw new IllegalArgumentException("명령 트리의 루트는 literal 이어야 한다");
        String name = normalize(root.name());
        return registerNames(plugin, name, aliases, (n, ns) -> treeNode(plugin, ns, n, root));
    }

    /** 이름·별칭·"플러그인:이름" 을 트리에 붙이고 접속자에게 다시 보낸다. build(짧은 이름, 트리에 붙는 이름). */
    private Collection<String> registerNames(FurnacePlugin plugin, String name, List<String> aliases,
                                             java.util.function.BiFunction<String, String, LiteralArgumentBuilder<CommandSource>> build) {
        Set<String> names = new LinkedHashSet<>();   // plugin.yml 의 별칭과 인자로 받은 별칭이 겹쳐도 한 번만(겹침 경고를 헛으로 내지 않는다)
        names.add(name);
        List<String> extra = new ArrayList<>(aliases);
        PluginDescription.CommandInfo info = plugin.description().commands().get(name);
        if (info != null) extra.addAll(info.aliases());
        for (String a : extra) names.add(normalize(a));
        String namespace = plugin.name().toLowerCase(Locale.ROOT).replace(' ', '_');
        Set<String> registered = new LinkedHashSet<>();
        for (String n : names) {
            String ns = namespace + ":" + n;
            boolean nsFree = dispatcher().getRoot().getChild(ns) == null;
            if (nsFree) {
                dispatcher().register(build.apply(n, ns));
                registered.add(ns);
            }
            if (dispatcher().getRoot().getChild(n) == null) {
                dispatcher().register(build.apply(n, n));
                registered.add(n);
            } else if (nsFree) {
                log.warn("[{}] 명령 '{}' 은(는) 이미 있어 '{}' 로만 등록한다", plugin.name(), n, ns);
            } else {
                // 플러그인 이름이 minecraft 이면 '플러그인:이름' 이 바닐라 별칭(minecraft:give)과 겹쳐 둘 다 못 쓴다
                log.warn("[{}] 명령 '{}' 과(와) '{}' 이(가) 둘 다 이미 있어 등록하지 않는다", plugin.name(), n, ns);
            }
        }
        owned.computeIfAbsent(plugin, k -> ConcurrentHashMap.newKeySet()).addAll(registered);
        resendTrees();
        return List.copyOf(registered);
    }

    // ---- API 트리 → 서버 Brigadier 노드

    private LiteralArgumentBuilder<CommandSource> treeNode(FurnacePlugin plugin, String name, String label, CommandBuilder spec) {
        Timing exec = host.profiler().timing(plugin, PluginProfiler.Kind.COMMAND, "/" + label);
        Timing tab = host.profiler().timing(plugin, PluginProfiler.Kind.COMMAND, "/" + label + " (자동완성)");
        LiteralArgumentBuilder<CommandSource> b = literal(name);
        fill(b, spec, exec, tab);
        return b;
    }

    private void fill(org.domi.furnace.server.command.brigadier.ArgumentBuilder<CommandSource, ?> b, CommandBuilder spec, Timing exec, Timing tab) {
        if (spec.requirement() != null) b.requires(src -> spec.requirement().test(host.sender(src)));
        if (spec.executor() != null) b.executes(ctx -> runTree(spec.executor(), exec, ctx));
        for (CommandBuilder child : spec.children()) {
            org.domi.furnace.server.command.brigadier.ArgumentBuilder<CommandSource, ?> cb;
            if (child.isLiteral()) {
                cb = literal(child.name());
            } else {
                org.domi.furnace.server.command.brigadier.RequiredArgumentBuilder<CommandSource, ?> rb = argument(child.name(), argType(child.type()));
                if (child.suggester() != null) {
                    CommandBuilder.Suggester sg = child.suggester();
                    rb.suggests((ctx, sb) -> {
                        long t0 = tab.start();
                        try {
                            for (String c : sg.suggest(host.sender(ctx.getSource()), sb.getRemaining())) {
                                if (c.toLowerCase(Locale.ROOT).startsWith(sb.getRemainingLowerCase())) sb.suggest(c);
                            }
                        } catch (Throwable t) {
                            log.error("자동완성 실패: /{}", ctx.getInput(), t);
                        } finally {
                            tab.stop(t0);
                        }
                        return sb.buildFuture();
                    });
                }
                cb = rb;
            }
            fill(cb, child, exec, tab);
            b.then(cb);
        }
    }

    private static org.domi.furnace.server.command.brigadier.ArgumentType<?> argType(CommandBuilder.ArgType t) {
        return switch (t) {
            case CommandBuilder.ArgType.Bool b -> Arguments.bool();
            case CommandBuilder.ArgType.Int i -> Arguments.integer(i.min(), i.max());
            case CommandBuilder.ArgType.Dbl d -> Arguments.doubleArg(d.min(), d.max());
            case CommandBuilder.ArgType.Str s -> switch (s.kind()) {
                case WORD -> Arguments.word();
                case QUOTABLE -> Arguments.string();
                case GREEDY -> Arguments.greedyString();
            };
        };
    }

    private int runTree(CommandBuilder.Executor ex, Timing timing, CommandContext<CommandSource> ctx) {
        CommandSender sender = host.sender(ctx.getSource());
        CommandBuilder.Context c = new CommandBuilder.Context() {
            public CommandSender sender() { return sender; }
            public String input() { return ctx.getInput(); }
            public int getInt(String n) { return ctx.getArgument(n, Integer.class); }
            public double getDouble(String n) { return ctx.getArgument(n, Double.class); }
            public boolean getBoolean(String n) { return ctx.getArgument(n, Boolean.class); }
            public String getString(String n) { return ctx.getArgument(n, String.class); }
        };
        long t0 = timing.start();
        try {
            return ex.run(c);
        } catch (Throwable t) {
            log.error("Command exception: /{}", ctx.getInput(), t);
            sender.sendMessage(Text.translatable("command.failed").color(org.domi.furnace.api.text.NamedTextColor.RED));
            return 0;
        } finally {
            timing.stop(t0);
        }
    }

    @Override
    public synchronized boolean unregister(FurnacePlugin plugin, String name) {
        Set<String> names = owned.get(plugin);
        if (names == null || !names.remove(name)) return false;
        dispatcher().getRoot().removeChild(name);
        resendTrees();
        return true;
    }

    @Override
    public void updateCommands(CommandSender sender) {
        if (host.source(sender).getPlayer() != null) core().commands().sendCommands(host.source(sender).getPlayer());
    }

    @Override
    public void updateCommands() { resendTrees(); }

    private static BasicCommand withPermission(BasicCommand c, String permission) {
        return new BasicCommand() {
            public void execute(CommandSender sender, String[] args) { c.execute(sender, args); }
            public void execute(CommandSender sender, String label, String[] args) { c.execute(sender, label, args); }
            public List<String> suggest(CommandSender sender, String[] args) { return c.suggest(sender, args); }
            public List<String> suggest(CommandSender sender, String label, String[] args) { return c.suggest(sender, label, args); }
            public String permission() { return permission; }
        };
    }

    private static String normalize(String label) {
        String n = label.toLowerCase(Locale.ROOT).trim();
        if (n.isEmpty() || n.indexOf(' ') >= 0 || n.indexOf(':') >= 0) throw new IllegalArgumentException("명령 이름이 잘못됐다: '" + label + "'");
        return n;
    }

    /** name 은 트리에 붙는 이름, label 은 /timings 에 보일 이름(플러그인: 접두어 없이 — 두 이름의 시간을 한 줄로 모은다). */
    private LiteralArgumentBuilder<CommandSource> node(FurnacePlugin plugin, String name, String label, BasicCommand cmd) {
        Timing exec = host.profiler().timing(plugin, PluginProfiler.Kind.COMMAND, "/" + label);
        Timing tab = host.profiler().timing(plugin, PluginProfiler.Kind.COMMAND, "/" + label + " (자동완성)");
        return literal(name)
                .requires(src -> cmd.canUse(host.sender(src)))
                .executes(ctx -> run(cmd, name, exec, ctx, new String[0]))
                .then(argument("args", Arguments.greedyString())
                        .suggests((ctx, b) -> suggest(cmd, name, tab, ctx, b))
                        .executes(ctx -> run(cmd, name, exec, ctx, ctx.getArgument("args", String.class).split(" ", -1))));
    }

    /** label 은 트리에 붙은 이름(사용자가 친 이름 — 별칭이나 "플러그인:이름" 일 수 있다). */
    private int run(BasicCommand cmd, String label, Timing timing, CommandContext<CommandSource> ctx, String[] args) {
        CommandSender sender = host.sender(ctx.getSource());
        long t0 = timing.start();
        try {
            cmd.execute(sender, label, args);
        } catch (Throwable t) {
            log.error("Command exception: /{}", ctx.getInput(), t);
            sender.sendMessage(Text.translatable("command.failed").color(org.domi.furnace.api.text.NamedTextColor.RED));
            return 0;
        } finally {
            timing.stop(t0);
        }
        return 1;
    }

    private CompletableFuture<Suggestions> suggest(BasicCommand cmd, String label, Timing timing, CommandContext<CommandSource> ctx, SuggestionsBuilder b) {
        String remaining = b.getRemaining();
        String[] args = remaining.split(" ", -1);
        List<String> candidates;
        long t0 = timing.start();
        try {
            candidates = cmd.suggest(host.sender(ctx.getSource()), label, args);
        } catch (Throwable t) {
            log.error("자동완성 실패: /{}", ctx.getInput(), t);
            return Suggestions.empty();
        } finally {
            timing.stop(t0);
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
        // 결과는 콜백으로 흐르므로 여기서는 "큐에 들어갔는가"(접수) 만 돌려준다. 전역 스레드가 아니면 전역으로 넘긴다.
        core().commands().performPrefixedCommand(src, commandLine);
        return true;
    }

    @Override
    public boolean hasCommand(String name) { return dispatcher().getRoot().getChild(name) != null; }

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
