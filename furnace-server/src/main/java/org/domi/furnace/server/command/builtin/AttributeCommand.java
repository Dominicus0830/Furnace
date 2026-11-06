// 바닐라 AttributeCommand: /attribute <대상> <속성> get|base get|base set|modifier add|remove|value get.
// 대상은 LivingEntity(플레이어 포함)여야 하고, 속성 값은 그 엔티티를 가진 region 스레드에서 읽고 쓴다(EntityOps.onOwner).
// 차이: 비동기로 끝나면 반환값이 1 이다(바닐라는 값×scale 의 정수; context-notes 의 "반환값 차이").
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeInstance;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ResourceArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.Dynamic3CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;

public final class AttributeCommand {
    private AttributeCommand() { }

    private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY =
            new DynamicCommandExceptionType(n -> Text.translatable("commands.attribute.failed.entity", n));
    private static final Dynamic2CommandExceptionType ERROR_NO_SUCH_ATTRIBUTE =
            new Dynamic2CommandExceptionType((n, a) -> Text.translatable("commands.attribute.failed.no_attribute", n, a));
    private static final Dynamic3CommandExceptionType ERROR_NO_SUCH_MODIFIER =
            new Dynamic3CommandExceptionType((n, a, id) -> Text.translatable("commands.attribute.failed.no_modifier", a, n, id));
    private static final Dynamic3CommandExceptionType ERROR_MODIFIER_ALREADY_PRESENT =
            new Dynamic3CommandExceptionType((n, a, id) -> Text.translatable("commands.attribute.failed.modifier_already_present", id, a, n));
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE =
            new Dynamic2CommandExceptionType((id, reg) -> Text.translatable("argument.resource.not_found", id, reg));

    private static final String REGISTRY = "minecraft:attribute";

    public static void register(CommandDispatcher<CommandSource> d) {
        var dbl = Arguments.doubleArg(-Double.MAX_VALUE, Double.MAX_VALUE);
        d.register(literal("attribute").requires(s -> s.hasPermission(2))
                .then(argument("target", EntityArgument.entity())
                        .then(argument("attribute", ResourceArgument.resource(REGISTRY))
                                .then(literal("get")
                                        .executes(c -> getValue(c, 1.0))
                                        .then(argument("scale", dbl).executes(c -> getValue(c, c.getArgument("scale", Double.class)))))
                                .then(literal("base")
                                        .then(literal("set").then(argument("value", dbl).executes(AttributeCommand::setBase)))
                                        .then(literal("get")
                                                .executes(c -> getBase(c, 1.0))
                                                .then(argument("scale", dbl).executes(c -> getBase(c, c.getArgument("scale", Double.class))))))
                                .then(literal("modifier")
                                        .then(literal("add").then(argument("id", ResourceLocationArgument.id())
                                                .then(argument("value", dbl)
                                                        .then(literal("add_value").executes(c -> addModifier(c, AttributeModifier.ADD_VALUE)))
                                                        .then(literal("add_multiplied_base").executes(c -> addModifier(c, AttributeModifier.ADD_MULTIPLIED_BASE)))
                                                        .then(literal("add_multiplied_total").executes(c -> addModifier(c, AttributeModifier.ADD_MULTIPLIED_TOTAL))))))
                                        .then(literal("remove").then(argument("id", ResourceLocationArgument.id()).executes(AttributeCommand::removeModifier)))
                                        .then(literal("value").then(literal("get").then(argument("id", ResourceLocationArgument.id())
                                                .executes(c -> getModifier(c, 1.0))
                                                .then(argument("scale", dbl).executes(c -> getModifier(c, c.getArgument("scale", Double.class)))))))))));
    }

    /** 바닐라 ResourceArgument.getAttribute: 레지스트리에 없는 id 면 argument.resource.not_found. */
    private static Attribute attribute(CommandContext<CommandSource> c) throws CommandSyntaxException {
        ResourceId id = c.getArgument("attribute", ResourceId.class);
        Attribute a = Attribute.byName(id.namespace() + ":" + id.path());
        if (a == null) throw ERROR_UNKNOWN_RESOURCE.create(id.namespace() + ":" + id.path(), REGISTRY);
        return a;
    }

    private static Text attrName(Attribute a) { return Text.translatable("attribute.name." + a.name().substring("minecraft:".length())); }

    private static String modId(CommandContext<CommandSource> c) {
        ResourceId id = ResourceLocationArgument.getId(c, "id");
        return id.namespace() + ":" + id.path();
    }

    /** 바닐라 getLivingEntity: 살아 있는 것이 아니면 failed.entity. 플레이어는 자기 속성 맵을 가진다. */
    private static AttributeMap mapOf(Rider r) throws CommandSyntaxException {
        if (r instanceof LivingEntity le) return le.attributes();
        if (r instanceof Player p) return p.attributes();
        throw ERROR_NOT_LIVING_ENTITY.create(r.displayName());
    }

