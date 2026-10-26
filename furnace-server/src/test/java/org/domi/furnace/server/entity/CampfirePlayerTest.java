package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.types.CampfireBlock;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;

/** 켜진 모닥불 안에 선 플레이어: campfire 피해(모닥불 1, 영혼 모닥불 2), 꺼졌으면 없음, 화염 저항이면 무효. */
class CampfirePlayerTest {

    @org.junit.jupiter.api.BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private final Player player = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
    private final PlayerVitals v = player.vitals();

    CampfirePlayerTest() {
        v.env(new PlayerVitalsTest.TestEnv());
        v.loadGameTypes(null, GameMode.SURVIVAL, false);
        var dry = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
        for (int i = 0; i < 61; i++) v.tick(dry);   // 접속 직후 무적(60틱)이 지나게
    }

    private static CampfireBlock behavior(int state) { return (CampfireBlock) B.behavior(state); }

    @Test
    void litCampfireHurtsPlayer() {
        int s = Blocks.get().defaultState("minecraft:campfire");
        behavior(s).entityInsidePlayer(s, v);
        assertEquals(19.0f, v.health());
    }

    @Test
    void soulCampfireHurtsTwo() {
        int s = Blocks.get().defaultState("minecraft:soul_campfire");
        behavior(s).entityInsidePlayer(s, v);
        assertEquals(18.0f, v.health());
    }

    @Test
    void unlitCampfireNoDamage() {
        int s = B.prop("lit").with(Blocks.get().defaultState("minecraft:campfire"), false);
        behavior(s).entityInsidePlayer(s, v);
        assertEquals(20.0f, v.health());
    }

    @Test
    void fireResistanceBlocksIt() {
        v.addEffect(new MobEffectInstance(MobEffect.FIRE_RESISTANCE, 200, 0));
        int s = Blocks.get().defaultState("minecraft:campfire");
        behavior(s).entityInsidePlayer(s, v);
        assertEquals(20.0f, v.health());
    }
}
