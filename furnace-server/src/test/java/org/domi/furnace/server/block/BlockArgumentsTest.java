package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.domi.furnace.server.command.arguments.BlockPredicateArgument;
import org.domi.furnace.server.command.arguments.BlockStateArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.junit.jupiter.api.Test;

/** 블록 상태·술어 인자(BlockStateParser) — 속성 반영·오류 커서·태그 술어. */
class BlockArgumentsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    private static String parse(String s) throws CommandSyntaxException {
        return B.describe(BlockStateArgument.block().parse(new StringReader(s)).state());
    }

    @Test
    void blockState_parsesIdAndProperties() throws CommandSyntaxException {
        assertEquals("minecraft:stone", parse("stone"));
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]",
                parse("minecraft:oak_stairs[facing=east, waterlogged = true]"));
        assertEquals("minecraft:lava[level=1]", parse("lava[level=1]"));
        StringReader r = new StringReader("stone ~ ~");
        BlockStateArgument.block().parse(r);
        assertEquals(5, r.getCursor(), "뒤 인자는 남긴다");
    }

    @Test
    void blockState_errors() {
        var e = assertThrows(CommandSyntaxException.class, () -> parse("stone[foo=bar]"));
        assertEquals("argument.block.property.unknown", key(e));
        e = assertThrows(CommandSyntaxException.class, () -> parse("lava[level=99]"));
        assertEquals("argument.block.property.invalid", key(e));
        e = assertThrows(CommandSyntaxException.class, () -> parse("nope_block"));
        assertEquals("argument.block.id.invalid", key(e));
        e = assertThrows(CommandSyntaxException.class, () -> parse("#minecraft:logs"));
        assertEquals("argument.block.tag.disallowed", key(e));
        e = assertThrows(CommandSyntaxException.class, () -> parse("lava[level=1"));
        assertEquals("argument.block.property.unclosed", key(e));
    }

    private static String key(CommandSyntaxException e) {
        return ((org.domi.furnace.api.text.Content.Translatable) e.getRawMessage().content()).key();
    }

    @Test
    void blockPredicate_blockAndTag() throws CommandSyntaxException {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(0, -60, 0);
        level.setBlock(pos, B.defaultState("minecraft:oak_log"), 3);
        var logs = BlockPredicateArgument.blockPredicate().parse(new StringReader("#minecraft:logs[axis=y]"));
        var logsX = BlockPredicateArgument.blockPredicate().parse(new StringReader("#logs[axis=x]"));
        var oak = BlockPredicateArgument.blockPredicate().parse(new StringReader("oak_log"));
        var stone = BlockPredicateArgument.blockPredicate().parse(new StringReader("stone"));
        assertTrue(logs.test(level, pos));
        assertFalse(logsX.test(level, pos));
        assertTrue(oak.test(level, pos));
        assertFalse(stone.test(level, pos));
    }
}
