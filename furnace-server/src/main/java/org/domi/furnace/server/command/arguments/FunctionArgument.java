// 바닐라 FunctionArgument(hb, minecraft:function) — "ns:path" 또는 "#ns:태그". 해석은 실행 때 서버 함수 라이브러리에서 한다.
// 없는 함수는 arguments.function.unknown, 정의가 없는 태그는 라이브러리가 빈 목록을 돌려주므로 오류가 아니라 빈 목록이다
// (tag.unknown 은 라이브러리가 null 을 돌려줄 때만 — 현재 구현에서는 도달하지 않는 방어 분기).
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.function.CommandFunction;
import org.domi.furnace.server.function.FunctionLibrary;

public final class FunctionArgument implements ArgumentType<FunctionArgument.Ref> {

    private static final List<String> EXAMPLES = List.of("foo", "foo:bar", "#foo");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG =
            new DynamicCommandExceptionType(id -> Text.translatable("arguments.function.tag.unknown", id));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_FUNCTION =
            new DynamicCommandExceptionType(id -> Text.translatable("arguments.function.unknown", id));

    /** 파싱 결과: 이름과 태그 여부. */
    public record Ref(ResourceId id, boolean tag) {}

    /** 이름과 해석된 함수들(바닐라 Pair<ResourceLocation, Collection>). */
    public record Resolved(ResourceId id, Collection<CommandFunction> functions) {}

    public static FunctionArgument functions() { return new FunctionArgument(); }

    @Override
    public Ref parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
            return new Ref(ResourceLocationArgument.id().parse(reader), true);
        }
        return new Ref(ResourceLocationArgument.id().parse(reader), false);
    }

    private static FunctionLibrary library(CommandContext<CommandSource> c) { return c.getSource().getServer().functions().library(); }

    public static Resolved getFunctionCollection(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
        Ref ref = c.getArgument(name, Ref.class);
        FunctionLibrary lib = library(c);
        if (ref.tag()) {
            Collection<CommandFunction> tag = lib.tag(ref.id());
            if (tag == null) throw ERROR_UNKNOWN_TAG.create(ref.id().toString());
            return new Resolved(ref.id(), tag);
        }
        CommandFunction f = lib.function(ref.id()).orElseThrow(() -> ERROR_UNKNOWN_FUNCTION.create(ref.id().toString()));
        return new Resolved(ref.id(), List.of(f));
    }

    public static Collection<CommandFunction> getFunctions(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
        return getFunctionCollection(c, name).functions();
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:function"; }
}
