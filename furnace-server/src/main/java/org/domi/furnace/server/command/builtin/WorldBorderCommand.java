// 바닐라 WorldBorderCommand(/worldborder, 권한 2) — 크기(add/set, 선택적 보간 시간)·중심·피해·경고·조회. 오버월드 보더를 다룬다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Locale;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.Vec2Argument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.net.protocol.Text;
import org.domi.furnace.server.world.WorldBorder;

public final class WorldBorderCommand {

    private static final SimpleCommandExceptionType ERROR_SAME_CENTER = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.center.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_SIZE = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.set.failed.nochange"));
    private static final SimpleCommandExceptionType ERROR_TOO_SMALL = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.set.failed.small"));
    // 바닐라 인자는 float 5.999997E7F / double 2.9999984E7 그대로라 문구에 "5.999997E7", "2.9999984E7" 로 찍힌다.
    private static final SimpleCommandExceptionType ERROR_TOO_BIG = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.set.failed.big", 5.999997E7F));
    private static final SimpleCommandExceptionType ERROR_TOO_FAR_OUT = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.set.failed.far", 2.9999984E7));
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_TIME = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.warning.time.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_DISTANCE = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.warning.distance.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_BUFFER = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.damage.buffer.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_AMOUNT = new SimpleCommandExceptionType(Text.translatable("commands.worldborder.damage.amount.failed"));

