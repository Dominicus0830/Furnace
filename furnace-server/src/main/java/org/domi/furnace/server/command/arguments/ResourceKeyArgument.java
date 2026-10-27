// 바닐라 ResourceKeyArgument(minecraft:resource_key) — 레지스트리 하나를 정해 두고 id 를 읽는다(존재 확인은 명령 본문에서). 속성: 레지스트리 이름.
package org.domi.furnace.server.command.arguments;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;

public final class ResourceKeyArgument implements ArgumentType<ResourceId> {
    private static final List<String> EXAMPLES = List.of("foo", "foo:bar", "012");

    private final String registry;
    /** 서버 쪽 제안(바닐라 suggestRegistryElements ELEMENTS) — 이 서버가 데이터를 가진 id 들. */
    private final Supplier<Iterable<String>> elements;

    private ResourceKeyArgument(String registry, Supplier<Iterable<String>> elements) {
        this.registry = registry;
        this.elements = elements;
    }

    public static ResourceKeyArgument key(String registry, Supplier<Iterable<String>> elements) { return new ResourceKeyArgument(registry, elements); }

    public static ResourceId get(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, ResourceId.class); }

    @Override
    public ResourceId parse(StringReader reader) throws CommandSyntaxException { return ResourceLocationArgument.id().parse(reader); }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSource)) return Suggestions.empty();
        return SharedSuggestions.suggest(elements.get(), builder);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:resource_key"; }

    @Override
    public void writeProperties(ByteBuf buf) { Types.writeString(buf, registry); }
}
