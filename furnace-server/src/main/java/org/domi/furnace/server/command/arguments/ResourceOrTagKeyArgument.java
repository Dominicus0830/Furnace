// 바닐라 ResourceOrTagKeyArgument(minecraft:resource_or_tag_key) — "#태그" 또는 id. 속성: 레지스트리 이름.
package org.domi.furnace.server.command.arguments;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;

public final class ResourceOrTagKeyArgument implements ArgumentType<ResourceOrTagKeyArgument.Result> {

    /** id 또는 태그(tag=true 면 id 가 태그 이름). */
    public record Result(ResourceId id, boolean tag) {
        /** 바닐라 asPrintable: 태그는 "#" 를 붙인다. */
        public String printable() { return tag ? "#" + id : id.toString(); }
    }

    private final String registry;
    private final String networkId;

    private ResourceOrTagKeyArgument(String registry, String networkId) { this.registry = registry; this.networkId = networkId; }

    /** 바닐라 ResourceOrTagArgument(minecraft:resource_or_tag): 파싱은 같고 네트워크 id 만 다르다. */
    public static ResourceOrTagKeyArgument resourceOrTag(String registry) { return new ResourceOrTagKeyArgument(registry, "minecraft:resource_or_tag"); }

    public static ResourceOrTagKeyArgument resourceOrTagKey(String registry) { return new ResourceOrTagKeyArgument(registry, "minecraft:resource_or_tag_key"); }

    public static Result get(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Result.class); }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            int cursor = reader.getCursor();
            try {
                reader.skip();
                return new Result(ResourceLocationArgument.id().parse(reader), true);
            } catch (CommandSyntaxException e) {
                reader.setCursor(cursor);
                throw e;
            }
        }
        return new Result(ResourceLocationArgument.id().parse(reader), false);
    }

    @Override
    public Collection<String> getExamples() { return List.of("foo", "foo:bar", "012", "#skeletons", "#minecraft:skeletons"); }

    @Override
    public String networkId() { return networkId; }

    @Override
    public void writeProperties(ByteBuf buf) { Types.writeString(buf, registry); }
}
