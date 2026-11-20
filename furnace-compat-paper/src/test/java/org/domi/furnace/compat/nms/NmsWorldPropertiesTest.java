package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** NMS shim 5c: 월드 속성(날씨·시간·규칙)과 플레이어 게임 모드·능력(Axiom 월드 속성·비행 속도·게임 모드 패킷). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsWorldPropertiesTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    CraftWorld world;
    ServerLevel level;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Builder");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        world = (CraftWorld) bukkit.getWorld();
        level = world.getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void weatherTimeAndRules() {
        // 바닐라 날씨 순환 범위와 표본(Axiom: RAIN_DURATION.sample(level.random) 으로 비 시간을 고른다)
        assertEquals(12000, ServerLevel.RAIN_DURATION.getMinValue());
        assertEquals(24000, ServerLevel.RAIN_DURATION.getMaxValue());
        assertEquals(15600, ServerLevel.THUNDER_DURATION.getMaxValue());
        RandomSource r = RandomSource.create(42);
        for (int i = 0; i < 100; i++) {
            int v = ServerLevel.RAIN_DELAY.sample(r);
            assertTrue(v >= 12000 && v <= 180000, String.valueOf(v));
        }
        RandomSource a = RandomSource.create(7), b = RandomSource.create(7);
        assertEquals(b.nextInt(24000 - 12000 + 1) + 12000, ServerLevel.RAIN_DURATION.sample(a), "바닐라 randomBetweenInclusive: 난수 한 번");

        level.setWeatherParameters(0, 6000, true, true);
        assertTrue(world.hasStorm());
        assertTrue(world.isThundering());
        assertEquals(6000, world.getWeatherDuration());
        assertEquals(6000, world.getThunderDuration());
        level.setWeatherParameters(5000, 0, false, false);
        assertFalse(world.hasStorm());
        assertEquals(5000, world.getClearWeatherDuration());

        level.setDayTime(1234);
        assertEquals(1234, world.getFullTime());

        GameRules rules = level.getGameRules();
        assertEquals("doDaylightCycle", GameRules.RULE_DAYLIGHT.getId());
        assertTrue(rules.getBoolean(GameRules.RULE_DAYLIGHT));
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level);   // Axiom SetTime: 시간 흐름 끄기
        assertEquals(Boolean.FALSE, world.getGameRuleValue(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE), "Bukkit 과 같은 규칙 상태");
        assertFalse(level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).get());
        rules.getRule(GameRules.RULE_DAYLIGHT).set(true, level);
        assertEquals(3, rules.getInt(GameRules.RULE_RANDOMTICKING));
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(5, level);
        assertEquals(5, world.getGameRuleValue(org.bukkit.GameRule.RANDOM_TICK_SPEED));
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(3, level);
    }

    @Test
    void gameModeAndAbilities() {
        ServerPlayer me = bukkit.getHandle();
        assertSame(GameType.CREATIVE, GameType.byId(1));
        assertTrue(me.setGameMode(GameType.CREATIVE));
        assertFalse(me.setGameMode(GameType.CREATIVE), "같은 모드면 거짓");
        assertEquals(org.bukkit.GameMode.CREATIVE, bukkit.getGameMode());
        var ab = me.getAbilities();
        assertTrue(ab.mayfly, "크리에이티브는 날 수 있다");
        // Axiom SetFlySpeed: 세터는 곧바로 서버 값
        ab.setFlyingSpeed(0.1F);
        assertEquals(0.1F, org.domi.furnace.compat.nms.NmsChunks.bridge().playerAbilities(tp.player()).flyingSpeed());
        assertSame(ab, me.getAbilities(), "바닐라처럼 플레이어마다 같은 객체");
        assertEquals(0.1F, me.getAbilities().getFlyingSpeed());
        // 바닐라의 흔한 꼴: 필드를 고치고 onUpdateAbilities
        assertTrue(me.setGameMode(GameType.SURVIVAL));
        assertFalse(me.getAbilities().mayfly, "게임 모드가 바뀌면 보기도 따라온다");
        me.getAbilities().mayfly = true;
        assertFalse(bukkit.getAllowFlight(), "onUpdateAbilities 전에는 서버가 옛 값");
        me.onUpdateAbilities();
        assertTrue(bukkit.getAllowFlight());
        me.getAbilities().mayfly = false;
        me.getAbilities().setFlyingSpeed(0.05F);   // 세터가 고친 필드도 함께 쓴다
        assertFalse(bukkit.getAllowFlight());
        var free = new net.minecraft.world.entity.player.Abilities();
        free.setFlyingSpeed(0.2F);
        assertEquals(0.2F, free.getFlyingSpeed(), "플레이어 없는 객체는 값만 든다");
    }

    /** 바닐라 1.21.1 규칙 51개(불리언 40·정수 11)가 모두 키로 있고, 읽은 값이 Bukkit 규칙 값과 같다. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void allFiftyOneRulesRead() throws IllegalAccessException {
        GameRules rules = level.getGameRules();
        int bools = 0, ints = 0;
        for (var f : GameRules.class.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != GameRules.Key.class) continue;
            GameRules.Key k = (GameRules.Key) f.get(null);
            Object bukkitValue = world.getGameRuleValue(org.bukkit.GameRule.getByName(k.getId()));
            Object v = rules.getRule(k);
            if (v instanceof GameRules.BooleanValue b) {
                bools++;
                assertEquals(bukkitValue, b.get(), k.getId());
                assertEquals(b.get(), rules.getBoolean(k), k.getId());
            } else {
                ints++;
                assertEquals(bukkitValue, ((GameRules.IntegerValue) v).get(), k.getId());
                assertEquals(((GameRules.IntegerValue) v).get(), rules.getInt(k), k.getId());
            }
        }
        assertEquals(40, bools);
        assertEquals(11, ints);
    }
}
