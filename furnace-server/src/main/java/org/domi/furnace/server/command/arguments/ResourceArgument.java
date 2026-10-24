// 바닐라 ResourceArgument(minecraft:resource) — 레지스트리 하나를 정해 두고 id 를 읽는다. 속성: 레지스트리 이름.
package org.domi.furnace.server.command.arguments;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;

public final class ResourceArgument implements ArgumentType<ResourceId> {
    private final String registry;

    public ResourceArgument(String registry) { this.registry = registry; }

    public static ResourceArgument resource(String registry) { return new ResourceArgument(registry); }

    @Override
    public ResourceId parse(StringReader reader) throws CommandSyntaxException { return ResourceLocationArgument.id().parse(reader); }

    @Override
    public Collection<String> getExamples() { return List.of("foo", "foo:bar", "012"); }

    @Override
    public String networkId() { return "minecraft:resource"; }

    @Override
    public void writeProperties(ByteBuf buf) { Types.writeString(buf, registry); }
}
