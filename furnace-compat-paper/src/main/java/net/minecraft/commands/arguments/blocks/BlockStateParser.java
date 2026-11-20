package net.minecraft.commands.arguments.blocks;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.domi.furnace.compat.nms.NmsBrigadier;

/**
 * 블록 상태 글 읽기(Mojang 이름 shim, NMS shim 5e-3c — Axiom 의 허용 블록 목록): {@code stone}·{@code oak_log[axis=y]}·{@code #minecraft:logs[axis=y]}·
 * {@code chest{Lock:"a"}} 꼴. 문법·오류 종류·인자·커서 자리는 Paper 1.21.1 바이트코드(javap)대로 Furnace 가 새로 썼다 — 블록·태그는 건네받은 조회기에서 찾고
 * (거르는 조회기면 걸러진 블록은 "Unknown block type"), 지정한 속성은 쓴 차례대로, 태그의 속성은 글자 그대로(HashMap) 남긴다. 태그 뒤 속성 오류의 블록
 * 이름 자리는 바닐라처럼 "minecraft:"(태그는 블록 이름을 정하지 않는다). 오류 메시지는 {@link NmsBrigadier}(번역 글은 같고 원본이 Component 가 아닌 점만 다르다).
 * 명령 제안({@code fillSuggestions})은 없다.
 */
public class BlockStateParser {

    public static final SimpleCommandExceptionType ERROR_NO_TAGS_ALLOWED = new SimpleCommandExceptionType(NmsBrigadier.translatable("argument.block.tag.disallowed"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_BLOCK = new DynamicCommandExceptionType(id -> NmsBrigadier.translatable("argument.block.id.invalid", id));
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_PROPERTY =
            new Dynamic2CommandExceptionType((block, prop) -> NmsBrigadier.translatable("argument.block.property.unknown", block, prop));
    public static final Dynamic2CommandExceptionType ERROR_DUPLICATE_PROPERTY =
            new Dynamic2CommandExceptionType((block, prop) -> NmsBrigadier.translatable("argument.block.property.duplicate", prop, block));
    public static final Dynamic3CommandExceptionType ERROR_INVALID_VALUE =
            new Dynamic3CommandExceptionType((block, prop, value) -> NmsBrigadier.translatable("argument.block.property.invalid", block, value, prop));
    public static final Dynamic2CommandExceptionType ERROR_EXPECTED_VALUE =
            new Dynamic2CommandExceptionType((block, prop) -> NmsBrigadier.translatable("argument.block.property.novalue", block, prop));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_PROPERTIES =
            new SimpleCommandExceptionType(NmsBrigadier.translatable("argument.block.property.unclosed"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(tag -> NmsBrigadier.translatable("arguments.block.tag.unknown", tag));

    private final HolderLookup<Block> blocks;
    private final StringReader reader;
    private final boolean forTesting;
    private final boolean allowNbt;
    private final Map<Property<?>, Comparable<?>> properties = new LinkedHashMap<>();
    private final Map<String, String> vagueProperties = new HashMap<>();
    private ResourceLocation id = ResourceLocation.withDefaultNamespace("");
    private StateDefinition<Block, BlockState> definition;
    private BlockState state;
    private CompoundTag nbt;
    private HolderSet<Block> tag;

    private BlockStateParser(HolderLookup<Block> blocks, StringReader reader, boolean forTesting, boolean allowNbt) {
        this.blocks = blocks;
        this.reader = reader;
        this.forTesting = forTesting;
        this.allowNbt = allowNbt;
    }

    public static BlockResult parseForBlock(HolderLookup<Block> blocks, String input, boolean allowNbt) throws CommandSyntaxException {
        return parseForBlock(blocks, new StringReader(input), allowNbt);
    }

    /** 블록 하나(태그 #… 는 "Tags aren't allowed here"). 틀리면 커서를 읽기 전 자리로 되돌리고 던진다. */
    public static BlockResult parseForBlock(HolderLookup<Block> blocks, StringReader reader, boolean allowNbt) throws CommandSyntaxException {
        int start = reader.getCursor();
        try {
            BlockStateParser p = new BlockStateParser(blocks, reader, false, allowNbt);
            p.parse();
            return new BlockResult(p.state, p.properties, p.nbt);
        } catch (CommandSyntaxException e) {
            reader.setCursor(start);
            throw e;
        }
    }

    public static Either<BlockResult, TagResult> parseForTesting(HolderLookup<Block> blocks, String input, boolean allowNbt) throws CommandSyntaxException {
        return parseForTesting(blocks, new StringReader(input), allowNbt);
    }

    /** 블록이면 왼쪽, 태그(#…)면 오른쪽. 틀리면 커서를 읽기 전 자리로 되돌리고 던진다. */
    public static Either<BlockResult, TagResult> parseForTesting(HolderLookup<Block> blocks, StringReader reader, boolean allowNbt) throws CommandSyntaxException {
        int start = reader.getCursor();
        try {
            BlockStateParser p = new BlockStateParser(blocks, reader, true, allowNbt);
            p.parse();
            return p.tag != null ? Either.right(new TagResult(p.tag, p.vagueProperties, p.nbt)) : Either.left(new BlockResult(p.state, p.properties, p.nbt));
        } catch (CommandSyntaxException e) {
            reader.setCursor(start);
            throw e;
        }
    }

    private void parse() throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            readTag();
            if (reader.canRead() && reader.peek() == '[') readVagueProperties();
        } else {
            readBlock();
            if (reader.canRead() && reader.peek() == '[') readProperties();
        }
        if (allowNbt && reader.canRead() && reader.peek() == '{') readNbt();
    }

    private void readBlock() throws CommandSyntaxException {
        int start = reader.getCursor();
        id = ResourceLocation.read(reader);
        Optional<? extends net.minecraft.core.Holder.Reference<Block>> found = blocks.get(ResourceKey.create(Registries.BLOCK, id));
        if (found.isEmpty()) {
            reader.setCursor(start);
            throw ERROR_UNKNOWN_BLOCK.createWithContext(reader, id.toString());
        }
        Block block = found.get().value();
        definition = block.getStateDefinition();
        state = block.defaultBlockState();
    }

    private void readTag() throws CommandSyntaxException {
        if (!forTesting) throw ERROR_NO_TAGS_ALLOWED.createWithContext(reader);
        int start = reader.getCursor();
        reader.expect('#');
        ResourceLocation tagId = ResourceLocation.read(reader);
        Optional<? extends HolderSet.Named<Block>> found = blocks.get(TagKey.create(Registries.BLOCK, tagId));
        if (found.isEmpty()) {
            reader.setCursor(start);
            throw ERROR_UNKNOWN_TAG.createWithContext(reader, tagId.toString());
        }
        tag = found.get();
    }

    private void readProperties() throws CommandSyntaxException {
        reader.skip();
        reader.skipWhitespace();
        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            int start = reader.getCursor();
            String name = reader.readString();
            Property<?> property = definition.getProperty(name);
            if (property == null) {
                reader.setCursor(start);
                throw ERROR_UNKNOWN_PROPERTY.createWithContext(reader, id.toString(), name);
            }
            if (properties.containsKey(property)) {
                reader.setCursor(start);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(reader, id.toString(), name);
            }
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '=') throw ERROR_EXPECTED_VALUE.createWithContext(reader, id.toString(), name);
            reader.skip();
            reader.skipWhitespace();
            int valueStart = reader.getCursor();
            setValue(property, reader.readString(), valueStart);
            reader.skipWhitespace();
            if (!reader.canRead()) continue;
            if (reader.peek() == ',') {
                reader.skip();
                continue;
            }
            if (reader.peek() != ']') throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
            break;
        }
        if (reader.canRead()) reader.skip();
        else throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
    }

