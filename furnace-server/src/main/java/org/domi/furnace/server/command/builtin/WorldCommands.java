// 바닐라 TimeCommand·WeatherCommand·DifficultyCommand·GameRuleCommand.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.TimeArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameRules;
import org.domi.furnace.server.world.WorldClock;

public final class WorldCommands {

    private static final DynamicCommandExceptionType ERROR_ALREADY_DIFFICULT =
            new DynamicCommandExceptionType(d -> Text.translatable("commands.difficulty.failure", d));

    private WorldCommands() {}

    // ------------------------------------------------------------------ time

    public static void registerTime(CommandDispatcher<CommandSource> d) {
        d.register(literal("time").requires(s -> s.hasPermission(2))
                .then(literal("set")
                        .then(literal("day").executes(c -> setTime(c.getSource(), 1000)))
                        .then(literal("noon").executes(c -> setTime(c.getSource(), 6000)))
                        .then(literal("night").executes(c -> setTime(c.getSource(), 13000)))
                        .then(literal("midnight").executes(c -> setTime(c.getSource(), 18000)))
                        .then(argument("time", TimeArgument.time()).executes(c -> setTime(c.getSource(), c.getArgument("time", Integer.class)))))
                .then(literal("add").then(argument("time", TimeArgument.time())
                        .executes(c -> addTime(c.getSource(), c.getArgument("time", Integer.class)))))
                .then(literal("query")
                        .then(literal("daytime").executes(c -> queryTime(c.getSource(), dayTime(c.getSource()))))
                        .then(literal("gametime").executes(c -> queryTime(c.getSource(),
                                (int) (c.getSource().getServer().clock().gameTime() % Integer.MAX_VALUE))))
                        .then(literal("day").executes(c -> queryTime(c.getSource(),
                                (int) (c.getSource().getServer().clock().dayTime() / 24000L % Integer.MAX_VALUE))))));
    }

    private static int dayTime(CommandSource s) { return (int) (s.getServer().clock().dayTime() % 24000L); }

    private static int queryTime(CommandSource source, int time) {
        source.sendSuccess(() -> Text.translatable("commands.time.query", time), false);
        return time;
    }

    private static int setTime(CommandSource source, int time) {
        source.getServer().clock().setDayTime(time);
        source.sendSuccess(() -> Text.translatable("commands.time.set", time), true);
        return dayTime(source);
    }

    private static int addTime(CommandSource source, int amount) {
        WorldClock clock = source.getServer().clock();
        clock.setDayTime(clock.dayTime() + amount);
        int t = dayTime(source);
        source.sendSuccess(() -> Text.translatable("commands.time.set", t), true);
        return t;
    }

    // ------------------------------------------------------------------ weather

    public static void registerWeather(CommandDispatcher<CommandSource> d) {
        d.register(literal("weather").requires(s -> s.hasPermission(2))
                .then(literal("clear").executes(c -> setClear(c.getSource(), -1))
                        .then(argument("duration", TimeArgument.time(1)).executes(c -> setClear(c.getSource(), c.getArgument("duration", Integer.class)))))
                .then(literal("rain").executes(c -> setRain(c.getSource(), -1))
                        .then(argument("duration", TimeArgument.time(1)).executes(c -> setRain(c.getSource(), c.getArgument("duration", Integer.class)))))
                .then(literal("thunder").executes(c -> setThunder(c.getSource(), -1))
                        .then(argument("duration", TimeArgument.time(1)).executes(c -> setThunder(c.getSource(), c.getArgument("duration", Integer.class))))));
    }

    private static int duration(CommandSource s, int time, int[] range) {
        return time == -1 ? WorldClock.sample(s.getServer().clock().random(), range) : time;
    }

    private static int setClear(CommandSource s, int time) {
        s.getServer().clock().setWeatherParameters(duration(s, time, WorldClock.RAIN_DELAY), 0, false, false);
        s.sendSuccess(() -> Text.translatable("commands.weather.set.clear"), true);
        return time;
    }

