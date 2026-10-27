// 바닐라 ItemPredicateArgument(minecraft:item_predicate)·ComponentPredicateParser 문법:
//   top        := type ('[' conditions? ']')?
//   type       := 아이템 | '#'태그 | '*'
//   conditions := alternatives (',' conditions)?      — 모두 만족
//   alternatives := term ('|' alternatives)?          — 하나라도
//   term       := test | '!' test
//   test       := 컴포넌트 '=' SNBT | 하위술어 '~' SNBT | 컴포넌트(있는가)
// 각 토큰 앞 공백은 건너뛴다(바닐라 문법 규칙과 같다). 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ComponentType;
import org.domi.furnace.server.item.DataComponents;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.Val;

public final class ItemPredicateArgument implements ArgumentType<ItemPredicateArgument.Result> {

    private static final List<String> EXAMPLES = List.of("stick", "minecraft:stick", "#stick", "#stick{foo:'bar'}");

    public static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(id -> Text.translatable("argument.item.id.invalid", id));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(id -> Text.translatable("arguments.item.tag.unknown", id));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(id -> Text.translatable("arguments.item.component.unknown", id));
    public static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT =
            new Dynamic2CommandExceptionType((t, e) -> Text.translatable("arguments.item.component.malformed", t, e));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_PREDICATE = new DynamicCommandExceptionType(id -> Text.translatable("arguments.item.predicate.unknown", id));
    public static final Dynamic2CommandExceptionType ERROR_MALFORMED_PREDICATE =
            new Dynamic2CommandExceptionType((t, e) -> Text.translatable("arguments.item.predicate.malformed", t, e));

    /** 바닐라 item_sub_predicate_type 레지스트리(1.21.1). count 는 따로 넣은 가짜 종류다. */
    private static final List<String> SUB_PREDICATES = List.of("minecraft:damage", "minecraft:enchantments", "minecraft:stored_enchantments",
            "minecraft:potion_contents", "minecraft:custom_data", "minecraft:container", "minecraft:bundle_contents",
            "minecraft:firework_explosion", "minecraft:fireworks", "minecraft:writable_book_content", "minecraft:written_book_content",
            "minecraft:attribute_modifiers", "minecraft:trim", "minecraft:jukebox_playable");

    public interface Result extends Predicate<ItemStack> {}

    public static ItemPredicateArgument itemPredicate() { return new ItemPredicateArgument(); }