    private WorldBorderCommand() {}

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("worldborder").requires(s -> s.hasPermission(2))
                .then(literal("add")
                        .then(argument("distance", Arguments.doubleArg(-WorldBorder.MAX_SIZE, WorldBorder.MAX_SIZE))
                                .executes(c -> setSize(c.getSource(), border(c.getSource()).getSize() + c.getArgument("distance", Double.class), 0L))
                                .then(argument("time", Arguments.integer(0))
                                        .executes(c -> setSize(c.getSource(), border(c.getSource()).getSize() + c.getArgument("distance", Double.class),
                                                border(c.getSource()).getLerpRemainingTime() + (long) c.getArgument("time", Integer.class) * 1000L)))))
                .then(literal("set")
                        .then(argument("distance", Arguments.doubleArg(-WorldBorder.MAX_SIZE, WorldBorder.MAX_SIZE))
                                .executes(c -> setSize(c.getSource(), c.getArgument("distance", Double.class), 0L))
                                .then(argument("time", Arguments.integer(0))
                                        .executes(c -> setSize(c.getSource(), c.getArgument("distance", Double.class),
                                                (long) c.getArgument("time", Integer.class) * 1000L)))))
                .then(literal("center")
                        .then(argument("pos", Vec2Argument.vec2())
                                .executes(c -> setCenter(c.getSource(), Vec2Argument.getVec2(c, "pos")))))
                .then(literal("damage")
                        .then(literal("amount")
                                .then(argument("damagePerBlock", Arguments.floatArg(0f, Float.MAX_VALUE))
                                        .executes(c -> setDamageAmount(c.getSource(), c.getArgument("damagePerBlock", Float.class)))))
                        .then(literal("buffer")
                                .then(argument("distance", Arguments.floatArg(0f, Float.MAX_VALUE))
                                        .executes(c -> setDamageBuffer(c.getSource(), c.getArgument("distance", Float.class))))))
                .then(literal("get").executes(c -> getSize(c.getSource())))
                .then(literal("warning")
                        .then(literal("distance")
                                .then(argument("distance", Arguments.integer(0))
                                        .executes(c -> setWarningDistance(c.getSource(), c.getArgument("distance", Integer.class)))))
                        .then(literal("time")
                                .then(argument("time", Arguments.integer(0))
                                        .executes(c -> setWarningTime(c.getSource(), c.getArgument("time", Integer.class)))))));
    }

    /** 바닐라는 명령 소스의 레벨(오버월드)의 보더. 지금은 오버월드뿐이다. */
    private static WorldBorder border(CommandSource source) { return source.getServer().border(); }

    private static int setDamageBuffer(CommandSource source, float distance) throws CommandSyntaxException {
        WorldBorder border = border(source);
        if (border.getDamageSafeZone() == (double) distance) throw ERROR_SAME_DAMAGE_BUFFER.create();
        border.setDamageSafeZone(distance);
        source.sendSuccess(() -> Text.translatable("commands.worldborder.damage.buffer.success", String.format(Locale.ROOT, "%.2f", distance)), true);
        return (int) distance;
    }

    private static int setDamageAmount(CommandSource source, float amount) throws CommandSyntaxException {
        WorldBorder border = border(source);
        if (border.getDamagePerBlock() == (double) amount) throw ERROR_SAME_DAMAGE_AMOUNT.create();
        border.setDamagePerBlock(amount);
        source.sendSuccess(() -> Text.translatable("commands.worldborder.damage.amount.success", String.format(Locale.ROOT, "%.2f", amount)), true);
        return (int) amount;
    }

    private static int setWarningTime(CommandSource source, int time) throws CommandSyntaxException {
        WorldBorder border = border(source);
        if (border.getWarningTime() == time) throw ERROR_SAME_WARNING_TIME.create();
        border.setWarningTime(time);
        source.sendSuccess(() -> Text.translatable("commands.worldborder.warning.time.success", time), true);
        return time;
    }

    private static int setWarningDistance(CommandSource source, int distance) throws CommandSyntaxException {
        WorldBorder border = border(source);
        if (border.getWarningBlocks() == distance) throw ERROR_SAME_WARNING_DISTANCE.create();
        border.setWarningBlocks(distance);
        source.sendSuccess(() -> Text.translatable("commands.worldborder.warning.distance.success", distance), true);
        return distance;
    }

    private static int getSize(CommandSource source) {
        double size = border(source).getSize();
        source.sendSuccess(() -> Text.translatable("commands.worldborder.get", String.format(Locale.ROOT, "%.0f", size)), false);
        return (int) Math.floor(size + 0.5);
    }

    private static int setCenter(CommandSource source, Vec2Argument.Vec2 pos) throws CommandSyntaxException {
        WorldBorder border = border(source);
        if (border.getCenterX() == (double) pos.x() && border.getCenterZ() == (double) pos.y()) throw ERROR_SAME_CENTER.create();
        if (Math.abs((double) pos.x()) > WorldBorder.MAX_CENTER_COORDINATE || Math.abs((double) pos.y()) > WorldBorder.MAX_CENTER_COORDINATE) {
            throw ERROR_TOO_FAR_OUT.create();
        }
        border.setCenter(pos.x(), pos.y());
        source.sendSuccess(() -> Text.translatable("commands.worldborder.center.success",
                String.format(Locale.ROOT, "%.2f", pos.x()), String.format(Locale.ROOT, "%.2f", pos.y())), true);
        return 0;
    }

    private static int setSize(CommandSource source, double newSize, long timeMs) throws CommandSyntaxException {
        WorldBorder border = border(source);
        double oldSize = border.getSize();
        if (oldSize == newSize) throw ERROR_SAME_SIZE.create();
        if (newSize < 1.0) throw ERROR_TOO_SMALL.create();
        if (newSize > WorldBorder.MAX_SIZE) throw ERROR_TOO_BIG.create();
        if (timeMs > 0L) {
            border.lerpSizeBetween(oldSize, newSize, timeMs);
            String seconds = Long.toString(timeMs / 1000L);
            String shown = String.format(Locale.ROOT, "%.1f", newSize);
            if (newSize > oldSize) source.sendSuccess(() -> Text.translatable("commands.worldborder.set.grow", shown, seconds), true);
            else source.sendSuccess(() -> Text.translatable("commands.worldborder.set.shrink", shown, seconds), true);
        } else {
            border.setSize(newSize);
            source.sendSuccess(() -> Text.translatable("commands.worldborder.set.immediate", String.format(Locale.ROOT, "%.1f", newSize)), true);
        }
        return (int) (newSize - oldSize);
    }
}
