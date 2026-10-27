// 바닐라 DamageCommand: /damage <대상> <양> [<유형> [at <위치> | by <엔티티> [from <원인>]]].
// 대상이 플레이어면 PlayerVitals.hurt, 일반 엔티티면 그 엔티티를 가진 region 스레드에서 hurt 를 부른다.
// 차이: 일반 엔티티 대상은 반환값이 늘 1 이고 결과는 나중에 메시지로 온다(context-notes 의 "반환값 차이").
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ResourceArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.arguments.Vec3Argument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.entity.damage.DamageSource;
import org.domi.furnace.server.entity.damage.DamageType;

public final class DamageCommand {
    private DamageCommand() { }

    static final SimpleCommandExceptionType ERROR_INVULNERABLE = new SimpleCommandExceptionType(Text.translatable("commands.damage.invulnerable"));
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE =
            new Dynamic2CommandExceptionType((id, reg) -> Text.translatable("argument.resource.not_found", id, reg));

    /** 출처를 만드는 쪽(by/from 엔티티는 해석이 끝난 뒤 정해진다). */
    @FunctionalInterface
    private interface SourceMaker {
        int run(DamageSource src) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("damage").requires(s -> s.hasPermission(2))
                .then(argument("target", EntityArgument.entity())
                        .then(argument("amount", Arguments.floatArg(0.0F, Float.MAX_VALUE))
                                .executes(c -> damage(c, DamageSource.of(DamageType.GENERIC)))
                                .then(argument("damageType", ResourceArgument.resource("minecraft:damage_type"))
                                        .executes(c -> damage(c, DamageSource.of(type(c))))
                                        .then(literal("at").then(argument("location", Vec3Argument.vec3())
                                                .executes(c -> damage(c, DamageSource.at(type(c), Vec3Argument.getVec3(c, "location"))))))
                                        .then(literal("by").then(argument("entity", EntityArgument.entity())
                                                .executes(c -> {
                                                    DamageType t = type(c);
                                                    return EntityArgument.withEntity(c, "entity", e -> damage(c, DamageSource.by(t, e.get(0))));
                                                })
                                                .then(literal("from").then(argument("cause", EntityArgument.entity())
                                                        .executes(c -> {
                                                            DamageType t = type(c);
                                                            return EntityArgument.withEntity(c, "entity", e -> EntityArgument.withEntity(c, "cause",
                                                                    cause -> damage(c, DamageSource.by(t, e.get(0), cause.get(0)))));
                                                        })))))))));
    }

    static DamageType type(CommandContext<CommandSource> c) throws CommandSyntaxException {
        var id = ResourceLocationArgument.getId(c, "damageType");
        DamageType t = DamageType.find(id.toString());
        if (t == null) throw ERROR_UNKNOWN_RESOURCE.create(id.toString(), "minecraft:damage_type");
        return t;
    }

    private static int damage(CommandContext<CommandSource> c, DamageSource src) throws CommandSyntaxException {
        float amount = c.getArgument("amount", Float.class);
        return EntityArgument.withEntity(c, "target", t -> apply(c.getSource(), t.get(0), amount, src));
    }

    /** 바닐라 DamageCommand.damage: target.hurt 가 참이면 성공 메시지, 아니면 invulnerable. */
    static int apply(CommandSource source, Rider target, float amount, DamageSource src) throws CommandSyntaxException {
        return EntityOps.forEach(source, List.of(target), r -> hurt(r, amount, src), results -> {
            if (!results.get(0)) throw ERROR_INVULNERABLE.create();
            source.sendSuccess(() -> Text.translatable("commands.damage.success", amount, target.displayName()), true);
            return 1;
        });
    }

    /** 대상 주인 스레드에서 부른다. */
    static boolean hurt(Rider r, float amount, DamageSource src) {
        if (r instanceof Player p) return p.vitals().hurt(src, amount);
        if (r instanceof LivingEntity le) return le.hurtBySource(src, amount);
        return ((Entity) r).hurtBy(Entity.Damage.of(src.type()), amount, src.attacker());
    }
}
