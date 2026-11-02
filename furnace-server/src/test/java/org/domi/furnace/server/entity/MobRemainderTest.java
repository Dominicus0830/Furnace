// 몹 잔여 규칙: Vindicator 'Johnny'·Shulker 방어 목표·킬러 토끼 이름·Creeper 효과 구름/염소/doHurtTarget·Fox 음식 효과.
// 기대값은 바닐라 1.21.1 디컴파일(cks, ckj.c, cfy.a, cjp, cfo/btn.eat)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobRemainderTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void goalTablesHaveJohnnyAndShulkerDefense() {
        var level = new BlockPlacementTest.TestLevel();
        var vt = MobGoalTableTest.table(new Vindicator(level, level.nextEntityId()).targetSelector);
        assertTrue(vt.contains("4 VindicatorJohnnyAttackGoal<LivingEntity>"), vt.toString());
        var st = MobGoalTableTest.table(new Shulker(level, level.nextEntityId()).targetSelector);
        assertTrue(st.contains("3 ShulkerDefenseAttackGoal<LivingEntity>"), st.toString());
    }

    @Test
    void vindicatorNamedJohnnyTurnsAggressiveForever() {
        var level = new BlockPlacementTest.TestLevel();
        var v = new Vindicator(level, level.nextEntityId());
        assertFalse(v.isJohnny());
        v.setCustomName(Text.text("johnny"));
        assertFalse(v.isJohnny(), "정확히 Johnny 여야 한다");
        v.setCustomName(Text.text("Johnny"));
        assertTrue(v.isJohnny());
        v.setCustomName(Text.text("Bob"));
        assertTrue(v.isJohnny(), "이름을 바꿔도 유지");
        var tag = v.saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        assertTrue(tag.getBoolean("Johnny"));
    }

    @Test
    void shulkerDefenseNeedsATeam() {
        var level = new BlockPlacementTest.TestLevel();
        var shulker = new Shulker(level, level.nextEntityId());
        var goal = shulker.targetSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(g -> g.getClass().getSimpleName().equals("ShulkerDefenseAttackGoal")).findFirst().orElseThrow();
        assertNull(shulker.getTeam());
        assertFalse(goal.canUse(), "팀이 없으면 쓰지 않는다");
    }

    @Test
    void killerBunnyGetsItsNameUnlessNamed() {
        var level = new BlockPlacementTest.TestLevel();
        var r = new Rabbit(level, level.nextEntityId());
        r.setVariant(Rabbit.TYPE_EVIL);
        assertTrue(r.hasCustomName());
        assertEquals("entity.minecraft.killer_bunny", ((org.domi.furnace.api.text.Content.Translatable) r.getCustomName().content()).key());
        var named = new Rabbit(level, level.nextEntityId());
        named.setCustomName(Text.text("Bugs"));
        named.setVariant(Rabbit.TYPE_EVIL);
        assertEquals("Bugs", named.getCustomName().getString());
        var plain = new Rabbit(level, level.nextEntityId());
        plain.setVariant(Rabbit.TYPE_WHITE);
        assertFalse(plain.hasCustomName());
    }

    @Test
    void creeperIgnoresGoatsAndDoesNotMelee() {
        var level = new BlockPlacementTest.TestLevel();
        var c = new Creeper(level, level.nextEntityId());
        var goat = new Goat(level, level.nextEntityId());
        c.setTarget(goat);
        assertNull(c.getTarget(), "염소는 대상이 아니다");
        var zombie = new Zombie(level, level.nextEntityId());
        c.setTarget(zombie);
        assertSame(zombie, c.getTarget());
        assertTrue(c.doHurtTarget(zombie));
        assertEquals(zombie.maxHealth(), zombie.health(), 0.0F, "때리지 않는다");
    }

    @Test
    void creeperExplosionLeavesEffectCloud() {
        var level = new BlockPlacementTest.TestLevel();
        var c = new Creeper(level, level.nextEntityId());
        c.moveTo(0.5, -59.0, 0.5, 0, 0);
        level.addFreshEntity(c);
        c.addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 1));
        c.ignite();
        for (int i = 0; i < 40 && !c.isRemoved(); i++) c.tick();
        assertTrue(c.isRemoved(), "30틱 도화선 뒤 폭발");
        var clouds = level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(-5, -65, -5, 5, -55, 5), e -> true);
        assertEquals(1, clouds.size());
        var tag = clouds.get(0).saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        assertEquals(2.5F, tag.getFloat("Radius"), 0.0F);
        assertEquals(-0.5F, tag.getFloat("RadiusOnUse"), 0.0F);
        assertEquals(10, tag.getInt("WaitTime"));
        assertEquals(300, tag.getInt("Duration"), "기본 600 의 절반");
        assertEquals(-2.5F / 300, tag.getFloat("RadiusPerTick"), 0.0F);
    }

    @Test
    void creeperWithoutEffectsLeavesNoCloud() {
        var level = new BlockPlacementTest.TestLevel();
        var c = new Creeper(level, level.nextEntityId());
        c.moveTo(0.5, -59.0, 0.5, 0, 0);
        level.addFreshEntity(c);
        c.ignite();
        for (int i = 0; i < 40 && !c.isRemoved(); i++) c.tick();
        assertTrue(level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(-5, -65, -5, 5, -55, 5), e -> true).isEmpty());
    }

    @Test
    void effectColorWeightsByAmplifierPlusOne() {
        // 속도(증폭 1 → 비중 2) + 힘(증폭 0 → 비중 1): 채널마다 (2a + b) / 3 (정수 나눗셈)
        int a = MobEffect.SPEED.color(), b = MobEffect.STRENGTH.color();
        int r = (2 * (a >> 16 & 255) + (b >> 16 & 255)) / 3, g = (2 * (a >> 8 & 255) + (b >> 8 & 255)) / 3, bl = (2 * (a & 255) + (b & 255)) / 3;
        int got = Creeper.effectsColor(java.util.List.of(new MobEffectInstance(MobEffect.SPEED, 100, 1), new MobEffectInstance(MobEffect.STRENGTH, 100, 0)));
        assertEquals(r << 16 | g << 8 | bl, got);
        assertEquals(-13083194, Creeper.effectsColor(java.util.List.of(new MobEffectInstance(MobEffect.SPEED, 100, 0, false, false))), "보이는 효과가 없으면 기본색");
    }

    @Test
    void foxEatingAppliesFoodEffectsAndConsumesOne() {
        var level = new BlockPlacementTest.TestLevel();
        var fox = new Fox(level, level.nextEntityId());
        fox.moveTo(0.5, -60.0, 0.5, 0, 0);
        level.addFreshEntity(fox);
        ItemStack rest = fox.finishEating(B.stack("minecraft:golden_apple", 2));
        assertEquals(1, rest.count());
        assertTrue(fox.hasEffect(MobEffect.REGENERATION), "확률 1.0 효과는 반드시 걸린다");
        assertTrue(fox.hasEffect(MobEffect.ABSORPTION));
    }
}
