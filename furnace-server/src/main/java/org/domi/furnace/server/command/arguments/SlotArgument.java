// 바닐라 SlotArgument(minecraft:item_slot): 슬롯 이름 하나 → 슬롯 번호. 범위 이름(여러 칸)은 거절. 속성 없음.
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

public final class SlotArgument implements ArgumentType<Integer> {

    private static final List<String> EXAMPLES = List.of("container.5", "weapon");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SLOT = new DynamicCommandExceptionType(n -> Text.translatable("slot.unknown", n));
    public static final DynamicCommandExceptionType ERROR_ONLY_SINGLE_SLOT_ALLOWED = new DynamicCommandExceptionType(n -> Text.translatable("slot.only_single_allowed", n));

    public static SlotArgument slot() { return new SlotArgument(); }

    public static int getSlot(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Integer.class); }

    /** ParserUtils.readWhile(reader, c -> c != ' '). */
    static String readName(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String name = readName(reader);
        SlotRanges.Range r = SlotRanges.byName(name);
        if (r == null) throw ERROR_UNKNOWN_SLOT.createWithContext(reader, name);
        if (r.size() != 1) throw ERROR_ONLY_SINGLE_SLOT_ALLOWED.createWithContext(reader, name);
        return r.slots().get(0);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:item_slot"; }
}
