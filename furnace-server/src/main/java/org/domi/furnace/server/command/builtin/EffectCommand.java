// 바닐라 EffectCommands: /effect give <대상> <효과> [초] [단계] [입자숨김], /effect clear [대상] [효과].
// 대상은 플레이어와 살아 있는 엔티티(몹). 살아 있지 않은 엔티티(아이템·화살 등)는 건너뛰고 세지 않는다(바닐라와 같음).
// 일반 엔티티는 region 이 나눠 가져 그 주인 스레드에서 걸고, 결과는 모아서 한 번에 알린다(TagCommand 와 같은 방식).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
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
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;

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

    /** 효과를 거는 방법(초·단계·입자). seconds 가 null 이면 기본, -1 이면 무한. */
    private record Give(Integer seconds, int amplifier, boolean particles) { }

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("effect").requires(s -> s.hasPermission(2))
                .then(literal("clear")
                        .executes(c -> EntityOps.result(c.getSource(), clear(c.getSource(), List.of(c.getSource().getEntityOrException()), null)))
                        .then(argument("targets", EntityArgument.entities())
                                .executes(c -> EntityOps.result(c.getSource(), EntityArgument.withEntitiesAsync(c, "targets", t -> clear(c.getSource(), t, null))))
                                .then(argument("effect", ResourceArgument.resource("minecraft:mob_effect"))
                                        .executes(c -> EntityOps.result(c.getSource(), EntityArgument.withEntitiesAsync(c, "targets", t -> clear(c.getSource(), t, effect(c))))))))
                .then(literal("give").then(argument("targets", EntityArgument.entities()).then(argument("effect", ResourceArgument.resource("minecraft:mob_effect"))
                        .executes(c -> give(c, new Give(null, 0, true)))
                        .then(literal("infinite")
                                .executes(c -> give(c, new Give(-1, 0, true)))
                                .then(argument("amplifier", Arguments.integer(0, 255))
                                        .executes(c -> give(c, new Give(-1, c.getArgument("amplifier", Integer.class), true)))
                                        .then(argument("hideParticles", Arguments.bool())
                                                .executes(c -> give(c, new Give(-1, c.getArgument("amplifier", Integer.class), !c.getArgument("hideParticles", Boolean.class)))))))
                        .then(argument("seconds", Arguments.integer(1, 1000000))
                                .executes(c -> give(c, new Give(c.getArgument("seconds", Integer.class), 0, true)))
                                .then(argument("amplifier", Arguments.integer(0, 255))
                                        .executes(c -> give(c, new Give(c.getArgument("seconds", Integer.class), c.getArgument("amplifier", Integer.class), true)))
                                        .then(argument("hideParticles", Arguments.bool())
                                                .executes(c -> give(c, new Give(c.getArgument("seconds", Integer.class), c.getArgument("amplifier", Integer.class),
                                                        !c.getArgument("hideParticles", Boolean.class)))))))))));
    }

    /** 바닐라 순서: 대상을 먼저 찾고(없으면 "No entity was found") 효과를 꺼낸다. */
    private static int give(CommandContext<CommandSource> c, Give g) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        return EntityOps.result(source, EntityArgument.withEntitiesAsync(c, "targets", t -> give(source, t, effect(c), g)));
    }

    private static CompletableFuture<Integer> give(CommandSource source, List<Rider> targets, MobEffect e, Give g) {
        Integer seconds = g.seconds();
        int duration = seconds != null && seconds == -1 ? -1 : seconds != null ? (e.isInstantaneous() ? seconds : seconds * 20) : (e.isInstantaneous() ? 1 : 600);
        return EntityOps.forEachAsync(source, targets, r -> applyGive(r, e, duration, g.amplifier(), g.particles()), results -> {
            int n = 0;
            for (boolean b : results) if (b) n++;
            if (n == 0) throw ERROR_GIVE_FAILED.create();
            int shown = duration / 20;
            if (targets.size() == 1) {
                Rider r = targets.get(0);
                source.sendSuccess(() -> Text.translatable("commands.effect.give.success.single", effectName(e), r.displayName(), shown), true);
            } else {
                source.sendSuccess(() -> Text.translatable("commands.effect.give.success.multiple", effectName(e), targets.size(), shown), true);
            }
            return n;
        });
    }

    /** 대상의 주인 스레드에서 효과를 건다. 살아 있지 않은 엔티티면 false. */
    private static boolean applyGive(Rider r, MobEffect e, int duration, int amplifier, boolean particles) {
        if (r instanceof Player p) {
            if (e.isInstantaneous()) {
                p.vitals().applyInstantaneousEffect(e, amplifier, 1.0);
                return true;
            }
            return p.vitals().addEffect(new MobEffectInstance(e, duration, amplifier, false, particles));
        }
        if (!(r instanceof LivingEntity le)) return false;
        if (e.isInstantaneous()) {
            e.applyInstantenousEffect(le.effectHost(), amplifier, 1.0);
            return true;
        }
        return le.addEffect(new MobEffectInstance(e, duration, amplifier, false, particles));
    }

    private static CompletableFuture<Integer> clear(CommandSource source, List<Rider> targets, MobEffect e) {
        return EntityOps.forEachAsync(source, targets, r -> applyClear(r, e), results -> {
            int n = 0;
            for (boolean b : results) if (b) n++;
            if (n == 0) throw (e == null ? ERROR_CLEAR_EVERYTHING_FAILED : ERROR_CLEAR_SPECIFIC_FAILED).create();
            String key = e == null ? "commands.effect.clear.everything.success" : "commands.effect.clear.specific.success";
            if (targets.size() == 1) {
                Rider r = targets.get(0);
                source.sendSuccess(() -> e == null ? Text.translatable(key + ".single", r.displayName()) : Text.translatable(key + ".single", effectName(e), r.displayName()), true);
            } else {
                source.sendSuccess(() -> e == null ? Text.translatable(key + ".multiple", targets.size()) : Text.translatable(key + ".multiple", effectName(e), targets.size()), true);
            }
            return n;
        });
    }

    /** 바닐라 removeAllEffects / removeEffect: 지운 게 있으면 true. 살아 있지 않은 엔티티면 false. */
    private static boolean applyClear(Rider r, MobEffect e) {
        if (r instanceof Player p) return e == null ? p.vitals().removeAllEffects() > 0 : p.vitals().removeEffect(e);
        if (!(r instanceof LivingEntity le)) return false;
        return e == null ? le.removeAllEffects() > 0 : le.removeEffect(e);
    }
}
