package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.item.ItemStack;

/**
 * 명령 인자 "아이템 조건"(Mojang 이름 shim, NMS shim 5번 묶음): {@code stick}·{@code #planks}·{@code *}·{@code diamond_sword[damage=0]} 꼴. 겉모양만 있다 —
 * {@link BlockPredicateArgument} 와 같은 까닭으로 글을 읽는 {@link #parse} 는 UnsupportedNmsCall 이다.
 */
class ItemPredicateArgument {

    private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo=bar}");

    private final CommandBuildContext context;

    public ItemPredicateArgument(CommandBuildContext context) { this.context = java.util.Objects.requireNonNull(context, "context"); }

    public static ItemPredicateArgument itemPredicate(CommandBuildContext context) { return new ItemPredicateArgument(context); }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("ItemPredicateArgument#parse", "reading an item predicate is done by the server's own command"
                + " parser, which the NMS shim cannot reach - read the argument as a string and parse it in the plugin");
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    /** 아이템 조건 한 개. */
    public interface Result extends Predicate<ItemStack> {}
}
