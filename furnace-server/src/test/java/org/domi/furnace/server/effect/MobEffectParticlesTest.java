// 효과 입자(바닐라 MobEffect.createParticleOptions·LivingEntity.updateSynchronizedMobEffectParticles)와 엔티티 데이터 List<ParticleOptions>(18) 직렬화.
package org.domi.furnace.server.effect;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.junit.jupiter.api.Test;

class MobEffectParticlesTest {

    @Test
    void colouredParticleUsesEffectColour_translucentWhenAmbient() {
        assertEquals(new ParticleOptions.Color(0xFF000000 | 3402751), MobEffect.SPEED.particle(false));
        assertEquals(new ParticleOptions.Color(38 << 24 | 3402751), MobEffect.SPEED.particle(true));
    }

    @Test
    void sixEffectsUseTheirOwnParticle() {
        assertEquals(new ParticleOptions.Simple("minecraft:trial_omen"), MobEffect.TRIAL_OMEN.particle(false));
        assertEquals(new ParticleOptions.Simple("minecraft:raid_omen"), MobEffect.RAID_OMEN.particle(true));
        assertEquals(new ParticleOptions.Simple("minecraft:small_gust"), MobEffect.WIND_CHARGED.particle(false));
        assertEquals(new ParticleOptions.Simple("minecraft:item_cobweb"), MobEffect.WEAVING.particle(false));
        assertEquals(new ParticleOptions.Simple("minecraft:item_slime"), MobEffect.OOZING.particle(false));
        assertEquals(new ParticleOptions.Simple("minecraft:infested"), MobEffect.INFESTED.particle(false));
    }

    @Test
    void onlyVisibleEffectsGiveParticles_ambientNeedsAll() {
        var shown = new MobEffectInstance(MobEffect.POISON, 100, 0, true, true);
        var hidden = new MobEffectInstance(MobEffect.SPEED, 100, 0, false, false);
        assertEquals(List.of(MobEffect.POISON.particle(true)), MobEffectInstance.visibleParticles(List.of(shown, hidden)));
        assertFalse(MobEffectInstance.allAmbient(List.of(shown, hidden)));
        assertTrue(MobEffectInstance.allAmbient(List.of(shown)));
        assertTrue(MobEffectInstance.allAmbient(List.of()), "비어 있으면 true(바닐라 areAllEffectsAmbient)");
        assertEquals(List.of(), MobEffectInstance.visibleParticles(List.of()));
    }

    @Test
    void particleListSerializesAsCountThenEachParticle() {
        var list = List.<ParticleOptions>of(new ParticleOptions.Color(0xFF123456), new ParticleOptions.Simple("minecraft:small_gust"));
        ByteBuf buf = Unpooled.buffer();
        new SetEntityData(5, List.of(new SetEntityData.Item(10, SetEntityData.SERIALIZER_PARTICLES, list))).write(buf);
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(10, buf.readUnsignedByte());
        assertEquals(18, Types.readVarInt(buf), "EntityDataSerializers.PARTICLES");
        assertEquals(2, Types.readVarInt(buf));
        assertEquals(ParticleOptions.Registry.id("minecraft:entity_effect"), Types.readVarInt(buf));
        assertEquals(0xFF123456, buf.readInt());
        assertEquals(ParticleOptions.Registry.id("minecraft:small_gust"), Types.readVarInt(buf));
        assertEquals(0xFF, buf.readUnsignedByte(), "끝 표시");
        assertFalse(buf.isReadable());
        buf.release();
    }
}
