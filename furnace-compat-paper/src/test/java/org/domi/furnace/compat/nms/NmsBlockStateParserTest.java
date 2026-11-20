package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * NMS shim 5e-3c: 블록 상태 글 읽기({@link BlockStateParser}), {@link ResourceLocation#read}, {@link TagParser#readStruct}.
 * 기대값은 Paper 1.21.1 jar 를 직접 돌린 결과다(부트스트랩한 Paper 서버 클래스로 같은 입력을 읽음, 태그 조회는 "logs" 만 있는 조회기 — 여기서는 Furnace 의 진짜
 * 블록 태그를 써서 "logs" 는 있고 "nope" 은 없다). 모드 0 = parseForTesting(NBT 없음), 1 = parseForBlock(NBT 있음), 2 = parseForTesting(NBT 있음).
 */
class NmsBlockStateParserTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() {
        server = FurnaceTestServer.builder().start();
        Bukkit.setServer(CraftServer.INSTANCE);
        // 서버가 Brigadier 묶음을 붙이면 부르는 자리 — 바닐라처럼 기본 오류를 게임 문구로 바꾼다(Paper 결과는 그 상태에서 뽑았다)
        new org.domi.furnace.compat.paper.CompatFormat().sharedLibrariesReady(java.util.Set.of("brigadier"));
    }

    @AfterAll
    static void stop() { server.close(); }

    private static final String[] CASES = {
        "stone", "minecraft:oak_log[axis=x]", "oak_log[ axis = z ]rest", "#minecraft:logs[axis=y,foo=bar]", "#minecraft:logs",
        "nope", "oak_log[foo=x]", "oak_log[axis=q]", "oak_log[axis=x,axis=y]", "oak_log[axis]", "oak_log[axis=x", "oak_log[axis=x;", "#nope",
        "#minecraft:logs[axis=y", "#minecraft:logs[axis=y,axis=z]", "#minecraft:logs[axis]", "Stone", "chest{Lock:\"a\"}x", "chest{Lock:}",
        "oak_log[axis=\"x\"]", "oak_log[axis=\"x]", "oak_log[\"ax\\is\"=x]", "", "minecraft:", "oak_stairs[waterlogged=true,facing=east]", "#minecraft:logs{a:1b}"};

    /** Paper 1.21.1 결과(위 CASES × 모드 0·1·2 차례). */
    private static final String[] PAPER = {
            "0|stone|B minecraft:stone {} null|cursor=5",
            "1|stone|B minecraft:stone {} null|cursor=5",
            "2|stone|B minecraft:stone {} null|cursor=5",
            "0|minecraft:oak_log[axis=x]|B minecraft:oak_log[axis=x] {axis=x} null|cursor=25",
            "1|minecraft:oak_log[axis=x]|B minecraft:oak_log[axis=x] {axis=x} null|cursor=25",
            "2|minecraft:oak_log[axis=x]|B minecraft:oak_log[axis=x] {axis=x} null|cursor=25",
            "0|oak_log[ axis = z ]rest|B minecraft:oak_log[axis=z] {axis=z} null|cursor=19",
            "1|oak_log[ axis = z ]rest|B minecraft:oak_log[axis=z] {axis=z} null|cursor=19",
            "2|oak_log[ axis = z ]rest|B minecraft:oak_log[axis=z] {axis=z} null|cursor=19",
            "0|#minecraft:logs[axis=y,foo=bar]|T minecraft:logs {axis=y, foo=bar} null|cursor=31",
            "1|#minecraft:logs[axis=y,foo=bar]|E Tags aren't allowed here, only actual blocks at position 0: <--[HERE]|cursor=0",
            "2|#minecraft:logs[axis=y,foo=bar]|T minecraft:logs {axis=y, foo=bar} null|cursor=31",
            "0|#minecraft:logs|T minecraft:logs {} null|cursor=15",
            "1|#minecraft:logs|E Tags aren't allowed here, only actual blocks at position 0: <--[HERE]|cursor=0",
            "2|#minecraft:logs|T minecraft:logs {} null|cursor=15",
            "0|nope|E Unknown block type 'minecraft:nope' at position 0: <--[HERE]|cursor=0",
            "1|nope|E Unknown block type 'minecraft:nope' at position 0: <--[HERE]|cursor=0",
            "2|nope|E Unknown block type 'minecraft:nope' at position 0: <--[HERE]|cursor=0",
            "0|oak_log[foo=x]|E Block minecraft:oak_log does not have property 'foo' at position 8: oak_log[<--[HERE]|cursor=0",
            "1|oak_log[foo=x]|E Block minecraft:oak_log does not have property 'foo' at position 8: oak_log[<--[HERE]|cursor=0",
            "2|oak_log[foo=x]|E Block minecraft:oak_log does not have property 'foo' at position 8: oak_log[<--[HERE]|cursor=0",
            "0|oak_log[axis=q]|E Block minecraft:oak_log does not accept 'q' for axis property at position 13: ..._log[axis=<--[HERE]|cursor=0",
            "1|oak_log[axis=q]|E Block minecraft:oak_log does not accept 'q' for axis property at position 13: ..._log[axis=<--[HERE]|cursor=0",
            "2|oak_log[axis=q]|E Block minecraft:oak_log does not accept 'q' for axis property at position 13: ..._log[axis=<--[HERE]|cursor=0",
            "0|oak_log[axis=x,axis=y]|E Property 'axis' can only be set once for block minecraft:oak_log at position 15: ...og[axis=x,<--[HERE]|cursor=0",
            "1|oak_log[axis=x,axis=y]|E Property 'axis' can only be set once for block minecraft:oak_log at position 15: ...og[axis=x,<--[HERE]|cursor=0",
            "2|oak_log[axis=x,axis=y]|E Property 'axis' can only be set once for block minecraft:oak_log at position 15: ...og[axis=x,<--[HERE]|cursor=0",
            "0|oak_log[axis]|E Expected value for property 'minecraft:oak_log' on block axis at position 12: ...k_log[axis<--[HERE]|cursor=0",
            "1|oak_log[axis]|E Expected value for property 'minecraft:oak_log' on block axis at position 12: ...k_log[axis<--[HERE]|cursor=0",
            "2|oak_log[axis]|E Expected value for property 'minecraft:oak_log' on block axis at position 12: ...k_log[axis<--[HERE]|cursor=0",
            "0|oak_log[axis=x|E Expected closing ] for block state properties at position 14: ...log[axis=x<--[HERE]|cursor=0",
            "1|oak_log[axis=x|E Expected closing ] for block state properties at position 14: ...log[axis=x<--[HERE]|cursor=0",
            "2|oak_log[axis=x|E Expected closing ] for block state properties at position 14: ...log[axis=x<--[HERE]|cursor=0",
            "0|oak_log[axis=x;|E Expected closing ] for block state properties at position 14: ...log[axis=x<--[HERE]|cursor=0",
            "1|oak_log[axis=x;|E Expected closing ] for block state properties at position 14: ...log[axis=x<--[HERE]|cursor=0",
            "2|oak_log[axis=x;|E Expected closing ] for block state properties at position 14: ...log[axis=x<--[HERE]|cursor=0",
            "0|#nope|E Unknown block tag 'minecraft:nope' at position 0: <--[HERE]|cursor=0",
            "1|#nope|E Tags aren't allowed here, only actual blocks at position 0: <--[HERE]|cursor=0",
            "2|#nope|E Unknown block tag 'minecraft:nope' at position 0: <--[HERE]|cursor=0",
            "0|#minecraft:logs[axis=y|E Expected closing ] for block state properties at position 21: ...logs[axis=<--[HERE]|cursor=0",
            "1|#minecraft:logs[axis=y|E Tags aren't allowed here, only actual blocks at position 0: <--[HERE]|cursor=0",
            "2|#minecraft:logs[axis=y|E Expected closing ] for block state properties at position 21: ...logs[axis=<--[HERE]|cursor=0",
            "0|#minecraft:logs[axis=y,axis=z]|E Property 'axis' can only be set once for block minecraft: at position 23: ...gs[axis=y,<--[HERE]|cursor=0",
            "1|#minecraft:logs[axis=y,axis=z]|E Tags aren't allowed here, only actual blocks at position 0: <--[HERE]|cursor=0",
            "2|#minecraft:logs[axis=y,axis=z]|E Property 'axis' can only be set once for block minecraft: at position 23: ...gs[axis=y,<--[HERE]|cursor=0",
            "0|#minecraft:logs[axis]|E Expected value for property 'minecraft:' on block axis at position 16: ...raft:logs[<--[HERE]|cursor=0",
            "1|#minecraft:logs[axis]|E Tags aren't allowed here, only actual blocks at position 0: <--[HERE]|cursor=0",
            "2|#minecraft:logs[axis]|E Expected value for property 'minecraft:' on block axis at position 16: ...raft:logs[<--[HERE]|cursor=0",
            "0|Stone|E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "1|Stone|E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "2|Stone|E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "0|chest{Lock:\"a\"}x|B minecraft:chest[facing=north,type=single,waterlogged=false] {} null|cursor=5",
            "1|chest{Lock:\"a\"}x|B minecraft:chest[facing=north,type=single,waterlogged=false] {} {Lock:\"a\"}|cursor=15",
            "2|chest{Lock:\"a\"}x|B minecraft:chest[facing=north,type=single,waterlogged=false] {} {Lock:\"a\"}|cursor=15",
            "0|chest{Lock:}|B minecraft:chest[facing=north,type=single,waterlogged=false] {} null|cursor=5",
            "1|chest{Lock:}|E Expected value at position 11: ...hest{Lock:<--[HERE]|cursor=0",
            "2|chest{Lock:}|E Expected value at position 11: ...hest{Lock:<--[HERE]|cursor=0",
            "0|oak_log[axis=\"x\"]|B minecraft:oak_log[axis=x] {axis=x} null|cursor=17",
            "1|oak_log[axis=\"x\"]|B minecraft:oak_log[axis=x] {axis=x} null|cursor=17",
            "2|oak_log[axis=\"x\"]|B minecraft:oak_log[axis=x] {axis=x} null|cursor=17",
            "0|oak_log[axis=\"x]|E Unclosed quoted string at position 16: ...g[axis=\"x]<--[HERE]|cursor=0",
            "1|oak_log[axis=\"x]|E Unclosed quoted string at position 16: ...g[axis=\"x]<--[HERE]|cursor=0",
            "2|oak_log[axis=\"x]|E Unclosed quoted string at position 16: ...g[axis=\"x]<--[HERE]|cursor=0",
            "0|oak_log[\"ax\\is\"=x]|E Invalid escape sequence '\\i' in quoted string at position 12: ...k_log[\"ax\\<--[HERE]|cursor=0",
            "1|oak_log[\"ax\\is\"=x]|E Invalid escape sequence '\\i' in quoted string at position 12: ...k_log[\"ax\\<--[HERE]|cursor=0",
            "2|oak_log[\"ax\\is\"=x]|E Invalid escape sequence '\\i' in quoted string at position 12: ...k_log[\"ax\\<--[HERE]|cursor=0",
            "0||E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "1||E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "2||E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "0|minecraft:|E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "1|minecraft:|E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "2|minecraft:|E Unknown block type 'minecraft:' at position 0: <--[HERE]|cursor=0",
            "0|oak_stairs[waterlogged=true,facing=east]|B minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=true] {waterlogged=true, facing=east} null|cursor=40",
            "1|oak_stairs[waterlogged=true,facing=east]|B minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=true] {waterlogged=true, facing=east} null|cursor=40",
            "2|oak_stairs[waterlogged=true,facing=east]|B minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=true] {waterlogged=true, facing=east} null|cursor=40",
            "0|#minecraft:logs{a:1b}|T minecraft:logs {} null|cursor=15",
            "1|#minecraft:logs{a:1b}|E Tags aren't allowed here, only actual blocks at position 0: <--[HERE]|cursor=0",
            "2|#minecraft:logs{a:1b}|T minecraft:logs {} {a:1b}|cursor=21",
    };

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String props(Map<Property<?>, Comparable<?>> m) {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        for (var e : m.entrySet()) j.add(e.getKey().getName() + "=" + ((Property) e.getKey()).getName((Comparable) e.getValue()));
        return j.toString();
    }

    private static String block(BlockStateParser.BlockResult b) {
        return "B " + BlockStateParser.serialize(b.blockState()) + " " + props(b.properties()) + " " + b.nbt();
    }

    private static String tag(BlockStateParser.TagResult t) {
        return "T " + t.tag().unwrapKey().map(k -> k.location().toString()).orElse("?") + " " + new TreeMap<>(t.vagueProperties()) + " " + t.nbt();
    }

    @Test
    void parsesAndFailsLikePaper() {
        HolderLookup<Block> lookup = BuiltInRegistries.BLOCK.asLookup();
        List<String> got = new ArrayList<>();
        for (String c : CASES) {
            for (int mode = 0; mode < 3; mode++) {
                StringReader r = new StringReader(c);
                String out;
                try {
                    out = switch (mode) {
                        case 0 -> BlockStateParser.parseForTesting(lookup, r, false).map(NmsBlockStateParserTest::block, NmsBlockStateParserTest::tag);
                        case 1 -> block(BlockStateParser.parseForBlock(lookup, r, true));
                        default -> BlockStateParser.parseForTesting(lookup, r, true).map(NmsBlockStateParserTest::block, NmsBlockStateParserTest::tag);
                    };
                } catch (CommandSyntaxException e) {
                    out = "E " + e.getMessage();
                }
                got.add(mode + "|" + c + "|" + out + "|cursor=" + r.getCursor());
            }
        }
        assertEquals(String.join("\n", PAPER), String.join("\n", got));
    }

    @Test
    void brigadierBuiltInErrorsAreTheVanillaOnes() {
        assertTrue(CommandSyntaxException.BUILT_IN_EXCEPTIONS instanceof net.minecraft.commands.BrigadierExceptions);
        assertTrue(!CommandSyntaxException.ENABLE_COMMAND_STACK_TRACES, "바닐라 SharedConstants 처럼 스택 추적 끔");
        var b = CommandSyntaxException.BUILT_IN_EXCEPTIONS;
        assertEquals("Integer must not be less than 5, found 3", b.integerTooLow().create(3, 5).getMessage());
        assertEquals("Expected 'x'", b.readerExpectedSymbol().create("x").getMessage());
        assertEquals("Invalid boolean, expected 'true' or 'false' but found 'yes'", b.readerInvalidBool().create("yes").getMessage());
        assertEquals("Could not parse command: boom", b.dispatcherParseException().create("boom").getMessage());
        assertEquals("Unknown or incomplete command, see below for error", b.dispatcherUnknownCommand().create().getMessage());
    }

    @Test
    void serializeLikePaper() {
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", BlockStateParser.serialize(Blocks.OAK_STAIRS.defaultBlockState()));
        assertEquals("minecraft:stone", BlockStateParser.serialize(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void resultsCarryTheParsedObjects() throws Exception {
        HolderLookup<Block> lookup = BuiltInRegistries.BLOCK.asLookup();
        var b = BlockStateParser.parseForTesting(lookup, "oak_log[axis=x]", false).left().orElseThrow();
        assertSame(Blocks.OAK_LOG, b.blockState().getBlock());
        var t = BlockStateParser.parseForTesting(lookup, "#minecraft:logs", false).right().orElseThrow();
        assertTrue(Blocks.OAK_LOG.defaultBlockState().is(t.tag()), "참나무 통나무는 logs 태그");
        assertTrue(!Blocks.STONE.defaultBlockState().is(t.tag()));
        // 오류는 바닐라처럼 Brigadier 예외 종류를 그대로 쓴다
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> BlockStateParser.parseForBlock(lookup, "nope", false));
        assertSame(BlockStateParser.ERROR_UNKNOWN_BLOCK, e.getType());
        assertEquals("Unknown block type 'minecraft:nope'", e.getRawMessage().getString());
    }

    @Test
    void resourceLocationReadAndTagParserLikePaper() throws Exception {
        StringReader r = new StringReader("Minecraft:x");
        assertEquals("minecraft:", ResourceLocation.read(r).toString(), "대문자 앞에서 멈춘다");
        assertEquals(0, r.getCursor());
        StringReader bad = new StringReader("a:b:c rest");
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> ResourceLocation.read(bad));
        assertEquals("Invalid ID at position 0: <--[HERE]", e.getMessage());
        StringReader ok = new StringReader("foo:bar/baz rest");
        assertEquals("foo:bar/baz", ResourceLocation.read(ok).toString());
        assertEquals(11, ok.getCursor());
        // TagParser(StringReader).readStruct: 입력, Paper 결과("묶음|남은 글" 또는 "E 문구|커서")
        String[][] snbt = {
            {"  {a:1b, b:[I;1,2]}tail", "{a:1b,b:[I;1,2]}|tail"},
            {"{a:1b,}", "{a:1b}|"},
            {"x{a:1b}", "E Expected '{' at position 0: <--[HERE]|0"},
            {"{a:[1,2b]}", "E Can't insert TAG_Byte into list of TAG_Int at position 6: {a:[1,<--[HERE]|6"},
            {"{a:[I;1,2L]}", "E Can't insert TAG_Long into TAG_Int_Array at position 8: {a:[I;1,<--[HERE]|8"},
            {"{a:[B;1b,2]}", "E Can't insert TAG_Int into TAG_Byte_Array at position 9: {a:[B;1b,<--[HERE]|9"},
            {"{a:[L;1L,2b]}", "E Can't insert TAG_Byte into TAG_Long_Array at position 9: {a:[L;1L,<--[HERE]|9"},
            {"{a:[1,[2]]}", "E Can't insert TAG_List into list of TAG_Int at position 6: {a:[1,<--[HERE]|6"},
            {"{a:[{},1]}", "E Can't insert TAG_Int into list of TAG_Compound at position 7: {a:[{},<--[HERE]|7"},
            {"{a:[\"x\",1b]}", "E Can't insert TAG_Byte into list of TAG_String at position 8: {a:[\"x\",<--[HERE]|8"},
            {"{a:[[I;1],[B;1b]]}", "E Can't insert TAG_Byte_Array into list of TAG_Int_Array at position 10: {a:[[I;1],<--[HERE]|10"},
            {"{a:[Q;1]}", "E Invalid array type 'Q' at position 4: {a:[<--[HERE]|4"},
            {"{a:1b,", "E Expected key at position 6: {a:1b,<--[HERE]|6"},
            {"{a:[", "E Expected value at position 4: {a:[<--[HERE]|4"},
            {"{a:[1,", "E Expected value at position 6: {a:[1,<--[HERE]|6"},
            {"{a:[I;", "E Expected value at position 6: {a:[I;<--[HERE]|6"},
            {"{a:[I;1,", "E Expected value at position 8: {a:[I;1,<--[HERE]|8"},
            {"{a:[i;1]}", "E Invalid array type 'i' at position 4: {a:[<--[HERE]|4"},
            {"{a:['x';1]}", "E Expected ']' at position 7: {a:['x'<--[HERE]|7"},
            {"{a:[B;1b,2b] , b:[L;3L]}", "{a:[B;1B,2B],b:[L;3L]}|"},
            {"{a:{b:[]}}", "{a:{b:[]}}|"},
            {"{a:1b", "E Expected '}' at position 5: {a:1b<--[HERE]|5"},
            {"{:1}", "E Expected key at position 1: {<--[HERE]|1"},
            {"{a 1}", "E Expected ':' at position 3: {a <--[HERE]|3"},
            {"{a:[1 2]}", "E Expected ']' at position 6: {a:[1 <--[HERE]|6"},
        };
        for (String[] t : snbt) {
            StringReader in = new StringReader(t[0]);
            String out;
            try {
                CompoundTag c = new TagParser(in).readStruct();
                out = c + "|" + in.getRemaining();
            } catch (CommandSyntaxException x) {
                out = "E " + x.getMessage() + "|" + x.getCursor();
            }
            assertEquals(t[1], out, t[0]);
        }
    }
}
