package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.particle.Particle;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 입자 shim: 내장 레지스트리 번호 순서가 서버 목록과 같고, 바닐라 상수 값·생성 규칙이 같으며, Furnace 입자와 성분 그대로 오간다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsParticlesTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void registryFollowsTheServerOrder() {
        var want = Furnace.server().gameRegistries().builtIn("minecraft:particle_type");
        assertEquals(109, want.size());
        assertEquals(want.size(), BuiltInRegistries.PARTICLE_TYPE.size());
        for (int i = 0; i < want.size(); i++) {
            assertEquals(want.get(i), BuiltInRegistries.PARTICLE_TYPE.getKey(BuiltInRegistries.PARTICLE_TYPE.byId(i)).toString(), "번호 " + i);
        }
        assertSame(ParticleTypes.DUST, BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse("minecraft:dust")));
        assertTrue(ParticleTypes.ELDER_GUARDIAN.getOverrideLimiter());
        assertTrue(!ParticleTypes.FLAME.getOverrideLimiter());
        assertSame(ParticleTypes.FLAME, ParticleTypes.FLAME.getType());
    }

    @Test
    void vanillaConstantsAndRules() {
        assertEquals(new org.joml.Vector3f(1, 0, 0), DustParticleOptions.REDSTONE_PARTICLE_COLOR);
        assertEquals(1.0f, DustParticleOptions.REDSTONE.getScale());
        // 3790560 = 0x39D6E0
        assertEquals(new org.joml.Vector3f((float) (0x39 / 255.0), (float) (0xD6 / 255.0), (float) (0xE0 / 255.0)), DustColorTransitionOptions.SCULK_PARTICLE_COLOR);
        assertEquals(4.0f, new DustParticleOptions(new org.joml.Vector3f(), 9f).getScale(), "크기는 0.01~4 로 죈다");
        assertEquals(0.01f, new DustParticleOptions(new org.joml.Vector3f(), 0f).getScale());
        var c = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.5f, 1f, 0f);
        assertEquals(1.0f, c.getAlpha());
        assertEquals(127 / 255f, c.getRed(), "floor(0.5 * 255) = 127");
        assertEquals(0xFF7FFF00, c.furnaceColor());
        assertThrows(IllegalArgumentException.class, () -> new ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.ItemStack.EMPTY));
    }

    @Test
    void convertsBothWays() {
        String at = "test";
        assertSame(ParticleTypes.FLAME, NmsParticles.fromApi(Particle.of("minecraft:flame"), at));
        assertEquals(Particle.of("minecraft:flame"), NmsParticles.toApi(ParticleTypes.FLAME, at));
        var dust = (DustParticleOptions) NmsParticles.fromApi(Particle.dust(0xFF8000, 2f), at);
        assertEquals(2f, dust.getScale());
        assertEquals(Particle.dust(0xFF8000, 2f), NmsParticles.toApi(dust, at));
        assertThrows(IllegalArgumentException.class, () -> NmsParticles.fromApi(new Particle.Simple("minecraft:nope"), at));
        assertThrows(UnsupportedNmsCall.class, () -> NmsParticles.fromApi(Particle.Vibration.toEntity(5, 1f, 10), at));
    }
}
