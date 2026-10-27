// 바닐라 ItemArgument(minecraft:item_stack)·ItemParser: "아이템[컴포넌트=SNBT,!컴포넌트,…]" → 아이템 + 컴포넌트 패치. 속성 없음.
// 컴포넌트 값은 SNBT 로 읽어 그 종류의 저장 코덱으로 정규화한다. 오류 키·커서 위치는 바닐라와 같게.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ComponentType;
import org.domi.furnace.server.item.DataComponents;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemPrototypes;

public final class ItemArgument implements ArgumentType<ItemArgument.ItemInput> {

    private static final List<String> EXAMPLES = List.of("stick", "minecraft:stick", "stick{foo=bar}");

    public static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(id -> Text.translatable("argument.item.id.invalid", id));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(id -> Text.translatable("arguments.item.component.unknown", id));
    public static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT =
            new Dynamic2CommandExceptionType((type, err) -> Text.translatable("arguments.item.component.malformed", type, err));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_COMPONENT = new SimpleCommandExceptionType(Text.translatable("arguments.item.component.expected"));
    public static final DynamicCommandExceptionType ERROR_REPEATED_COMPONENT = new DynamicCommandExceptionType(t -> Text.translatable("arguments.item.component.repeated", t));
    public static final DynamicCommandExceptionType ERROR_MALFORMED_ITEM = new DynamicCommandExceptionType(e -> Text.translatable("arguments.item.malformed", e));
    public static final Dynamic2CommandExceptionType ERROR_STACK_TOO_BIG =
            new Dynamic2CommandExceptionType((item, max) -> Text.translatable("arguments.item.overstacked", item, max));

    /** 바닐라 ItemInput: 아이템(레지스트리 이름·숫자 id) + 컴포넌트 패치(기본값 대비 정리 전). */
    public record ItemInput(String item, int itemId, ComponentPatch components) {

        /** createItemStack(count, allowOversizedStacks=check): check 면 최대 스택을 넘을 때 오류. */
        public ItemStack createItemStack(int count, boolean check) throws CommandSyntaxException {
            ItemStack s = new ItemStack(itemId, count, components.normalizedFor(item));
            int max = maxStackSize(s);
            if (check && count > max) throw ERROR_STACK_TOO_BIG.create(item, max);
            return s;
        }
    }

    public static ItemArgument item() { return new ItemArgument(); }

    public static ItemInput getItem(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, ItemInput.class); }

    /** 기본값 + 패치를 본 최대 스택(바닐라 getMaxStackSize). */
    public static int maxStackSize(ItemStack s) {
        Object v = componentValue(s, "minecraft:max_stack_size");
        return v instanceof Number n ? n.intValue() : 1;
    }

    /** 기본값 + 패치의 정규 값(없거나 지웠으면 null) — 바닐라 ItemStack.get. */
    public static Object componentValue(ItemStack s, String component) {
        if (s.isEmpty()) return null;
        Map<String, Object> p = s.patch().entries();
        if (p.containsKey(component)) return p.get(component);
        String name = ItemContext.name("minecraft:item", s.itemId());
        return name == null ? null : ItemPrototypes.defaultValue(name, component);
    }

    @Override
    public ItemInput parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        ItemInput in;
        try {
            in = parseItem(reader);
        } catch (CommandSyntaxException e) {
            reader.setCursor(start);
            throw e;
        }
        validate(reader, in);
        return in;
    }

    private static ItemInput parseItem(StringReader reader) throws CommandSyntaxException {
        int idStart = reader.getCursor();
        ResourceId id = ResourceLocationArgument.id().parse(reader);
        Integer num = ItemContext.id("minecraft:item", id.toString());
        if (num == null) {
            reader.setCursor(idStart);
            throw ERROR_UNKNOWN_ITEM.createWithContext(reader, id.toString());
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        if (reader.canRead() && reader.peek() == '[') readComponents(reader, patch);
        return new ItemInput(id.toString(), num, ComponentPatch.ofNormalized(patch));
    }

    private static void readComponents(StringReader reader, Map<String, Object> out) throws CommandSyntaxException {
        reader.expect('[');
        Set<ComponentType> seen = new HashSet<>();
        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            if (reader.canRead() && reader.peek() == '!') {
                reader.skip();
                ComponentType t = readComponentType(reader);
                if (!seen.add(t)) throw ERROR_REPEATED_COMPONENT.create(t.name());
                out.put(t.name(), null);
                reader.skipWhitespace();
            } else {
                ComponentType t = readComponentType(reader);
                if (!seen.add(t)) throw ERROR_REPEATED_COMPONENT.create(t.name());
                reader.skipWhitespace();
                reader.expect('=');
                reader.skipWhitespace();
                out.put(t.name(), readValue(reader, t));
                reader.skipWhitespace();
            }
            if (!reader.canRead() || reader.peek() != ',') break;
            reader.skip();
            reader.skipWhitespace();
            if (!reader.canRead()) throw ERROR_EXPECTED_COMPONENT.createWithContext(reader);
        }
        reader.expect(']');
    }

    /** 저장하지 않는(transient) 종류는 모르는 종류로 본다. */
    public static ComponentType readComponentType(StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead()) throw ERROR_EXPECTED_COMPONENT.createWithContext(reader);
        int start = reader.getCursor();
        ResourceId id = ResourceLocationArgument.id().parse(reader);
        ComponentType t = DataComponents.byName(id.toString());
        if (t != null && !t.isTransient()) return t;
        reader.setCursor(start);
        throw ERROR_UNKNOWN_COMPONENT.createWithContext(reader, id.toString());
    }

    private static Object readValue(StringReader reader, ComponentType t) throws CommandSyntaxException {
        int start = reader.getCursor();
        Object tag = readTag(reader);
        try {
            return t.persistent().normalize(tag);
        } catch (RuntimeException e) {
            reader.setCursor(start);
            throw ERROR_MALFORMED_COMPONENT.createWithContext(reader, t.toString(), String.valueOf(e.getMessage()));
        }
    }

    /** TagParser.readValue — SNBT 값 하나. */
    public static Object readTag(StringReader reader) throws CommandSyntaxException {
        int[] end = new int[1];
        try {
            Object v = org.domi.furnace.server.item.Snbt.parsePrefix(reader.getString(), reader.getCursor(), end);
            reader.setCursor(end[0]);
            return v;
        } catch (RuntimeException e) {
            throw new SimpleCommandExceptionType(Text.text(String.valueOf(e.getMessage()))).createWithContext(reader);
        }
    }

    /** ItemStack.validateComponents: 내구도가 있으면서 겹칠 수 없다. */
    private static void validate(StringReader reader, ItemInput in) throws CommandSyntaxException {
        ItemStack s = new ItemStack(in.itemId(), 1, in.components().normalizedFor(in.item()));
        if (componentValue(s, "minecraft:max_damage") != null && maxStackSize(s) > 1) {
            throw ERROR_MALFORMED_ITEM.createWithContext(reader, "Item cannot be both damageable and stackable");
        }
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:item_stack"; }
}
