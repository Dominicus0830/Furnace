// 선택자 nbt=·level=·advancements=·predicate= 의 참/거짓·반전·범위·오류 키(바닐라 EntitySelectorOptions).
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.advancement.PlayerAdvancements;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.arguments.EntitySelectorParser;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelectorOptionsTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Cow[] cows;
    DataPackManager packs;
    Player steve;
    CommandSource self;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new org.domi.furnace.server.world.ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        core.world().submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> cows[0].addTag("a")).orTimeout(10, TimeUnit.SECONDS).join();
        steve = new Player(5, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        self = CommandSource.player(core, steve).withPermission(2);
    }

    @AfterEach
    void tearDown() {
        sched.close();
        if (packs != null) packs.close();
    }

    private static EntitySelector parse(String s) throws CommandSyntaxException {
        return new EntitySelectorParser(new StringReader(s), true).parse();
    }

    private static String key(String s) {
        return assertThrows(CommandSyntaxException.class, () -> parse(s)).getRawMessage().translationKey();
    }

    private boolean selfHit(String s) throws CommandSyntaxException { return parse(s).findPlayers(self).size() == 1; }

    private int count(String s) throws CommandSyntaxException {
        return parse(s).findRiders(CommandSource.console(core)).orTimeout(10, TimeUnit.SECONDS).join().size();
    }

    @Test
    void level() throws Exception {
        steve.vitals().setExperienceLevels(5);
        assertTrue(selfHit("@s[level=5]"));
        assertTrue(selfHit("@s[level=3..]"));
        assertTrue(selfHit("@s[level=..5]"));
        assertFalse(selfHit("@s[level=6..]"));
        assertFalse(selfHit("@s[level=0..4]"));
        assertFalse(parse("@e[level=0..]").includesEntities(), "level= 은 플레이어만");
        assertEquals("argument.entity.options.level.negative", key("@a[level=-1]"));
        assertEquals("argument.entity.options.inapplicable", key("@a[level=1,level=2]"));
    }

    @Test
    void nbt() throws Exception {
        assertEquals(1, count("@e[type=cow,nbt={Tags:[\"a\"]}]"));
        assertEquals(1, count("@e[type=cow,nbt=!{Tags:[\"a\"]}]"));
        assertEquals(2, count("@e[type=cow,nbt={}]"));
        assertEquals(0, count("@e[type=cow,nbt=!{}]"));
        steve.addTag("p");
        assertTrue(selfHit("@s[nbt={Tags:[\"p\"]}]"));
        assertFalse(selfHit("@s[nbt=!{Tags:[\"p\"]}]"));
        assertFalse(selfHit("@s[nbt={Tags:[\"q\"]}]"));
        assertEquals("argument.nbt.expected.key", key("@e[nbt={,}]"));
    }

    private void loadPacks() throws Exception {
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack.resolve("data/test/predicate"));
        Files.createDirectories(pack.resolve("data/test/advancement"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        Files.writeString(pack.resolve("data/test/predicate/is_player.json"),
                "{\"condition\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"type\":\"minecraft:player\"}}");
        Files.writeString(pack.resolve("data/test/predicate/is_cow.json"),
                "{\"condition\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"type\":\"minecraft:cow\"}}");
        Files.writeString(pack.resolve("data/test/advancement/two.json"),
                "{\"criteria\":{\"x\":{\"trigger\":\"minecraft:impossible\"},\"y\":{\"trigger\":\"minecraft:impossible\"}}}");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
    }

    @Test
    void predicate() throws Exception {
        loadPacks();
        assertTrue(selfHit("@s[predicate=test:is_player]"));
        assertFalse(selfHit("@s[predicate=!test:is_player]"));
        assertFalse(selfHit("@s[predicate=test:is_cow]"));
        assertTrue(selfHit("@s[predicate=!test:is_cow]"));
        // 없는 predicate 는 반전해도 거짓
        assertFalse(selfHit("@s[predicate=test:missing]"));
        assertFalse(selfHit("@s[predicate=!test:missing]"));
        assertEquals(2, count("@e[type=cow,predicate=test:is_cow]"));
        assertEquals(0, count("@e[type=cow,predicate=!test:is_cow]"));
        assertEquals("argument.id.invalid", key("@e[predicate=a:b:c]"));
    }

    @Test
    void advancements() throws Exception {
        loadPacks();
        var mgr = packs.advancements();
        var adv = mgr.get(ResourceId.parse("test:two"));
        assertNotNull(adv);
        var a = new PlayerAdvancements(tmp, steve.uuid(), mgr, new PlayerAdvancements.Host() {
            public void send(org.domi.furnace.server.net.protocol.Packet p) {}
            public void awardRecipes(List<ResourceId> r) {}
            public void giveExperience(int points) {}
            public void giveLoot(String table) {}
            public void broadcast(Text message) {}
            public boolean announceAdvancements() { return false; }
            public Text displayName() { return Text.text("Steve"); }
            public org.domi.furnace.server.loot.LootContext playerContext() { return null; }
            public org.domi.furnace.server.loot.LootRegistries registries() { return packs.loot().registries(); }
        });
        steve.advancements(a);
        assertTrue(selfHit("@s[advancements={test:two=false}]"));
        assertFalse(selfHit("@s[advancements={test:two=true}]"));
        a.award(adv, "x");
        assertTrue(selfHit("@s[advancements={test:two={x=true,y=false}}]"));
        assertFalse(selfHit("@s[advancements={test:two={x=false}}]"));
        assertFalse(selfHit("@s[advancements={test:two={z=true}}]"), "없는 기준은 거짓");
        assertFalse(selfHit("@s[advancements={test:two={z=false}}]"));
        assertTrue(selfHit("@s[advancements={test:two=false}]"), "기준 하나만 달성");
        a.award(adv, "y");
        assertTrue(selfHit("@s[advancements={test:two=true}]"));
        // 없는 발전과제는 거짓
        assertFalse(selfHit("@s[advancements={test:nope=false}]"));
        assertEquals(0, count("@e[type=cow,advancements={test:two=false}]"));
        assertFalse(parse("@e[advancements={test:two=true}]").includesEntities());
        assertTrue(parse("@e[advancements={}]").includesEntities());
        assertEquals("argument.entity.options.inapplicable", key("@a[advancements={},advancements={}]"));
        assertEquals("parsing.bool.invalid", key("@a[advancements={test:two=maybe}]"));
    }
}