    private static int setRain(CommandSource s, int time) {
        s.getServer().clock().setWeatherParameters(0, duration(s, time, WorldClock.RAIN_DURATION), true, false);
        s.sendSuccess(() -> Text.translatable("commands.weather.set.rain"), true);
        return time;
    }

    private static int setThunder(CommandSource s, int time) {
        s.getServer().clock().setWeatherParameters(0, duration(s, time, WorldClock.THUNDER_DURATION), true, true);
        s.sendSuccess(() -> Text.translatable("commands.weather.set.thunder"), true);
        return time;
    }

    // ------------------------------------------------------------------ difficulty

    public static void registerDifficulty(CommandDispatcher<CommandSource> d) {
        LiteralArgumentBuilder<CommandSource> l = literal("difficulty");
        for (Difficulty diff : Difficulty.values()) l.then(literal(diff.key()).executes(c -> setDifficulty(c.getSource(), diff)));
        d.register(l.requires(s -> s.hasPermission(2)).executes(c -> {
            Difficulty cur = c.getSource().getServer().levelData().difficulty;
            c.getSource().sendSuccess(() -> Text.translatable("commands.difficulty.query", displayName(cur)), false);
            return cur.id();
        }));
    }

    public static Text displayName(Difficulty d) { return Text.translatable("options.difficulty." + d.key()); }

    private static int setDifficulty(CommandSource source, Difficulty d) throws CommandSyntaxException {
        if (source.getServer().levelData().difficulty == d) throw ERROR_ALREADY_DIFFICULT.create(d.key());
        source.getServer().setDifficulty(d, true);
        source.sendSuccess(() -> Text.translatable("commands.difficulty.success", displayName(d)), true);
        return 0;
    }

    // ------------------------------------------------------------------ gamerule

    public static void registerGameRule(CommandDispatcher<CommandSource> d) {
        LiteralArgumentBuilder<CommandSource> l = literal("gamerule").requires(s -> s.hasPermission(2));
        for (GameRules.Rule rule : GameRules.RULES.values()) l.then(ruleNode(rule));
        d.register(l);
    }

    private static LiteralArgumentBuilder<CommandSource> ruleNode(GameRules.Rule rule) {
        var value = rule.isBoolean()
                ? argument("value", Arguments.bool()).executes(c -> setRule(c.getSource(), rule, c.getArgument("value", Boolean.class) ? 1 : 0))
                : argument("value", rule.min() == Integer.MIN_VALUE ? Arguments.integer() : Arguments.integer(rule.min(), rule.max()))
                        .executes(c -> setRule(c.getSource(), rule, c.getArgument("value", Integer.class)));
        return literal(rule.name()).executes(c -> queryRule(c.getSource(), rule)).then(value);
    }

    /** 플러그인 규칙을 /gamerule 아래에 붙인다(바닐라 규칙 뒤). 트리 재전송은 부르는 쪽 몫. */
    public static void addGameRule(CommandDispatcher<CommandSource> d, GameRules.Rule rule) {
        d.register(literal("gamerule").requires(s -> s.hasPermission(2)).then(ruleNode(rule)));
    }

    /** /gamerule 아래의 그 규칙을 뗀다. */
    public static void removeGameRule(CommandDispatcher<CommandSource> d, String name) {
        var node = d.getRoot().getChild("gamerule");
        if (node != null) node.removeChild(name);
    }

    private static int setRule(CommandSource source, GameRules.Rule rule, int value) {
        source.getServer().gameRules().set(rule.name(), value);
        String shown = GameRules.serialize(rule, value);
        source.sendSuccess(() -> Text.translatable("commands.gamerule.set", rule.name(), shown), true);
        return value;
    }

    private static int queryRule(CommandSource source, GameRules.Rule rule) {
        int v = source.getServer().gameRules().raw(rule.name());
        String shown = GameRules.serialize(rule, v);
        source.sendSuccess(() -> Text.translatable("commands.gamerule.query", rule.name(), shown), false);
        return v;
    }
}