    private void readVagueProperties() throws CommandSyntaxException {
        reader.skip();
        int lastValueStart = -1;
        reader.skipWhitespace();
        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            int start = reader.getCursor();
            String name = reader.readString();
            if (vagueProperties.containsKey(name)) {
                reader.setCursor(start);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(reader, id.toString(), name);
            }
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '=') {
                reader.setCursor(start);
                throw ERROR_EXPECTED_VALUE.createWithContext(reader, id.toString(), name);
            }
            reader.skip();
            reader.skipWhitespace();
            lastValueStart = reader.getCursor();
            String value = reader.readString();
            vagueProperties.put(name, value);
            reader.skipWhitespace();
            if (!reader.canRead()) continue;
            lastValueStart = -1;
            if (reader.peek() == ',') {
                reader.skip();
                continue;
            }
            if (reader.peek() != ']') throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
            break;
        }
        if (reader.canRead()) {
            reader.skip();
            return;
        }
        if (lastValueStart >= 0) reader.setCursor(lastValueStart);
        throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(reader);
    }

    private void readNbt() throws CommandSyntaxException { nbt = new TagParser(reader).readStruct(); }

    private <T extends Comparable<T>> void setValue(Property<T> property, String value, int valueStart) throws CommandSyntaxException {
        Optional<T> v = property.getValue(value);
        if (v.isEmpty()) {
            reader.setCursor(valueStart);
            throw ERROR_INVALID_VALUE.createWithContext(reader, id.toString(), property.getName(), value);
        }
        state = state.setValue(property, v.get());
        properties.put(property, v.get());
    }

    /** "minecraft:oak_log[axis=y]" 꼴(속성은 상태의 차례 — 이름순). 등록 안 된 블록은 "air". */
    public static String serialize(BlockState state) {
        StringBuilder b = new StringBuilder(state.getBlockHolder().unwrapKey().map(k -> k.location().toString()).orElse("air"));
        if (!state.getProperties().isEmpty()) {
            b.append('[');
            boolean comma = false;
            for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
                if (comma) b.append(',');
                appendProperty(b, e.getKey(), e.getValue());
                comma = true;
            }
            b.append(']');
        }
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> void appendProperty(StringBuilder b, Property<T> property, Comparable<?> value) {
        b.append(property.getName()).append('=').append(property.getName((T) value));
    }

    /** 블록 하나: 상태, 지정한 속성(쓴 차례), 블록 엔티티 NBT(없으면 null). */
    public record BlockResult(BlockState blockState, Map<Property<?>, Comparable<?>> properties, CompoundTag nbt) {}

    /** 태그 하나: 블록 묶음, 글자 그대로의 속성, NBT(없으면 null). */
    public record TagResult(HolderSet<Block> tag, Map<String, String> vagueProperties, CompoundTag nbt) {}
}
