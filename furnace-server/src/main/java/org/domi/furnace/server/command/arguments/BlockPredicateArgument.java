// 바닐라 BlockPredicateArgument(minecraft:block_predicate): 블록 또는 #태그, 지정한 속성 값, 블록 엔티티 NBT 로 칸을 고른다.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.net.io.nbt.NbtTags;

public final class BlockPredicateArgument implements ArgumentType<BlockPredicateArgument.Predicate> {

    private static final List<String> EXAMPLES = List.of("stone", "minecraft:stone", "stone[foo=bar]", "#stone", "#stone[foo=bar]{baz=nbt}");

    /** 칸 하나를 검사한다(region 스레드에서). */
    public interface Predicate {
        boolean test(BlockLevel level, Pos pos);
    }

    public static BlockPredicateArgument blockPredicate() { return new BlockPredicateArgument(); }

    public static Predicate getBlockPredicate(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Predicate.class); }

    @Override
    public Predicate parse(StringReader reader) throws CommandSyntaxException {
        BlockStateArgument.Parsed p = BlockStateArgument.parse(reader, true, true);
        return p.tag() != null ? tagPredicate(p.tag(), p.properties(), p.nbt()) : blockPredicate(p.state(), p.properties(), p.nbt());
    }

    /** BlockPredicate: 같은 블록이고 지정한 속성 값이 같고 NBT 가 맞는가. */
    private static Predicate blockPredicate(int expected, Map<String, String> props, Object nbt) {
        int block = B.block(expected);
        return (level, pos) -> {
            int s = level.getBlockState(pos);
            if (B.block(s) != block) return false;
            for (String name : props.keySet()) {
                Prop prop = BlockStateArgument.propOf(name, s);
                if (prop.get(s) != prop.get(expected)) return false;
            }
            return nbtMatches(level, pos, nbt);
        };
    }

    /** TagPredicate: 태그에 들고, 지정한 속성이 그 블록에 있고 값이 같으며 NBT 가 맞는가. */
    private static Predicate tagPredicate(String tag, Map<String, String> props, Object nbt) {
        return (level, pos) -> {
            int s = level.getBlockState(pos);
            if (!B.isTag(s, tag)) return false;
            for (Map.Entry<String, String> e : props.entrySet()) {
                Prop prop = BlockStateArgument.propOf(e.getKey(), s);
                if (prop == null) return false;
                int v = prop.indexOf(s, e.getValue());
                if (v < 0 || prop.get(s) != v) return false;
            }
            return nbtMatches(level, pos, nbt);
        };
    }

    private static boolean nbtMatches(BlockLevel level, Pos pos, Object nbt) {
        if (nbt == null) return true;
        // 바닐라: 블록 엔티티가 있어야 하고, saveWithFullMetadata(id·x·y·z 포함)와 부분 일치(NbtUtils.compareNbt(nbt, tag, true)).
        return level.getBlockEntity(pos) instanceof BlockEntity be && NbtTags.compareNbt(nbt, be.saveWithFullMetadata(), true);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:block_predicate"; }
}
