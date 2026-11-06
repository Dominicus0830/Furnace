// 바닐라 BossBarCommands — /bossbar add|remove|list|set|get.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.bossbar.CustomBossEvent;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.ComponentUtils;
import org.domi.furnace.server.command.arguments.ComponentArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Player;

public final class BossBarCommands {

    private BossBarCommands() {}

    private static final DynamicCommandExceptionType ERROR_ALREADY_EXISTS = new DynamicCommandExceptionType(o -> Text.translatable("commands.bossbar.create.failed", o));
    static final DynamicCommandExceptionType ERROR_DOESNT_EXIST = new DynamicCommandExceptionType(o -> Text.translatable("commands.bossbar.unknown", o));
    private static final SimpleCommandExceptionType ERROR_NO_PLAYER_CHANGE = err("commands.bossbar.set.players.unchanged");
    private static final SimpleCommandExceptionType ERROR_NO_NAME_CHANGE = err("commands.bossbar.set.name.unchanged");
    private static final SimpleCommandExceptionType ERROR_NO_COLOR_CHANGE = err("commands.bossbar.set.color.unchanged");
    private static final SimpleCommandExceptionType ERROR_NO_STYLE_CHANGE = err("commands.bossbar.set.style.unchanged");
    private static final SimpleCommandExceptionType ERROR_NO_VALUE_CHANGE = err("commands.bossbar.set.value.unchanged");
    private static final SimpleCommandExceptionType ERROR_NO_MAX_CHANGE = err("commands.bossbar.set.max.unchanged");
    private static final SimpleCommandExceptionType ERROR_ALREADY_HIDDEN = err("commands.bossbar.set.visibility.unchanged.hidden");
    private static final SimpleCommandExceptionType ERROR_ALREADY_VISIBLE = err("commands.bossbar.set.visibility.unchanged.visible");

    private static SimpleCommandExceptionType err(String key) { return new SimpleCommandExceptionType(Text.translatable(key)); }

    public static void register(CommandDispatcher<CommandSource> d) {
        var setColor = literal("color");
        for (CustomBossEvent.Color color : CustomBossEvent.Color.values()) {
            setColor.then(literal(color.key()).executes(c -> setColor(c.getSource(), bar(c), color)));
        }
        var setStyle = literal("style");
        for (CustomBossEvent.Overlay o : CustomBossEvent.Overlay.values()) {
            setStyle.then(literal(o.key()).executes(c -> setStyle(c.getSource(), bar(c), o)));
        }
        d.register(literal("bossbar").requires(s -> s.hasPermission(2))
                .then(literal("add").then(argument("id", ResourceLocationArgument.id())
                        .then(argument("name", ComponentArgument.textComponent()).executes(c ->
                                create(c.getSource(), ResourceLocationArgument.getId(c, "id"), ComponentArgument.getComponent(c, "name"))))))
                .then(literal("remove").then(argument("id", ResourceLocationArgument.id()).executes(c -> remove(c.getSource(), bar(c)))))
                .then(literal("list").executes(c -> list(c.getSource())))
                .then(literal("set").then(argument("id", ResourceLocationArgument.id())
                        .then(literal("name").then(argument("name", ComponentArgument.textComponent()).executes(c ->
                                setName(c.getSource(), bar(c), ComponentArgument.getComponent(c, "name")))))
                        .then(setColor)
                        .then(setStyle)
                        .then(literal("value").then(argument("value", Arguments.integer(0)).executes(c ->
                                setValue(c.getSource(), bar(c), c.getArgument("value", Integer.class)))))
                        .then(literal("max").then(argument("max", Arguments.integer(1)).executes(c ->
                                setMax(c.getSource(), bar(c), c.getArgument("max", Integer.class)))))
                        .then(literal("visible").then(argument("visible", Arguments.bool()).executes(c ->
                                setVisible(c.getSource(), bar(c), c.getArgument("visible", Boolean.class)))))
                        .then(literal("players").executes(c -> setPlayers(c.getSource(), bar(c), List.of()))
                                .then(argument("targets", EntityArgument.players()).executes(c ->
                                        setPlayers(c.getSource(), bar(c), EntityArgument.getPlayers(c, "targets")))))))
                .then(literal("get").then(argument("id", ResourceLocationArgument.id())
                        .then(literal("value").executes(c -> getValue(c.getSource(), bar(c))))
                        .then(literal("max").executes(c -> getMax(c.getSource(), bar(c))))
                        .then(literal("visible").executes(c -> getVisible(c.getSource(), bar(c))))
                        .then(literal("players").executes(c -> getPlayers(c.getSource(), bar(c)))))));
    }

    private static CustomBossEvent bar(CommandContext<CommandSource> c) throws CommandSyntaxException {
        ResourceId id = ResourceLocationArgument.getId(c, "id");
        CustomBossEvent e = c.getSource().getServer().customBossEvents().get(id);
        if (e == null) throw ERROR_DOESNT_EXIST.create(id.toString());
        return e;
    }

    static int create(CommandSource s, ResourceId id, Text name) throws CommandSyntaxException {
        var events = s.getServer().customBossEvents();
        if (events.get(id) != null) throw ERROR_ALREADY_EXISTS.create(id.toString());
        CustomBossEvent e = events.create(id, ComponentUtils.updateForEntity(s, name, null, 0));
        s.sendSuccess(() -> Text.translatable("commands.bossbar.create.success", e.displayName()), true);
        return events.events().size();
    }

