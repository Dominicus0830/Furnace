// 바닐라 BlockStateArgument(minecraft:block_state)·BlockStateParser: "블록[속성=값,…]{nbt}" → 블록 상태(+ 블록 엔티티 NBT).
// 같은 파서를 BlockPredicateArgument 가 태그(#태그[…]) 허용으로 쓴다. 오류 문구·커서 위치는 바닐라와 같게.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;

public final class BlockStateArgument implements ArgumentType<BlockStateArgument.BlockInput> {

    private static final List<String> EXAMPLES = List.of("stone", "minecraft:stone", "stone[foo=bar]", "foo{bar=baz}");

    public static final SimpleCommandExceptionType ERROR_NO_TAGS_ALLOWED = new SimpleCommandExceptionType(Text.translatable("argument.block.tag.disallowed"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_BLOCK = new DynamicCommandExceptionType(id -> Text.translatable("argument.block.id.invalid", id));
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_PROPERTY =
            new Dynamic2CommandExceptionType((block, prop) -> Text.translatable("argument.block.property.unknown", block, prop));
    public static final Dynamic2CommandExceptionType ERROR_DUPLICATE_PROPERTY =
            new Dynamic2CommandExceptionType((prop, block) -> Text.translatable("argument.block.property.duplicate", prop, block));
    public static final Dynamic3 ERROR_INVALID_VALUE = (block, value, prop) -> Text.translatable("argument.block.property.invalid", block, value, prop);
    public static final Dynamic2CommandExceptionType ERROR_EXPECTED_VALUE =
            new Dynamic2CommandExceptionType((prop, block) -> Text.translatable("argument.block.property.novalue", prop, block));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_PROPERTIES =
            new SimpleCommandExceptionType(Text.translatable("argument.block.property.unclosed"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(tag -> Text.translatable("arguments.block.tag.unknown", tag));

    /** 인자 셋짜리 오류(바닐라 Dynamic3CommandExceptionType 자리). */
    public interface Dynamic3 {
        Text text(Object a, Object b, Object c);

        default CommandSyntaxException createWithContext(StringReader reader, Object a, Object b, Object c) {
            return new SimpleCommandExceptionType(text(a, b, c)).createWithContext(reader);
        }
    }

    /** BlockInput: 상태 하나 + 블록 엔티티 NBT(없으면 null). */
    public record BlockInput(int state, Object nbt) {}

    /** 파서 결과: 블록이면 state ≥ 0(속성은 그 상태에 반영, 지정한 속성 이름도 남긴다), 태그면 tag 와 문자열 속성. */
    public record Parsed(int state, Map<String, String> properties, String tag, Object nbt) {}

    public static BlockStateArgument block() { return new BlockStateArgument(); }

    public static BlockInput getBlock(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, BlockInput.class); }

    @Override
    public BlockInput parse(StringReader reader) throws CommandSyntaxException {
        Parsed p = parse(reader, false, true);
        return new BlockInput(p.state(), p.nbt());
    }

    /** BlockStateParser.parseForBlock / parseForTesting. */
    public static Parsed parse(StringReader reader, boolean allowTag, boolean allowNbt) throws CommandSyntaxException {
        Blocks blocks = Blocks.get();
        int state = -1;
        String tag = null;
        Map<String, String> props = new LinkedHashMap<>();
        if (allowTag && reader.canRead() && reader.peek() == '#') {
            reader.skip();
            int start = reader.getCursor();
            ResourceId id = ResourceLocationArgument.id().parse(reader);
            String name = id.toString();
            try {
                org.domi.furnace.server.world.gen.feature.BlockTags.get().blocksOf(name);
            } catch (IllegalArgumentException e) {
                reader.setCursor(start);
                throw ERROR_UNKNOWN_TAG.createWithContext(reader, name);
            }
            tag = name;
            if (reader.canRead() && reader.peek() == '[') readVagueProperties(reader, props, name);
        } else {
            if (reader.canRead() && reader.peek() == '#' && !allowTag) throw ERROR_NO_TAGS_ALLOWED.createWithContext(reader);
            int start = reader.getCursor();
            ResourceId id = ResourceLocationArgument.id().parse(reader);
            int block = blocks.block(id.toString());
            if (block < 0) {
                reader.setCursor(start);
                throw ERROR_UNKNOWN_BLOCK.createWithContext(reader, id.toString());
            }
            state = blocks.defaultState(block);
            if (reader.canRead() && reader.peek() == '[') state = readProperties(reader, state, id.toString(), props);
        }
        Object nbt = null;
        if (allowNbt && reader.canRead() && reader.peek() == '{') nbt = readNbt(reader);
        return new Parsed(state, props, tag, nbt);
    }

    private static int readProperties(StringReader reader, int state, String blockId, Map<String, String> seen) throws CommandSyntaxException {
        reader.skip();
        reader.skipWhitespace();
        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            int start = reader.getCursor();
            String name = reader.readString();
            Prop prop = propOf(name, state);
            if (prop == null) {
                reader.setCursor(start);
                throw ERROR_UNKNOWN_PROPERTY.createWithContext(reader, blockId, name);
            }
            if (seen.containsKey(name)) {
                reader.setCursor(start);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(reader, name, blockId);
            }
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '=') throw ERROR_EXPECTED_VALUE.createWithContext(reader, name, blockId);
            reader.skip();
            reader.skipWhitespace();
            int valueStart = reader.getCursor();
            String value = reader.readString();
            if (prop.indexOf(state, value) < 0) {
                reader.setCursor(valueStart);
                throw ERROR_INVALID_VALUE.createWithContext(reader, blockId, value, name);
            }
            state = prop.with(state, value);
            seen.put(name, value);
            reader.skipWhitespace();
            if (!reader.canRead()) continue;
            if (reader.peek() == ',') {
                reader.skip();
                continue;
            }
            if (reader.peek() != ']') throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
        }
        if (!reader.canRead()) throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
        reader.skip();
        return state;
    }

    /** readVagueProperties: 태그의 속성은 문자열로만 남긴다(값 검사는 비교 때). */
    private static void readVagueProperties(StringReader reader, Map<String, String> out, String tagId) throws CommandSyntaxException {
        reader.skip();
        int lastStart = -1;
        reader.skipWhitespace();
        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            int start = reader.getCursor();
            String name = reader.readString();
            if (out.containsKey(name)) {
                reader.setCursor(start);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(reader, name, tagId);
            }
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '=') {
                reader.setCursor(start);
                throw ERROR_EXPECTED_VALUE.createWithContext(reader, name, tagId);
            }
            reader.skip();
            reader.skipWhitespace();
            lastStart = reader.getCursor();
            String value = reader.readString();
            out.put(name, value);
            reader.skipWhitespace();
            if (!reader.canRead()) continue;
            lastStart = -1;
            if (reader.peek() == ',') {
                reader.skip();
                continue;
            }
            if (reader.peek() != ']') throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
        }
        if (!reader.canRead()) {
            if (lastStart >= 0) reader.setCursor(lastStart);
            throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
        }
        reader.skip();
    }

    /** 그 블록에 그 이름의 속성이 있으면. */
    static Prop propOf(String name, int state) {
        for (Prop p : Blocks.get().propsOf(Blocks.get().blockOf(state))) if (p.name().equals(name)) return p;
        return null;
    }

    /** TagParser.readStruct — SNBT compound 하나. */
    private static Object readNbt(StringReader reader) throws CommandSyntaxException {
        return new TagParser(reader).readStruct();
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:block_state"; }
}
