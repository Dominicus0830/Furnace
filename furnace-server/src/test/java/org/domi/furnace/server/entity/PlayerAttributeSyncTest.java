// 플레이어 속성 맵 동기: 입장 때 syncable 전부, 이후 바뀐 속성만. 효과·달리기·장비 수정자와 최대 체력 연결.
// 기대값은 디컴파일(LivingEntity.setSprinting/MobEffect/AttributeInstance)에서 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.UpdateAttributes;
import org.junit.jupiter.api.Test;

class PlayerAttributeSyncTest {

    private static Player player(PlayerVitalsTest.TestEnv env) {
        var p = new Player(5, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        return p;
    }

    private static List<UpdateAttributes> sent(PlayerVitalsTest.TestEnv env) {
        List<UpdateAttributes> out = new ArrayList<>();
        for (Packet p : env.trackerPackets) if (p instanceof UpdateAttributes u) out.add(u);
        return out;
    }

    private static UpdateAttributes last(PlayerVitalsTest.TestEnv env) {
        var l = sent(env);
        return l.get(l.size() - 1);
    }

    private static org.domi.furnace.server.attribute.AttributeInstance find(UpdateAttributes u, Attribute a) {
        for (var i : u.attributes()) if (i.attribute() == a) return i;
        return null;
    }

    @Test
    void joinSendsEverySyncableAttributeWithPlayerBaseValues() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        var u = last(env);
        for (Attribute a : new Attribute[] {Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED, Attribute.MOVEMENT_EFFICIENCY,
                Attribute.WATER_MOVEMENT_EFFICIENCY, Attribute.OXYGEN_BONUS, Attribute.SNEAKING_SPEED, Attribute.SUBMERGED_MINING_SPEED}) {
            assertNotNull(find(u, a), a.name());
        }
        assertEquals(20.0, find(u, Attribute.MAX_HEALTH).baseValue());
        // Player.createAttributes: movement_speed 0.1F(레지스트리 기본 0.7 이 아니다)
        assertEquals((double) 0.1F, find(u, Attribute.MOVEMENT_SPEED).baseValue());
        assertEquals(4.0, find(u, Attribute.ATTACK_SPEED).baseValue());
        assertEquals(0.3, find(u, Attribute.SNEAKING_SPEED).baseValue());
        assertEquals(0.2, find(u, Attribute.SUBMERGED_MINING_SPEED).baseValue());
        // 서버 전용 속성(attack_damage, attack_knockback, knockback_resistance 등)은 보내지 않는다
        assertNull(find(u, Attribute.ATTACK_DAMAGE));
        assertNull(find(u, Attribute.KNOCKBACK_RESISTANCE));
        assertNull(find(u, Attribute.ATTACK_KNOCKBACK));
        assertNotNull(p.pairingAttributes(), "새로 보게 된 사람에게 줄 스냅샷");
        assertEquals(u.attributes().size(), p.pairingAttributes().attributes().size());
    }

