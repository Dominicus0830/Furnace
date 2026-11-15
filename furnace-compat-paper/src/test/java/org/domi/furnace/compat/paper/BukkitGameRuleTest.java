package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.server.world.GameRules;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/**
 * T-141 E4a: GameRule 상수·World 게임 규칙 읽기/쓰기·WorldGameRuleChangeEvent·keepSpawnInMemory.
 * 기대값은 Paper 1.21.1 CraftWorld(getGameRuleValue 모르는 이름 "", setGameRuleValue null·모르는 이름 false, isGameRule 빈 이름 IAE,
 * setGameRule 범위 검사 없음, 정수 deserialize 실패 0)와 Add-WorldGameRuleChangeEvent 패치(/gamerule 취소해도 성공 알림은 지금 값).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitGameRuleTest {

    FurnaceTestServer server;
    World world;
    GameRules rules;

    @BeforeAll
    void start() throws Exception {
        Path dir = Files.createTempDirectory("gamerule");
        Path jar = Jars.bukkit(dir.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        world = Compat.world(server.overworld());
        rules = server.internals().gameRules();
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void reset() {
        RecorderPlugin.reset();
        for (var r : GameRules.RULES.values()) rules.set(r.name(), r.defaultValue());
    }

    private List<WorldGameRuleChangeEvent> events() {
        return RecorderPlugin.SEEN.stream().filter(e -> e instanceof WorldGameRuleChangeEvent).map(e -> (WorldGameRuleChangeEvent) e).toList();
    }

    @Test
    void constantsAreTheVanillaTable() {
        assertEquals(51, GameRule.values().length);
        assertEquals(GameRules.RULES.keySet(), Arrays.stream(GameRule.values()).map(GameRule::getName).collect(Collectors.toSet()));
        for (GameRule<?> r : GameRule.values())
            assertEquals(GameRules.RULES.get(r.getName()).isBoolean(), r.getType() == Boolean.class, r.getName());
        assertSame(GameRule.DO_FIRE_TICK, GameRule.getByName("doFireTick"));
        assertNull(GameRule.getByName("dofiretick"), "대소문자 구분");
        assertThrows(NullPointerException.class, () -> GameRule.getByName(null));
        assertEquals("gamerule.spawnChunkRadius", GameRule.SPAWN_CHUNK_RADIUS.translationKey());
        assertEquals("GameRule{key=keepInventory, type=class java.lang.Boolean}", GameRule.KEEP_INVENTORY.toString());
    }

    @Test
    void readsLikePaper() {
        rules.set("randomTickSpeed", 7);
        assertEquals("7", world.getGameRuleValue("randomTickSpeed"));
        assertEquals(7, world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED));
        assertEquals("true", world.getGameRuleValue("doFireTick"));
        assertEquals(Boolean.FALSE, world.getGameRuleValue(GameRule.KEEP_INVENTORY));
        assertNull(world.getGameRuleValue((String) null));
        assertEquals("", world.getGameRuleValue("noSuchRule"), "Paper: 모르는 이름은 빈 문자열");
        assertEquals(3, world.getGameRuleDefault(GameRule.RANDOM_TICK_SPEED));
        assertEquals(2, world.getGameRuleDefault(GameRule.SPAWN_CHUNK_RADIUS));
        assertEquals(Boolean.TRUE, world.getGameRuleDefault(GameRule.DO_DAYLIGHT_CYCLE));
        assertEquals(GameRules.RULES.keySet(), Set.of(world.getGameRules()));
        assertTrue(world.isGameRule("mobGriefing"));
        assertFalse(world.isGameRule("furnace:custom"));
        assertThrows(IllegalArgumentException.class, () -> world.isGameRule(""));
        assertThrows(IllegalArgumentException.class, () -> world.isGameRule(null));
        assertThrows(IllegalArgumentException.class, () -> world.getGameRuleValue((GameRule<?>) null));
    }

    @Test
    void setGameRuleFiresEventThenWrites() {
        assertTrue(world.setGameRule(GameRule.KEEP_INVENTORY, true));
        assertTrue(rules.getBoolean("keepInventory"));
        var e = events().get(0);
        assertNull(e.getCommandSender(), "API 로 바꾸면 보낸 쪽 null");
        assertSame(GameRule.KEEP_INVENTORY, e.getGameRule());
        assertEquals("true", e.getValue());
        assertSame(world, e.getWorld());

        // 처리기가 바꾼 값이 쓰인다 — 범위 검사 없음(spawnChunkRadius 명령 범위는 0..32)
        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> ((WorldGameRuleChangeEvent) ev).setValue("40"));
        assertTrue(world.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 5));
        assertEquals(40, rules.getInt("spawnChunkRadius"));

        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> ((WorldGameRuleChangeEvent) ev).setCancelled(true));
        assertFalse(world.setGameRule(GameRule.KEEP_INVENTORY, false));
        assertTrue(rules.getBoolean("keepInventory"), "취소하면 그대로");
        assertThrows(IllegalArgumentException.class, () -> world.setGameRule(GameRule.KEEP_INVENTORY, null));
        assertThrows(IllegalArgumentException.class, () -> world.setGameRule(null, true));
    }

    @Test
    void setGameRuleValueParsesLikeVanilla() {
        assertFalse(world.setGameRuleValue(null, "1"));
        assertFalse(world.setGameRuleValue("randomTickSpeed", null));
        assertFalse(world.setGameRuleValue("noSuchRule", "1"));
        assertThrows(IllegalArgumentException.class, () -> world.setGameRuleValue("", "1"));
        assertTrue(events().isEmpty());

        assertTrue(world.setGameRuleValue("randomTickSpeed", "12"));
        assertEquals(12, rules.getInt("randomTickSpeed"));
        assertTrue(world.setGameRuleValue("randomTickSpeed", "abc"), "바닐라 deserialize: 숫자 아님은 0");
        assertEquals(0, rules.getInt("randomTickSpeed"));
        assertTrue(world.setGameRuleValue("doFireTick", "yes"), "Boolean.parseBoolean");
        assertFalse(rules.getBoolean("doFireTick"));
        assertTrue(world.setGameRuleValue("doFireTick", "TRUE"));
        assertTrue(rules.getBoolean("doFireTick"));
        assertSame(GameRule.DO_FIRE_TICK, events().get(events().size() - 1).getGameRule());
    }

    @Test
    void keepSpawnInMemoryIsSpawnChunkRadius() {
        assertTrue(world.getKeepSpawnInMemory());
        world.setKeepSpawnInMemory(false);
        assertEquals(0, rules.getInt("spawnChunkRadius"));
        assertFalse(world.getKeepSpawnInMemory());
        rules.set("spawnChunkRadius", 7);
        world.setKeepSpawnInMemory(true);
        assertEquals(2, rules.getInt("spawnChunkRadius"), "켜면 기본값으로");
        assertTrue(world.getKeepSpawnInMemory());
    }

    @Test
    void gameruleCommandFiresEventWithSender() {
        String[] before = new String[1];
        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> {
            before[0] = ((WorldGameRuleChangeEvent) ev).getValue();
            ((WorldGameRuleChangeEvent) ev).setValue("9");
        });
        var out = server.execute("gamerule randomTickSpeed 5");
        assertTrue(out.success());
        assertEquals(9, rules.getInt("randomTickSpeed"));
        assertEquals(9, out.result());
        assertTrue(out.text().contains("randomTickSpeed") && out.text().contains("9"), out.text());
        var e = events().get(0);
        assertInstanceOf(ConsoleCommandSender.class, e.getCommandSender());
        assertEquals("5", before[0], "처리기 전 값은 명령 인자");
        assertSame(GameRule.RANDOM_TICK_SPEED, e.getGameRule());

        // 취소: 값은 그대로, 명령은 성공이고 지금 값을 알린다
        RecorderPlugin.reset();
        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> ((WorldGameRuleChangeEvent) ev).setCancelled(true));
        out = server.execute("gamerule randomTickSpeed 20");
        assertTrue(out.success());
        assertEquals(9, rules.getInt("randomTickSpeed"));
        assertTrue(out.text().contains("9") && !out.text().contains("20"), out.text());

        // 불리언 규칙: 처리기가 준 문자열을 Boolean.parseBoolean
        RecorderPlugin.reset();
        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> ((WorldGameRuleChangeEvent) ev).setValue("nope"));
        assertTrue(server.execute("gamerule doFireTick true").success());
        assertFalse(rules.getBoolean("doFireTick"));

        // 정수 규칙에 숫자 아닌 값: Paper 는 Integer.parseInt 예외로 명령이 실패한다(값은 그대로)
        RecorderPlugin.reset();
        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> ((WorldGameRuleChangeEvent) ev).setValue("many"));
        out = server.execute("gamerule randomTickSpeed 4");
        assertFalse(out.success(), out.text());
        assertEquals(9, rules.getInt("randomTickSpeed"));
    }

    @Test
    void nullValueIsNotCancel() {
        // PR #383 검토 R1: Paper 는 setValue(null) 을 취소로 보지 않는다 — 불리언은 Boolean.parseBoolean(null)=false, 정수는 parseInt 예외로 명령 실패
        rules.set("doFireTick", 1);
        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> ((WorldGameRuleChangeEvent) ev).setValue(null));
        var out = server.execute("gamerule doFireTick true");
        assertTrue(out.success(), out.text());
        assertFalse(rules.getBoolean("doFireTick"));

        rules.set("randomTickSpeed", 3);
        out = server.execute("gamerule randomTickSpeed 5");
        assertFalse(out.success(), out.text());
        assertEquals(3, rules.getInt("randomTickSpeed"));
    }

    @Test
    void cancelledGameruleStillRunsOnChanged() {
        // Paper 패치: 취소돼도 onChanged 는 돈다 — doImmediateRespawn 이면 지금 값(false)으로 게임 이벤트 11 을 다시 보낸다(PR #383 검토 S1)
        var tp = server.join("Ivo");
        server.tick(2);
        tp.clearReceived();
        RecorderPlugin.ACTIONS.put("WorldGameRuleChangeEvent", ev -> ((WorldGameRuleChangeEvent) ev).setCancelled(true));
        assertTrue(server.execute("gamerule doImmediateRespawn true").success());
        assertFalse(rules.getBoolean("doImmediateRespawn"));
        server.tick(1);
        assertNotNull(tp.awaitPacket(org.domi.furnace.api.packet.PacketTypes.Clientbound.GAME_EVENT, g -> Integer.valueOf(11).equals(g.get("event"))));
    }
}
