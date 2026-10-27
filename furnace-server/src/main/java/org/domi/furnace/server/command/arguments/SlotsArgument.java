// 바닐라 SlotsArgument(minecraft:item_slots): 슬롯 이름(범위 포함) → 슬롯 번호 목록. 속성 없음.
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

public final class SlotsArgument implements ArgumentType<SlotRanges.Range> {

    private static final List<String> EXAMPLES = List.of("container.*", "container.5", "weapon");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SLOT = new DynamicCommandExceptionType(n -> Text.translatable("slot.unknown", n));

    public static SlotsArgument slots() { return new SlotsArgument(); }

    public static SlotRanges.Range getSlots(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, SlotRanges.Range.class); }

    @Override
    public SlotRanges.Range parse(StringReader reader) throws CommandSyntaxException {
        String name = SlotArgument.readName(reader);
        SlotRanges.Range r = SlotRanges.byName(name);
        if (r == null) throw ERROR_UNKNOWN_SLOT.createWithContext(reader, name);
        return r;
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:item_slots"; }
}