    @Test
    void speedEffectAddsModifierSendsOnlyMovementSpeedAndRemovalRestores() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        int n = sent(env).size();
        p.vitals().addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 1));   // 신속 II
        p.syncAttributes();
        assertEquals(n + 1, sent(env).size());
        var u = last(env);
        assertEquals(1, u.attributes().size(), "바뀐 속성만");
        var ms = find(u, Attribute.MOVEMENT_SPEED);
        // AttributeTemplate: 0.2F × (증폭 1 + 1) = 0.2F(double) × 2, add_multiplied_total → 0.1F × (1 + 0.4000000059604645)
        double amount = (double) 0.2F * 2;
        assertEquals(amount, ms.getModifier("minecraft:effect.speed").amount());
        assertEquals(AttributeModifier.ADD_MULTIPLIED_TOTAL, ms.getModifier("minecraft:effect.speed").operation());
        assertEquals((double) 0.1F * (1.0 + amount), ms.value());
        assertEquals(ms.value(), PlayerAttributes.value(p, Attribute.MOVEMENT_SPEED), "계산 함수와 맵이 같은 값");

        p.syncAttributes();
        assertEquals(n + 1, sent(env).size(), "안 바뀌면 다시 보내지 않는다");

        p.vitals().removeEffect(MobEffect.SPEED);
        p.syncAttributes();
        assertEquals(n + 2, sent(env).size());
        assertTrue(find(last(env), Attribute.MOVEMENT_SPEED).modifiers().isEmpty());
        assertEquals((double) 0.1F, find(last(env), Attribute.MOVEMENT_SPEED).value());
    }

    @Test
    void sprintingAddsThirtyPercentModifierWithExactWireBytes() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        p.sprinting(true);
        p.syncAttributes();
        var u = last(env);
        var ms = find(u, Attribute.MOVEMENT_SPEED);
        assertEquals((double) 0.1F * (1.0 + (double) 0.3F), ms.value());
        assertEquals((double) 0.1F * (1.0 + (double) 0.3F), PlayerAttributes.value(p, Attribute.MOVEMENT_SPEED));

        // 선로 바이트: entity 5, 1개, 속성 21(movement_speed), 기본값 0.1F, 수정자 1개, "minecraft:sprinting", 0.3F, 연산 2
        ByteBuf buf = Unpooled.buffer();
        u.write(buf);
        byte[] id = "minecraft:sprinting".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(5, buf.readUnsignedByte());
        assertEquals(1, buf.readUnsignedByte());
        assertEquals(21, buf.readUnsignedByte());
        assertEquals(0x3FB99999A0000000L, buf.readLong());
        assertEquals(1, buf.readUnsignedByte());
        assertEquals(id.length, buf.readUnsignedByte());
        byte[] got = new byte[id.length];
        buf.readBytes(got);
        assertArrayEquals(id, got);
        assertEquals(0x3FD3333340000000L, buf.readLong());
        assertEquals(2, buf.readByte());
        assertEquals(0, buf.readableBytes());

        p.sprinting(false);
        p.syncAttributes();
        assertTrue(find(last(env), Attribute.MOVEMENT_SPEED).modifiers().isEmpty());
    }

    @Test
    void sentPacketIsASnapshotNotALiveView() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        var u = last(env);
        p.attributes().get(Attribute.MAX_HEALTH).setBaseValue(30.0);
        assertEquals(20.0, find(u, Attribute.MAX_HEALTH).baseValue(), "이미 만든 패킷은 이후 변경에 영향받지 않는다");
    }

    @Test
    void healthBoostRaisesMaxHealthImmediatelyAndAttributeBaseFeedsVitals() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        assertEquals(20.0F, p.vitals().maxHealth());
        p.vitals().addEffect(new MobEffectInstance(MobEffect.HEALTH_BOOST, 600, 1));   // 체력 강화 II: +4 × 2
        assertEquals(28.0F, p.vitals().maxHealth(), "효과를 걸자마자 최대 체력이 오른다");
        p.syncAttributes();
        assertEquals(28.0, p.attributes().value(Attribute.MAX_HEALTH));
        assertEquals(8.0, find(last(env), Attribute.MAX_HEALTH).getModifier("minecraft:effect.health_boost").amount());

        // /attribute base set: 기본값이 40 이면 40 + 8
        p.attributes().get(Attribute.MAX_HEALTH).setBaseValue(40.0);
        p.syncAttributes();
        assertEquals(48.0F, p.vitals().maxHealth());
        assertEquals(40.0, last(env).attributes().size() == 0 ? 0 : find(last(env), Attribute.MAX_HEALTH).baseValue());
    }

    @Test
    void loweringMaxHealthClampsCurrentHealth() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        assertEquals(20.0F, p.vitals().health());
        p.attributes().get(Attribute.MAX_HEALTH).setBaseValue(6.0);
        p.syncAttributes();
        assertEquals(6.0F, p.vitals().maxHealth());
        assertEquals(6.0F, p.vitals().health(), "LivingEntity.onAttributeUpdated: 체력이 최대 체력을 넘으면 자른다");
    }

    @Test
    void manuallyAddedModifierSurvivesReconcile() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        p.attributes().get(Attribute.LUCK).addOrReplaceModifier(new AttributeModifier("test:manual", 2.0, AttributeModifier.ADD_VALUE));
        p.vitals().addEffect(new MobEffectInstance(MobEffect.LUCK, 600, 0));
        p.syncAttributes();
        assertTrue(p.attributes().get(Attribute.LUCK).hasModifier("test:manual"));
        assertTrue(p.attributes().get(Attribute.LUCK).hasModifier("minecraft:effect.luck"));
        assertEquals(3.0, p.attributes().value(Attribute.LUCK));
        p.vitals().removeEffect(MobEffect.LUCK);
        p.syncAttributes();
        assertTrue(p.attributes().get(Attribute.LUCK).hasModifier("test:manual"));
        assertFalse(p.attributes().get(Attribute.LUCK).hasModifier("minecraft:effect.luck"));
    }

    @Test
    void respawnResendsAllSyncable() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncAttributes();
        int n = sent(env).size();
        p.markEntityDataResend();
        p.syncAttributes();
        assertEquals(n + 1, sent(env).size());
        assertEquals(p.attributes().syncable().size(), last(env).attributes().size());
    }

    /** PlayerAttributes.value(Player, …)(전리품 luck 등이 쓴다)가 /attribute 로 바꾼 기본값·수정자를 합친다(바닐라 AttributeInstance.getValue). */
    @Test
    void playerValueIncludesAttributeCommandBaseAndModifiers() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        assertEquals(0.0, PlayerAttributes.value(p, Attribute.LUCK));
        p.attributes().get(Attribute.LUCK).setBaseValue(2.0);   // /attribute @s minecraft:generic.luck base set 2
        assertEquals(2.0, PlayerAttributes.value(p, Attribute.LUCK));
        p.attributes().get(Attribute.LUCK).addOrReplaceModifier(new AttributeModifier("test:manual", 1.0, AttributeModifier.ADD_VALUE));
        assertEquals(3.0, PlayerAttributes.value(p, Attribute.LUCK), "동기 전에도 바로");
        p.vitals().addEffect(new MobEffectInstance(MobEffect.LUCK, 600, 0));   // +1
        assertEquals(4.0, PlayerAttributes.value(p, Attribute.LUCK));
        p.syncAttributes();   // 효과 수정자가 맵에 들어가도 두 번 세지 않는다
        assertEquals(4.0, PlayerAttributes.value(p, Attribute.LUCK));
        assertEquals(p.attributes().value(Attribute.LUCK), PlayerAttributes.value(p, Attribute.LUCK));
        p.vitals().removeEffect(MobEffect.LUCK);   // 동기 전: 맵에는 낡은 효과 수정자가 남았지만 세지 않는다
        assertEquals(3.0, PlayerAttributes.value(p, Attribute.LUCK));
        // 플레이어 기본 이동 속도는 여전히 0.1F
        assertEquals((double) 0.1F, PlayerAttributes.value(p, Attribute.MOVEMENT_SPEED));
    }
}
