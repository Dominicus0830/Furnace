package net.minecraft.commands.arguments.blocks;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

/**
 * 명령 인자 "블록 조건"(Mojang 이름 shim, NMS shim 5번 묶음): {@code stone}·{@code #minecraft:logs}·{@code oak_log[axis=y]} 꼴. 인자 종류 객체를 만들고
 * 예시를 주는 겉모양만 있다. 글을 읽는 {@link #parse}(블록 이름·태그·상태·NBT 문법)는 서버의 같은 인자 처리기가 Brigadier 명령 트리 안에 있고 이 shim 이 그것에 닿는
 * 길이 없어 UnsupportedNmsCall 이다 — 플러그인이 자기 문법으로 읽는 인자(제안만 이 종류로 쓰는 경우)는 된다.
 */
class BlockPredicateArgument {

    private static final Collection<String> EXAMPLES = Arrays.asList("stone", "minecraft:stone", "stone[foo=bar]", "#stone", "#stone[foo=bar]{baz=nbt}");

    private final CommandBuildContext context;

    public BlockPredicateArgument(CommandBuildContext context) { this.context = java.util.Objects.requireNonNull(context, "context"); }

    public static BlockPredicateArgument blockPredicate(CommandBuildContext context) { return new BlockPredicateArgument(context); }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockPredicateArgument#parse", "reading a block predicate is done by the server's own command"
                + " parser, which the NMS shim cannot reach - read the argument as a string and parse it in the plugin");
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    /** 블록 조건 한 개: 칸을 시험한다. */
    public interface Result extends Predicate<BlockInWorld> {
        boolean requiresNbt();
    }
}
