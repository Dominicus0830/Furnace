// /datapack · /reload (바닐라 DataPackCommand · ReloadCommand).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.FeatureFlags;
import org.domi.furnace.server.data.Pack;
import org.domi.furnace.server.data.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DataPackCommands {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PACK = new DynamicCommandExceptionType(p -> Text.translatable("commands.datapack.unknown", p));
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_ENABLED = new DynamicCommandExceptionType(p -> Text.translatable("commands.datapack.enable.failed", p));
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_DISABLED = new DynamicCommandExceptionType(p -> Text.translatable("commands.datapack.disable.failed", p));
    private static final DynamicCommandExceptionType ERROR_CANNOT_DISABLE_FEATURE = new DynamicCommandExceptionType(p -> Text.translatable("commands.datapack.disable.failed.feature", p));
    private static final Dynamic2CommandExceptionType ERROR_PACK_FEATURES_NOT_ENABLED =
        new Dynamic2CommandExceptionType((p, f) -> Text.translatable("commands.datapack.enable.failed.no_flags", p, f));

    private DataPackCommands() {}

    @FunctionalInterface
    private interface Inserter { void apply(List<Pack> list, Pack pack) throws CommandSyntaxException; }

    private static DataPackManager dp(CommandSource s) { return s.getServer().dataPacks(); }

    private static final SuggestionProvider<CommandSource> SELECTED_PACKS = (c, b) -> {
        DataPackManager m = dp(c.getSource());
        return SharedSuggestions.suggest(m == null ? List.of() : m.repository().selectedIds().stream().map(DataPackCommands::escape).toList(), b);
    };

    private static final SuggestionProvider<CommandSource> UNSELECTED_PACKS = (c, b) -> {
        DataPackManager m = dp(c.getSource());
        if (m == null) return SharedSuggestions.suggest(List.<String>of(), b);
        Collection<String> sel = m.repository().selectedIds();
        Set<String> features = m.worldFeatures();
        return SharedSuggestions.suggest(m.repository().available().stream()
            .filter(p -> FeatureFlags.isSubset(p.requestedFeatures(), features)).map(Pack::id)
            .filter(id -> !sel.contains(id)).map(DataPackCommands::escape).toList(), b);
    };

    public static void registerDataPack(CommandDispatcher<CommandSource> d) {
        d.register(literal("datapack").requires(s -> s.hasPermission(2))
            .then(literal("enable")
                .then(argument("name", Arguments.string()).suggests(UNSELECTED_PACKS)
                    .executes(c -> enable(c.getSource(), getPack(c, "name", true), (l, p) -> Pack.insert(l, p, false)))
                    .then(literal("after").then(argument("existing", Arguments.string()).suggests(SELECTED_PACKS)
                        .executes(c -> enable(c.getSource(), getPack(c, "name", true),
                            (l, p) -> l.add(l.indexOf(getPack(c, "existing", false)) + 1, p)))))
                    .then(literal("before").then(argument("existing", Arguments.string()).suggests(SELECTED_PACKS)
                        .executes(c -> enable(c.getSource(), getPack(c, "name", true),
                            (l, p) -> l.add(l.indexOf(getPack(c, "existing", false)), p)))))
                    .then(literal("last").executes(c -> enable(c.getSource(), getPack(c, "name", true), List::add)))
                    .then(literal("first").executes(c -> enable(c.getSource(), getPack(c, "name", true), (l, p) -> l.add(0, p))))))
            .then(literal("disable")
                .then(argument("name", Arguments.string()).suggests(SELECTED_PACKS)
                    .executes(c -> disable(c.getSource(), getPack(c, "name", false)))))
            .then(literal("list").executes(c -> listPacks(c.getSource()))
                .then(literal("available").executes(c -> listAvailablePacks(c.getSource())))
                .then(literal("enabled").executes(c -> listEnabledPacks(c.getSource())))));
    }

    public static void registerReload(CommandDispatcher<CommandSource> d) {
        d.register(literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
            CommandSource s = c.getSource();
            DataPackManager m = dp(s);
            s.sendSuccess(() -> Text.translatable("commands.reload.success"), true);
            if (m != null) reloadPacks(m.discoverNewPacks(m.repository().selectedIds()), s);
            return 0;
        }));
    }

    /** 바닐라 ReloadCommand.reloadPacks. */
    static void reloadPacks(Collection<String> ids, CommandSource s) {
        DataPackManager m = dp(s);
        if (m == null) return;
        m.reload(ids, ForkJoinPool.commonPool()).exceptionally(e -> {
            log.warn("Failed to execute reload", e);
            s.sendFailure(Text.translatable("commands.reload.failure"));
            return null;
        });
    }

    private static int enable(CommandSource s, Pack pack, Inserter inserter) throws CommandSyntaxException {
        DataPackManager m = dp(s);
        List<Pack> list = new ArrayList<>(m.repository().selected());
        inserter.apply(list, pack);
        s.sendSuccess(() -> Text.translatable("commands.datapack.modify.enable", pack.chatLink(true)), true);
        reloadPacks(list.stream().map(Pack::id).toList(), s);
        return list.size();
    }

    private static int disable(CommandSource s, Pack pack) {
        DataPackManager m = dp(s);
        List<Pack> list = new ArrayList<>(m.repository().selected());
        list.remove(pack);
        s.sendSuccess(() -> Text.translatable("commands.datapack.modify.disable", pack.chatLink(true)), true);
        reloadPacks(list.stream().map(Pack::id).toList(), s);
        return list.size();
    }

    private static int listPacks(CommandSource s) { return listEnabledPacks(s) + listAvailablePacks(s); }

    private static int listAvailablePacks(CommandSource s) {
        DataPackManager m = dp(s);
        if (m == null) return 0;
        PackRepository repo = m.repository();
        repo.reload();
        List<Pack> selected = repo.selected();
        Set<String> features = m.worldFeatures();
        List<Pack> avail = repo.available().stream()
            .filter(p -> !selected.contains(p) && FeatureFlags.isSubset(p.requestedFeatures(), features)).toList();
        if (avail.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.datapack.list.available.none"), false);
        } else {
            s.sendSuccess(() -> Text.translatable("commands.datapack.list.available.success", avail.size(),
                formatList(avail.stream().map(p -> p.chatLink(false)).toList())), false);
        }
        return avail.size();
    }

    private static int listEnabledPacks(CommandSource s) {
        DataPackManager m = dp(s);
        if (m == null) return 0;
        PackRepository repo = m.repository();
        repo.reload();
        List<Pack> selected = repo.selected();
        if (selected.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.datapack.list.enabled.none"), false);
        } else {
            s.sendSuccess(() -> Text.translatable("commands.datapack.list.enabled.success", selected.size(),
                formatList(selected.stream().map(p -> p.chatLink(true)).toList())), false);
        }
        return selected.size();
    }

    /** 바닐라 ComponentUtils.formatList — 회색 ", " 로 잇는다. */
    static Text formatList(List<Text> items) {
        if (items.isEmpty()) return Text.empty();
        if (items.size() == 1) return items.get(0);
        Text sep = Text.text(", ").color(NamedTextColor.GRAY);
        Text out = Text.empty();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out = out.append(sep);
            out = out.append(items.get(i));
        }
        return out;
    }

    private static Pack getPack(CommandContext<CommandSource> c, String arg, boolean enabling) throws CommandSyntaxException {
        String name = c.getArgument(arg, String.class);
        DataPackManager m = dp(c.getSource());
        Pack pack = m == null ? null : m.repository().pack(name);
        if (pack == null) throw ERROR_UNKNOWN_PACK.create(name);
        boolean selected = m.repository().selected().contains(pack);
        if (enabling && selected) throw ERROR_PACK_ALREADY_ENABLED.create(name);
        if (!enabling && !selected) throw ERROR_PACK_ALREADY_DISABLED.create(name);
        Set<String> world = m.worldFeatures();
        Set<String> req = pack.requestedFeatures();
        if (!enabling && !req.isEmpty() && pack.source() == Pack.Source.FEATURE) throw ERROR_CANNOT_DISABLE_FEATURE.create(name);
        if (!FeatureFlags.isSubset(req, world)) throw ERROR_PACK_FEATURES_NOT_ENABLED.create(name, FeatureFlags.printMissing(world, req));
        return pack;
    }

    private static String escape(String id) {
        for (int i = 0; i < id.length(); i++) {
            char ch = id.charAt(i);
            if (!(ch >= '0' && ch <= '9' || ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_' || ch == '-' || ch == '.' || ch == '+')) {
                return "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return id;
    }
}