    static int remove(CommandSource s, CustomBossEvent e) {
        s.getServer().customBossEvents().remove(e);
        s.sendSuccess(() -> Text.translatable("commands.bossbar.remove.success", e.displayName()), true);
        return s.getServer().customBossEvents().events().size();
    }

    static int list(CommandSource s) {
        Collection<CustomBossEvent> events = s.getServer().customBossEvents().events();
        if (events.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.bossbar.list.bars.none"), false);
        } else {
            List<Text> names = new ArrayList<>();
            for (CustomBossEvent e : events) names.add(e.displayName());
            s.sendSuccess(() -> Text.translatable("commands.bossbar.list.bars.some", events.size(), formatList(names)), false);
        }
        return events.size();
    }

    /** 바닐라 ComponentUtils.formatList(회색 ", "). */
    private static Text formatList(List<Text> items) {
        Text out = Text.empty();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out = out.append(ComponentUtils.defaultSeparator());
            out = out.append(items.get(i));
        }
        return out;
    }

    static int setName(CommandSource s, CustomBossEvent e, Text name) throws CommandSyntaxException {
        Text resolved = ComponentUtils.updateForEntity(s, name, null, 0);
        if (e.name().equals(resolved)) throw ERROR_NO_NAME_CHANGE.create();
        e.setName(resolved);
        s.sendSuccess(() -> Text.translatable("commands.bossbar.set.name.success", e.displayName()), true);
        return 0;
    }

    static int setColor(CommandSource s, CustomBossEvent e, CustomBossEvent.Color color) throws CommandSyntaxException {
        if (e.color() == color) throw ERROR_NO_COLOR_CHANGE.create();
        e.setColor(color);
        s.sendSuccess(() -> Text.translatable("commands.bossbar.set.color.success", e.displayName()), true);
        return 0;
    }

    static int setStyle(CommandSource s, CustomBossEvent e, CustomBossEvent.Overlay overlay) throws CommandSyntaxException {
        if (e.overlay() == overlay) throw ERROR_NO_STYLE_CHANGE.create();
        e.setOverlay(overlay);
        s.sendSuccess(() -> Text.translatable("commands.bossbar.set.style.success", e.displayName()), true);
        return 0;
    }

    static int setValue(CommandSource s, CustomBossEvent e, int value) throws CommandSyntaxException {
        if (e.value() == value) throw ERROR_NO_VALUE_CHANGE.create();
        e.setValue(value);
        s.sendSuccess(() -> Text.translatable("commands.bossbar.set.value.success", e.displayName(), value), true);
        return value;
    }

    static int setMax(CommandSource s, CustomBossEvent e, int max) throws CommandSyntaxException {
        if (e.max() == max) throw ERROR_NO_MAX_CHANGE.create();
        e.setMax(max);
        s.sendSuccess(() -> Text.translatable("commands.bossbar.set.max.success", e.displayName(), max), true);
        return max;
    }

    static int setVisible(CommandSource s, CustomBossEvent e, boolean visible) throws CommandSyntaxException {
        if (visible) {
            if (e.isVisible()) throw ERROR_ALREADY_VISIBLE.create();
        } else if (!e.isVisible()) {
            throw ERROR_ALREADY_HIDDEN.create();
        }
        e.setVisible(visible);
        s.sendSuccess(() -> Text.translatable("commands.bossbar.set.visible.success." + (visible ? "visible" : "hidden"), e.displayName()), true);
        return 0;
    }

    static int setPlayers(CommandSource s, CustomBossEvent e, Collection<Player> players) throws CommandSyntaxException {
        if (!e.setPlayers(players)) throw ERROR_NO_PLAYER_CHANGE.create();
        if (players.isEmpty()) s.sendSuccess(() -> Text.translatable("commands.bossbar.set.players.success.none", e.displayName()), true);
        else {
            List<Text> names = new ArrayList<>();
            for (Player p : players) names.add(p.displayName());
            s.sendSuccess(() -> Text.translatable("commands.bossbar.set.players.success.some", e.displayName(), players.size(), formatList(names)), true);
        }
        return e.players().size();
    }

    static int getValue(CommandSource s, CustomBossEvent e) {
        s.sendSuccess(() -> Text.translatable("commands.bossbar.get.value", e.displayName(), e.value()), true);
        return e.value();
    }

    static int getMax(CommandSource s, CustomBossEvent e) {
        s.sendSuccess(() -> Text.translatable("commands.bossbar.get.max", e.displayName(), e.max()), true);
        return e.max();
    }

    static int getVisible(CommandSource s, CustomBossEvent e) {
        if (e.isVisible()) {
            s.sendSuccess(() -> Text.translatable("commands.bossbar.get.visible.visible", e.displayName()), true);
            return 1;
        }
        s.sendSuccess(() -> Text.translatable("commands.bossbar.get.visible.hidden", e.displayName()), true);
        return 0;
    }

    static int getPlayers(CommandSource s, CustomBossEvent e) {
        List<Player> online = e.players();
        if (online.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.bossbar.get.players.none", e.displayName()), true);
        } else {
            List<Text> names = new ArrayList<>();
            for (Player p : online) names.add(p.displayName());
            s.sendSuccess(() -> Text.translatable("commands.bossbar.get.players.some", e.displayName(), online.size(), formatList(names)), true);
        }
        return online.size();
    }
}
