// 바닐라 CompoundTagArgument(minecraft:nbt_compound_tag) — SNBT 복합 태그 하나. 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class CompoundTagArgument implements ArgumentType<NbtCompound> {

    private static final List<String> EXAMPLES = List.of("{}", "{foo=bar}");

    public static CompoundTagArgument compoundTag() { return new CompoundTagArgument(); }

    public static NbtCompound getCompoundTag(CommandContext<CommandSource> context, String name) {
        return context.getArgument(name, NbtCompound.class);
    }

    @Override
    public NbtCompound parse(StringReader reader) throws CommandSyntaxException {
        return new TagParser(reader).readStruct();
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:nbt_compound_tag"; }
}