    /** 바닐라 getAttributeInstance: 이 엔티티에 그 속성이 없으면 failed.no_attribute. */
    private static AttributeInstance instance(Rider r, Attribute a) throws CommandSyntaxException {
        AttributeInstance i = mapOf(r).get(a);
        if (i == null) throw ERROR_NO_SUCH_ATTRIBUTE.create(r.displayName(), attrName(a));
        return i;
    }

    @FunctionalInterface
    private interface Op {
        int run(Rider r) throws CommandSyntaxException;
    }

    /** region 스레드 일의 결과(값 또는 명령 오류). */
    private record Outcome(int value, CommandSyntaxException error) { }

    private static int run(CommandContext<CommandSource> c, Op op) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        return EntityOps.result(source, EntityArgument.withEntityAsync(c, "target", targets -> EntityOps.forEachAsync(source, targets, r -> {
            try {
                return new Outcome(op.run(r), null);
            } catch (CommandSyntaxException e) {
                return new Outcome(0, e);
            }
        }, results -> {
            Outcome o = results.get(0);
            if (o.error() != null) throw o.error();
            return o.value();
        })));
    }

    private static int getValue(CommandContext<CommandSource> c, double scale) throws CommandSyntaxException {
        Attribute a = attribute(c);
        return run(c, r -> opGet(c.getSource(), r, a, scale));
    }

    private static int getBase(CommandContext<CommandSource> c, double scale) throws CommandSyntaxException {
        Attribute a = attribute(c);
        return run(c, r -> opGetBase(c.getSource(), r, a, scale));
    }

    private static int getModifier(CommandContext<CommandSource> c, double scale) throws CommandSyntaxException {
        Attribute a = attribute(c);
        String id = modId(c);
        return run(c, r -> opGetModifier(c.getSource(), r, a, id, scale));
    }

    private static int setBase(CommandContext<CommandSource> c) throws CommandSyntaxException {
        Attribute a = attribute(c);
        double value = c.getArgument("value", Double.class);
        return run(c, r -> opSetBase(c.getSource(), r, a, value));
    }

    private static int addModifier(CommandContext<CommandSource> c, int op) throws CommandSyntaxException {
        Attribute a = attribute(c);
        String id = modId(c);
        double value = c.getArgument("value", Double.class);
        return run(c, r -> opAddModifier(c.getSource(), r, a, id, value, op));
    }

    private static int removeModifier(CommandContext<CommandSource> c) throws CommandSyntaxException {
        Attribute a = attribute(c);
        String id = modId(c);
        return run(c, r -> opRemoveModifier(c.getSource(), r, a, id));
    }

    // ------------------------------------------------------------------ 대상 엔티티의 주인 스레드에서 도는 본문(바닐라 같은 이름의 메서드)

    static int opGet(CommandSource s, Rider r, Attribute a, double scale) throws CommandSyntaxException {
        instance(r, a);
        double v = mapOf(r).value(a);
        s.sendSuccess(() -> Text.translatable("commands.attribute.value.get.success", attrName(a), r.displayName(), v), false);
        return (int) (v * scale);
    }

    static int opGetBase(CommandSource s, Rider r, Attribute a, double scale) throws CommandSyntaxException {
        double v = instance(r, a).baseValue();
        s.sendSuccess(() -> Text.translatable("commands.attribute.base_value.get.success", attrName(a), r.displayName(), v), false);
        return (int) (v * scale);
    }

    static int opGetModifier(CommandSource s, Rider r, Attribute a, String id, double scale) throws CommandSyntaxException {
        AttributeModifier m = instance(r, a).getModifier(id);
        if (m == null) throw ERROR_NO_SUCH_MODIFIER.create(r.displayName(), attrName(a), id);
        double v = m.amount();
        s.sendSuccess(() -> Text.translatable("commands.attribute.modifier.value.get.success", id, attrName(a), r.displayName(), v), false);
        return (int) (v * scale);
    }

    static int opSetBase(CommandSource s, Rider r, Attribute a, double value) throws CommandSyntaxException {
        instance(r, a).setBaseValue(value);
        s.sendSuccess(() -> Text.translatable("commands.attribute.base_value.set.success", attrName(a), r.displayName(), value), true);
        return 1;
    }

    static int opAddModifier(CommandSource s, Rider r, Attribute a, String id, double value, int op) throws CommandSyntaxException {
        AttributeInstance i = instance(r, a);
        if (i.hasModifier(id)) throw ERROR_MODIFIER_ALREADY_PRESENT.create(r.displayName(), attrName(a), id);
        i.addOrReplaceModifier(new AttributeModifier(id, value, op));
        s.sendSuccess(() -> Text.translatable("commands.attribute.modifier.add.success", id, attrName(a), r.displayName()), true);
        return 1;
    }

    static int opRemoveModifier(CommandSource s, Rider r, Attribute a, String id) throws CommandSyntaxException {
        if (!instance(r, a).removeModifier(id)) throw ERROR_NO_SUCH_MODIFIER.create(r.displayName(), attrName(a), id);
        s.sendSuccess(() -> Text.translatable("commands.attribute.modifier.remove.success", id, attrName(a), r.displayName()), true);
        return 1;
    }
}