    public static Result getItemPredicate(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Result.class); }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        try {
            List<Predicate<ItemStack>> all = new ArrayList<>();
            Predicate<ItemStack> type = readType(reader);
            if (type != null) all.add(type);
            int save = reader.getCursor();
            reader.skipWhitespace();
            if (reader.canRead() && reader.peek() == '[') {
                reader.skip();
                reader.skipWhitespace();
                if (reader.canRead() && reader.peek() != ']') readConditions(reader, all);
                reader.skipWhitespace();
                reader.expect(']');
            } else {
                reader.setCursor(save);
            }
            List<Predicate<ItemStack>> fixed = List.copyOf(all);
            return s -> {
                for (Predicate<ItemStack> p : fixed) if (!p.test(s)) return false;
                return true;
            };
        } catch (CommandSyntaxException e) {
            reader.setCursor(start);
            throw e;
        }
    }

    /** '*' 이면 null(조건 없음). */
    private static Predicate<ItemStack> readType(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();
        if (reader.canRead() && reader.peek() == '*') {
            reader.skip();
            return null;
        }
        if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
            reader.skipWhitespace();
            ResourceId id = ResourceLocationArgument.id().parse(reader);
            String tag = id.toString();
            if (!ItemContext.hasTag("minecraft:item", tag)) throw ERROR_UNKNOWN_TAG.createWithContext(reader, tag);
            return s -> !s.isEmpty() && ItemContext.isInTag("minecraft:item", tag, s.itemId());
        }
        ResourceId id = ResourceLocationArgument.id().parse(reader);
        Integer num = ItemContext.id("minecraft:item", id.toString());
        if (num == null) throw ERROR_UNKNOWN_ITEM.createWithContext(reader, id.toString());
        int item = num;
        return s -> s.itemId() == item && !s.isEmpty();
    }

    private static void readConditions(StringReader reader, List<Predicate<ItemStack>> out) throws CommandSyntaxException {
        while (true) {
            out.add(readAlternatives(reader));
            int save = reader.getCursor();
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != ',') {
                reader.setCursor(save);
                return;
            }
            reader.skip();
        }
    }

    private static Predicate<ItemStack> readAlternatives(StringReader reader) throws CommandSyntaxException {
        List<Predicate<ItemStack>> any = new ArrayList<>();
        while (true) {
            any.add(readTerm(reader));
            int save = reader.getCursor();
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '|') {
                reader.setCursor(save);
                break;
            }
            reader.skip();
        }
        if (any.size() == 1) return any.get(0);
        List<Predicate<ItemStack>> fixed = List.copyOf(any);
        return s -> {
            for (Predicate<ItemStack> p : fixed) if (p.test(s)) return true;
            return false;
        };
    }

    private static Predicate<ItemStack> readTerm(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();
        if (reader.canRead() && reader.peek() == '!') {
            reader.skip();
            return readTest(reader).negate();
        }
        return readTest(reader);
    }

    private static Predicate<ItemStack> readTest(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();
        ResourceId id = ResourceLocationArgument.id().parse(reader);
        String name = id.toString();
        int save = reader.getCursor();
        reader.skipWhitespace();
        char next = reader.canRead() ? reader.peek() : 0;
        if (next == '~') {
            reader.skip();
            reader.skipWhitespace();
            if (!name.equals("minecraft:count") && !SUB_PREDICATES.contains(name)) throw ERROR_UNKNOWN_PREDICATE.createWithContext(reader, name);
            Object tag = ItemArgument.readTag(reader);
            return subPredicate(reader, name, tag);
        }
        if (name.equals("minecraft:count")) {
            if (next == '=') {
                reader.skip();
                reader.skipWhitespace();
                Object tag = ItemArgument.readTag(reader);
                Ints b = ints(reader, name, tag, true);
                return s -> b.matches(s.count());
            }
            reader.setCursor(save);
            return s -> true;
        }
        ComponentType t = DataComponents.byName(name);
        if (t == null || t.isTransient()) throw ERROR_UNKNOWN_COMPONENT.createWithContext(reader, name);
        if (next == '=') {
            reader.skip();
            reader.skipWhitespace();
            Object tag = ItemArgument.readTag(reader);
            Object value;
            try {
                value = t.persistent().normalize(tag);
            } catch (RuntimeException e) {
                throw ERROR_MALFORMED_COMPONENT.createWithContext(reader, name, String.valueOf(e.getMessage()));
            }
            return s -> t.same(value, ItemArgument.componentValue(s, t.name()));
        }
        reader.setCursor(save);
        return s -> ItemArgument.componentValue(s, t.name()) != null;
    }

    /** 하위 술어: count 와 damage 만 구현. 나머지 종류는 형식 오류로 막는다(가정 — 아직 술어 코덱이 없다). */
    private static Predicate<ItemStack> subPredicate(StringReader reader, String name, Object tag) throws CommandSyntaxException {
        switch (name) {
            case "minecraft:count" -> {
                Ints b = ints(reader, name, tag, false);
                return s -> b.matches(s.count());
            }
            case "minecraft:damage" -> {
                Map<String, Object> m;
                try {
                    m = Val.map(tag);
                } catch (RuntimeException e) {
                    throw ERROR_MALFORMED_PREDICATE.createWithContext(reader, name, String.valueOf(e.getMessage()));
                }
                Ints durability = m.containsKey("durability") ? ints(reader, name, m.get("durability"), false) : Ints.ANY;
                Ints damage = m.containsKey("damage") ? ints(reader, name, m.get("damage"), false) : Ints.ANY;
                return s -> {
                    if (!(ItemArgument.componentValue(s, "minecraft:damage") instanceof Number d)) return false;
                    Object max = ItemArgument.componentValue(s, "minecraft:max_damage");
                    int md = max instanceof Number n ? n.intValue() : 0;
                    return durability.matches(md - d.intValue()) && damage.matches(d.intValue());
                };
            }
            default -> throw ERROR_MALFORMED_PREDICATE.createWithContext(reader, name, "Not supported yet");
        }
    }

    /** MinMaxBounds.Ints.CODEC: 정수 하나, 또는 {min?, max?}. */
    record Ints(Integer min, Integer max) {
        static final Ints ANY = new Ints(null, null);

        boolean matches(int v) { return (min == null || min <= v) && (max == null || v <= max); }
    }

    private static Ints ints(StringReader reader, String name, Object tag, boolean component) throws CommandSyntaxException {
        try {
            if (tag instanceof Number n) return new Ints(n.intValue(), n.intValue());
            Map<String, Object> m = Val.map(tag);
            Integer min = m.containsKey("min") ? Val.number(m.get("min")).intValue() : null;
            Integer max = m.containsKey("max") ? Val.number(m.get("max")).intValue() : null;
            if (min != null && max != null && min > max) throw new IllegalArgumentException("Swapped values inside range: " + min + " > " + max);
            return new Ints(min, max);
        } catch (RuntimeException e) {
            throw (component ? ERROR_MALFORMED_COMPONENT : ERROR_MALFORMED_PREDICATE).createWithContext(reader, name, String.valueOf(e.getMessage()));
        }
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:item_predicate"; }
}
