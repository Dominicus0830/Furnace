// 바닐라 NbtTagArgument(minecraft:nbt_tag) — SNBT 태그 하나(TagParser.readValue). 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;

public final class NbtTagArgument implements ArgumentType<NbtTagArgument.Tag> {

    /** 파싱된 태그(값 모형이 Object 라 꺼낼 때 종류를 구분하려고 감싼다). */
    public record Tag(Object value) {}

    private static final List<String> EXAMPLES = List.of("0", "0b", "0l", "0.0", "\"foo\"", "{foo=bar}", "[0]");

    public static NbtTagArgument nbtTag() { return new NbtTagArgument(); }

    public static Object getNbtTag(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Tag.class).value(); }

    @Override
    public Tag parse(StringReader reader) throws CommandSyntaxException { return new Tag(new TagParser(reader).readValue()); }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:nbt_tag"; }
}
