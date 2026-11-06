// 엔티티 Tags NBT 저장·불러오기 왕복, 선택자 tag= 필터, /tag 명령(플레이어 @s 경로)의 오류 키와 결과.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.arguments.EntitySelectorParser;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TagCommandTest {

    @TempDir Path tmp;

    @Test
    void entityTagsRoundTripThroughNbt() {
        var level = new BlockPlacementTest.TestLevel();
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        assertTrue(cow.addTag("alpha"));
        assertTrue(cow.addTag("beta"));
        assertFalse(cow.addTag("alpha"), "이미 있는 태그");
        NbtCompound saved = cow.save();
        NbtList list = saved.getList("Tags");
        assertEquals(2, list.size());
        assertEquals(Set.of("alpha", "beta"), Set.of((String) list.get(0), (String) list.get(1)));
        var loaded = Entities.load(level, saved);
        assertNotNull(loaded);
        assertEquals(Set.of("alpha", "beta"), loaded.getTags());
        assertTrue(loaded.removeTag("alpha"));
        assertFalse(loaded.removeTag("alpha"));
        assertEquals(Set.of("beta"), loaded.getTags());
        // 태그가 없으면 Tags 키를 쓰지 않는다
        var plain = new Cow(level, level.nextEntityId());
        assertFalse(plain.save().contains("Tags"));
    }

    @Test
    void tagLimitIs1024() {
        var level = new BlockPlacementTest.TestLevel();
        var cow = new Cow(level, level.nextEntityId());
        for (int i = 0; i < 1024; i++) assertTrue(cow.addTag("t" + i));
        assertFalse(cow.addTag("more"));
    }

    @Test
    void playerTagsRoundTrip() {
        var p = new Player(5, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        assertTrue(p.addTag("x"));
        var out = new NbtCompound();
        p.saveTags(out);
        assertEquals(1, out.getList("Tags").size());
        var q = new Player(6, UUID.randomUUID(), "Alex", new ConcurrentEmbeddedChannel());
        q.loadTags(out);
        assertEquals(Set.of("x"), q.getTags());
        var empty = new NbtCompound();
        new Player(7, UUID.randomUUID(), "Bob", new ConcurrentEmbeddedChannel()).saveTags(empty);
        assertFalse(empty.contains("Tags"));
    }

    @Test
    void selectorTagFilter() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        var cow = new Cow(level, level.nextEntityId());
        var tagged = new Cow(level, level.nextEntityId());
        tagged.addTag("foo");
        var p = new Player(5, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.addTag("bar");
        EntitySelector withFoo = parse("@e[tag=foo]"), notFoo = parse("@e[tag=!foo]"), none = parse("@e[tag=]"), anyTag = parse("@e[tag=!]");
        assertFalse(withFoo.matchesFilters(cow));
        assertTrue(withFoo.matchesFilters(tagged));
        assertFalse(withFoo.matchesFilters(p));
        assertTrue(notFoo.matchesFilters(cow));
        assertFalse(notFoo.matchesFilters(tagged));
        assertTrue(notFoo.matchesFilters(p));
        assertTrue(none.matchesFilters(cow), "tag= 는 태그가 하나도 없는 것");
        assertFalse(none.matchesFilters(tagged));
        assertFalse(anyTag.matchesFilters(cow), "tag=! 는 태그가 하나라도 있는 것");
        assertTrue(anyTag.matchesFilters(tagged));
        assertTrue(anyTag.matchesFilters(p));
        // 여러 tag 는 모두 만족해야 한다
        p.addTag("foo");
        EntitySelector both = parse("@e[tag=foo,tag=bar]");
        assertTrue(both.matchesFilters(p));
        assertFalse(both.matchesFilters(tagged));
    }

    @Test
    void commandOnSelf() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var p = new Player(5, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
            CommandSource src = CommandSource.player(core, p).withPermission(2);
            var d = core.commands().getDispatcher();
            assertEquals(1, d.execute("tag @s add foo", src));
            assertEquals(Set.of("foo"), p.getTags());
            assertEquals("commands.tag.add.failed", key(assertThrows(CommandSyntaxException.class, () -> d.execute("tag @s add foo", src))));
            assertEquals(1, d.execute("tag @s list", src));
            assertEquals(1, d.execute("tag @s remove foo", src));
            assertEquals("commands.tag.remove.failed", key(assertThrows(CommandSyntaxException.class, () -> d.execute("tag @s remove foo", src))));
            assertEquals(0, d.execute("tag @s list", src));
        } finally {
            sched.close();
        }
    }

    private static EntitySelector parse(String s) throws CommandSyntaxException {
        return new EntitySelectorParser(new StringReader(s), true).parse();
    }

    static String key(CommandSyntaxException e) { return e.getRawMessage().translationKey(); }
}
