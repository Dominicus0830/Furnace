// NBT 경로 파싱·연산, SNBT 명령 파서 오류 키, /data storage 각 모드, storage 파일 왕복, 엔티티 접근자(플레이어 쓰기 금지).
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.arguments.NbtPathArgument;
import org.domi.furnace.server.command.arguments.TagParser;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtTags;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.CommandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataCommandsTest {

    @TempDir Path tmp;

    private static NbtCompound nbt(String s) throws CommandSyntaxException { return (NbtCompound) NbtTags.thaw(TagParser.parseTag(s)); }

    private static String key(CommandSyntaxException e) { return e.getRawMessage().translationKey(); }

    private static String w(Object tag) { return Snbt.write(NbtTags.freeze(NbtTags.copy(tag))); }

    // ------------------------------------------------------------------ 파서

    @Test
    void tagParserUsesVanillaErrorKeys() {
        assertEquals("argument.nbt.list.mixed", key(assertThrows(CommandSyntaxException.class, () -> TagParser.parseTag("{a:[1,2b]}"))));
        assertEquals("argument.nbt.array.mixed", key(assertThrows(CommandSyntaxException.class, () -> TagParser.parseTag("{a:[B;1,2]}"))));
        assertEquals("argument.nbt.array.invalid", key(assertThrows(CommandSyntaxException.class, () -> TagParser.parseTag("{a:[X;1]}"))));
        assertEquals("argument.nbt.expected.value", key(assertThrows(CommandSyntaxException.class, () -> TagParser.parseTag("{a:}"))));
        assertEquals("argument.nbt.trailing", key(assertThrows(CommandSyntaxException.class, () -> TagParser.parseTag("{} x"))));
        assertDoesNotThrow(() -> TagParser.parseTag("{a:[B;1b,2b],b:[I;1,2],c:[L;3L],d:1.5f,e:true}"));
    }

    @Test
    void pathParsing() throws Exception {
        var arg = NbtPathArgument.nbtPath();
        StringReader r = new StringReader("a.b[0].\"c d\"[{x:1b}].e{y:2}[] rest");
        assertEquals("a.b[0].\"c d\"[{x:1b}].e{y:2}[]", arg.parse(r).asString());
        assertEquals(" rest", r.getRemaining());
        assertEquals("{a:1}.b", NbtPathArgument.of("{a:1}.b").asString());
        assertEquals("arguments.nbtpath.node.invalid", key(assertThrows(CommandSyntaxException.class, () -> NbtPathArgument.of("a.{b:1}"))));
        assertEquals("arguments.nbtpath.node.invalid", key(assertThrows(CommandSyntaxException.class, () -> NbtPathArgument.of("a..b"))));
    }

    @Test
    void pathOperations() throws Exception {
        NbtCompound root = nbt("{list:[{id:1},{id:2},{id:1,x:5}],arr:[I;1,2,3],s:\"hi\",n:{m:3b}}");
        assertEquals(2, NbtPathArgument.of("list[{id:1}]").countMatching(root));
        assertEquals(3, NbtPathArgument.of("list[]").countMatching(root));
        assertEquals("3", w(NbtPathArgument.of("arr[-1]").get(root).get(0)));
        assertEquals("3b", w(NbtPathArgument.of("n.m").get(root).get(0)));
        var e = assertThrows(CommandSyntaxException.class, () -> NbtPathArgument.of("n.q.z").get(root));
        assertEquals("arguments.nbtpath.nothing_found", key(e));
        assertEquals("n.q", ((org.domi.furnace.api.text.Content.Translatable) e.getRawMessage().content()).args().get(0).toString());

        // set: 없는 부모는 만든다
        assertEquals(1, NbtPathArgument.of("a.b.c").set(root, 7));
        assertEquals("7", w(NbtPathArgument.of("a.b.c").get(root).get(0)));
        assertEquals(0, NbtPathArgument.of("a.b.c").set(root, 7), "같은 값이면 바뀐 것이 없다");
        // 필터 set 은 맞는 원소 모두
        assertEquals(2, NbtPathArgument.of("list[{id:1}].x").set(root, 9));
        // insert(배열): 정수 배열 끝에 끼운다
        assertEquals(1, NbtPathArgument.of("arr").insert(-1, root, List.of((Object) 4)));
        assertEquals("[I;1,2,3,4]", w(NbtPathArgument.of("arr").get(root).get(0)));
        // insert 대상이 리스트가 아니면 expected_list, 잘못된 위치는 invalid_index
        assertEquals("commands.data.modify.expected_list",
                key(assertThrows(CommandSyntaxException.class, () -> NbtPathArgument.of("s").insert(0, root, List.of((Object) 1)))));
        assertEquals("commands.data.modify.invalid_index",
                key(assertThrows(CommandSyntaxException.class, () -> NbtPathArgument.of("arr").insert(99, root, List.of((Object) 1)))));
        // 리스트 종류가 다르면 끼워지지 않는다(0 개 바뀜)
        assertEquals(0, NbtPathArgument.of("list").insert(0, root, List.of((Object) "x")));
        // remove
        assertEquals(2, NbtPathArgument.of("list[{id:1}]").remove(root));
        assertEquals(1, NbtPathArgument.of("list").countMatching(root));
        assertEquals(1, NbtPathArgument.of("list[]").remove(root));
        // 빈 리스트는 종류가 풀려 다른 종류를 받는다
        assertEquals(1, NbtPathArgument.of("list").insert(0, root, List.of((Object) "x")));
        assertEquals("[\"x\"]", w(NbtPathArgument.of("list").get(root).get(0)));
    }

    // ------------------------------------------------------------------ /data storage

    @Test
    void dataStorageCommands() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            List<String> out = new ArrayList<>();
            core.addConsoleListener(t -> out.add(t.getString()));
            var src = CommandSource.console(core);
            java.util.function.Function<String, String> run = cmd -> {
                out.clear();
                core.commands().performPrefixedCommand(src, cmd);
                return String.join("\n", out);
            };
            assertTrue(run.apply("data merge storage test:a {x:1,y:{z:\"s\"}}").contains("Modified storage test:a"));
            assertTrue(run.apply("data merge storage test:a {x:1}").contains("Nothing changed"));
            assertTrue(run.apply("data get storage test:a y.z").contains("Storage test:a has the following contents: \"s\""));
            assertTrue(run.apply("data get storage test:a x 2.5").contains("2.50"));
            run.apply("data modify storage test:a list set value [1,2]");
            run.apply("data modify storage test:a list append value 3");
            run.apply("data modify storage test:a list prepend from storage test:a x");
            run.apply("data modify storage test:a list insert 1 value 9");
            run.apply("data modify storage test:a str set string storage test:a y.z");
            run.apply("data modify storage test:a sub set string storage test:a list[0] 0 1");
            run.apply("data modify storage test:a y merge value {w:2b}");
            NbtCompound c = core.world().commandStorage().get("test:a");
            assertEquals("{list:[1,9,1,2,3],str:\"s\",sub:\"1\",x:1,y:{w:2b,z:\"s\"}}", Snbt.write(c));
            assertTrue(run.apply("data modify storage test:a y merge value 1").contains("Expected object"));
            assertTrue(run.apply("data modify storage test:a q set string storage test:a y").contains("Expected value"));
            assertTrue(run.apply("data modify storage test:a q set string storage test:a str 3").contains("Invalid substring"));
            assertTrue(run.apply("data get storage test:a list[]").contains("single NBT value"));
            assertTrue(run.apply("data remove storage test:a list[0]").contains("Modified"));
            assertTrue(run.apply("data remove storage test:a nope").contains("Nothing changed"));
            assertTrue(run.apply("data get storage test:a nope").contains("Found no elements matching nope"));

            // 저장 왕복: data/command_storage_test.dat
            core.world().commandStorage().saveIfDirty();
            Path f = tmp.resolve("data").resolve("command_storage_test.dat");
            if (Files.exists(f)) {
                NbtCompound root;
                try (var in = Files.newInputStream(f)) { root = NbtIo.readGzip(in); }
                assertTrue(root.getCompound("data").getCompound("contents").contains("a"));
            }
        } finally {
            sched.close();
        }
    }

    @Test
    void storageFileRoundTrip() throws Exception {
        Path data = tmp.resolve("world").resolve("data");
        CommandStorage s = new CommandStorage(data);
        s.set("foo:bar", TagParser.parseTag("{a:[I;1,2],b:\"c\"}"));
        s.set("foo:empty", new NbtCompound());
        s.saveIfDirty();
        Path f = data.resolve("command_storage_foo.dat");
        assertTrue(Files.exists(f));
        NbtCompound root;
        try (var in = Files.newInputStream(f)) { root = NbtIo.readGzip(in); }
        assertEquals(3955, root.getInt("DataVersion"));
        assertEquals("{bar:{a:[I;1,2],b:\"c\"}}", Snbt.write(root.getCompound("data").getCompound("contents")));
        CommandStorage again = new CommandStorage(data);
        assertEquals("{a:[I;1,2],b:\"c\"}", Snbt.write(again.get("foo:bar")));
        assertEquals("{}", Snbt.write(again.get("foo:none")));
        // 빈 compound 는 지운다
        again.set("foo:bar", new NbtCompound());
        assertTrue(again.keys().isEmpty());
    }

    // ------------------------------------------------------------------ 엔티티 접근자

    @Test
    void entityAccessorKeepsUuidAndRejectsPlayers() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        var cow = new Cow(level, level.nextEntityId());
        UUID id = cow.uuid();
        var a = new DataCommands.EntityAccessor(cow);
        NbtCompound data = a.getData();
        data.putIntArray("UUID", new int[] {1, 2, 3, 4});
        data.putBoolean("NoGravity", true);
        a.setData(data);
        assertEquals(id, cow.uuid(), "UUID 는 되돌린다");
        assertTrue(cow.isNoGravity());

        var p = new Player(5, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        var pa = new DataCommands.EntityAccessor(p);
        assertEquals("commands.data.entity.invalid", key(assertThrows(CommandSyntaxException.class, () -> pa.setData(new NbtCompound()))));
    }

    // ------------------------------------------------------------------ 동시성·새 파서 경로

    @Test
    void concurrentStorageAppendsKeepEveryUpdate() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var src = CommandSource.console(core).withSilent();
            int threads = 8, each = 50;
            var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            var start = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                fs.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < each; i++) core.commands().performPrefixedCommand(src, "data modify storage t:c l append value 1");
                    return null;
                }));
            }
            start.countDown();
            for (var f : fs) f.get();
            pool.shutdown();
            assertEquals(threads * each, core.world().commandStorage().get("t:c").getList("l").size());
        } finally {
            sched.close();
        }
    }

    private static void installItems() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        org.domi.furnace.server.item.ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt"));
    }

    @Test
    void giveComponentsUseVanillaTagParser() throws Exception {
        installItems();
        var arg = org.domi.furnace.server.command.arguments.ItemArgument.item();
        var e = assertThrows(CommandSyntaxException.class, () -> arg.parse(new StringReader("stone[custom_data={a:[I;1b]}]")));
        assertEquals("argument.nbt.array.mixed", key(e));
        var stack = arg.parse(new StringReader("stone[custom_data={a:true}]")).createItemStack(1, false);
        Object data = org.domi.furnace.server.command.arguments.ItemArgument.componentValue(stack, "minecraft:custom_data");
        assertEquals("{a:1b}", Snbt.write(data));
    }

    @Test
    void setblockNbtUsesVanillaTagParser() throws Exception {
        var arg = org.domi.furnace.server.command.arguments.BlockStateArgument.block();
        var e = assertThrows(CommandSyntaxException.class, () -> arg.parse(new StringReader("chest{a:[I;1b]}")));
        assertEquals("argument.nbt.array.mixed", key(e));
        assertEquals("{a:1b}", Snbt.write(arg.parse(new StringReader("chest{a:true}")).nbt()));
    }

    @Test
    void summonNbtUsesVanillaTagParser() throws Exception {
        var arg = org.domi.furnace.server.command.arguments.CompoundTagArgument.compoundTag();
        var e = assertThrows(CommandSyntaxException.class, () -> arg.parse(new StringReader("{a:[I;1b]}")));
        assertEquals("argument.nbt.array.mixed", key(e));
        assertEquals("{a:1b}", Snbt.write(arg.parse(new StringReader("{a:true}"))));
    }

    @Test
    void deepNestingAtCommandLengthLimitDoesNotOverflow() {
        // 채팅 명령 최대 길이(32767) 안에서 가장 깊은 중첩: '[' 만 늘어놓은 입력
        String deep = "[".repeat(32000);
        assertThrows(CommandSyntaxException.class, () -> new org.domi.furnace.server.command.arguments.TagParser(new StringReader(deep)).readValue());
    }
}
