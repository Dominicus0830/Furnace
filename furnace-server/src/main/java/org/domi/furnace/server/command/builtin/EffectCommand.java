// 바닐라 EffectCommands: /effect give <대상> <효과> [초] [단계] [입자숨김], /effect clear [대상] [효과].
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ResourceArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.Player;

public final class EffectCommand {
    private EffectCommand() { }

    private static final SimpleCommandExceptionType ERROR_NOT_FOUND = new SimpleCommandExceptionType(Text.translatable("effect.effectNotFound", ""));
    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.effect.give.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_EVERYTHING_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.effect.clear.everything.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_SPECIFIC_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.effect.clear.specific.failed"));

    private static MobEffect effect(CommandContext<CommandSource> c) throws CommandSyntaxException {
        var id = ResourceLocationArgument.getId(c, "effect");
        MobEffect e = MobEffect.byName(id.namespace() + ":" + id.path());
        if (e == null) throw new SimpleCommandExceptionType(Text.translatable("effect.effectNotFound", id.namespace() + ":" + id.path())).create();
        return e;
    }

    private static Text effectName(MobEffect e) { return Text.translatable("effect." + e.name().replace(':', '.')); }

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("effect").requires(s -> s.hasPermission(2))
                .then(literal("clear")
                        .executes(c -> clear(c.getSource(), List.of(c.getSource().getPlayerOrException()), null))
                        .then(argument("targets", EntityArgument.entities())
                                .executes(c -> clear(c.getSource(), EntityArgument.getEntities(c, "targets"), null))
                                .then(argument("effect", ResourceArgument.resource("minecraft:mob_effect"))
                                        .executes(c -> clear(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c))))))
                .then(literal("give").then(argument("targets", EntityArgument.entities()).then(argument("effect", ResourceArgument.resource("minecraft:mob_effect"))
                        .executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c), null, 0, true))
                        .then(literal("infinite")
                                .executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c), -1, 0, true))
                                .then(argument("amplifier", Arguments.integer(0, 255))
                                        .executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c), -1, c.getArgument("amplifier", Integer.class), true))
                                        .then(argument("hideParticles", Arguments.bool())
                                                .executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c), -1, c.getArgument("amplifier", Integer.class), !c.getArgument("hideParticles", Boolean.class))))))
                        .then(argument("seconds", Arguments.integer(1, 1000000))
                                .executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c), c.getArgument("seconds", Integer.class), 0, true))
                                .then(argument("amplifier", Arguments.integer(0, 255))
                                        .executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c), c.getArgument("seconds", Integer.class), c.getArgument("amplifier", Integer.class), true))
                                        .then(argument("hideParticles", Arguments.bool())
                                                .executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), effect(c), c.getArgument("seconds", Integer.class), c.getArgument("amplifier", Integer.class), !c.getArgument("hideParticles", Boolean.class))))))))));
    }

    private static int give(CommandSource source, Collection<Player> targets, MobEffect e, Integer seconds, int amplifier, boolean particles) throws CommandSyntaxException {
        int duration = seconds != null && seconds == -1 ? -1 : seconds != null ? (e.isInstantaneous() ? seconds : seconds * 20) : (e.isInstantaneous() ? 1 : 600);
        int n = 0;
        for (Player p : targets) {
            if (e.isInstantaneous()) {
                p.vitals().applyInstantaneousEffect(e, amplifier, 1.0);
                n++;
            } else if (p.vitals().addEffect(new MobEffectInstance(e, duration, amplifier, false, particles))) {
                n++;
            }
        }
        if (n == 0) throw ERROR_GIVE_FAILED.create();
        int shown = duration / 20;
        if (targets.size() == 1) {
            Player p = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.effect.give.success.single", effectName(e), p.displayName(), shown), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.effect.give.success.multiple", effectName(e), targets.size(), shown), true);
        }
        return n;
    }

    private static int clear(CommandSource source, Collection<Player> targets, MobEffect e) throws CommandSyntaxException {
        int n = 0;
        for (Player p : targets) {
            if (e == null ? p.vitals().removeAllEffects() > 0 : p.vitals().removeEffect(e)) n++;
        }
        if (n == 0) throw (e == null ? ERROR_CLEAR_EVERYTHING_FAILED : ERROR_CLEAR_SPECIFIC_FAILED).create();
        String key = e == null ? "commands.effect.clear.everything.success" : "commands.effect.clear.specific.success";
        if (targets.size() == 1) {
            Player p = targets.iterator().next();
            source.sendSuccess(() -> e == null ? Text.translatable(key + ".single", p.displayName()) : Text.translatable(key + ".single", effectName(e), p.displayName()), true);
        } else {
            source.sendSuccess(() -> e == null ? Text.translatable(key + ".multiple", targets.size()) : Text.translatable(key + ".multiple", effectName(e), targets.size()), true);
        }
        return targets.size();
    }
}
