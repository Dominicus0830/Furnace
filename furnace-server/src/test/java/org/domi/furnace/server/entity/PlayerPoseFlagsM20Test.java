// M20: 탑승 중 비행 킥·낙하 피해·배 회전, 시선 보정(getVisibilityPercent), 플레이어 불(공유 플래그 0)·투명(5)·발광(6), 불 보호/태우는 시간.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerPoseFlagsM20Test {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Player player(int id, GameMode mode) {
        var p = new Player(id, UUID.randomUUID(), "m20-" + id, new ConcurrentEmbeddedChannel());
        p.teleporter(t -> {});
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        return p;
    }

    /** 접속 직후 무적 틱(SPAWN_INVULNERABLE_TICKS)이 지나게 한다. */
    private static Player seasoned(int id, GameMode mode) {
        Player p = player(id, mode);
        for (int i = 0; i < 400; i++) p.vitals().tick(PlayerVitals.Surroundings.NONE);
        p.vitals().setFoodLevel(10);   // 자연 회복(포만 18 이상)이 피해를 가리지 않게
        return p;
    }

    private static ItemStack item(String name) {
        return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", name), 1);
    }

    // ------------------------------------------------------------------ 탑승 비행 킥

    /** 허공의 보트를 조종하며 위로/수평으로만 가는 이동을 보내면 떠 있다고 보고, 중력 0.04 → 160틱 넘게 이어지면 킥. */
    @Test
    void floatingVehicleKickedAfterMaximumTicks() {
        var level = new BlockPlacementTest.TestLevel();
        var boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -40, 0.5, 0, 0);
        level.addFreshEntity(boat);
        Player p = player(931, GameMode.SURVIVAL);
        assertTrue(p.startRiding(boat, true));
        p.tickRiding();
        assertEquals(VehicleMoves.Result.ACCEPTED, VehicleMoves.handle(p, 0.5, -40, 0.5, 0, 0));
        assertTrue(p.clientVehicleIsFloating, "허공·내려앉지 않음·중력 있음·주변 블록 없음");
        for (int i = 1; i <= 160; i++) p.tickRiding();
        assertNull(p.kickReason(), "++count > ceil(80 × max(0.08/0.04, 1)) = 160 까지는 괜찮다");
        p.tickRiding();
        assertEquals(org.domi.furnace.api.text.Text.translatable("multiplayer.disconnect.flying"), p.kickReason());
    }

    @Test
    void groundedOrWeightlessVehicleIsNotFloating() {
        var level = new BlockPlacementTest.TestLevel();
        var ground = new Boat(level, level.nextEntityId());
        ground.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(ground);
        Player p = player(932, GameMode.SURVIVAL);
        p.startRiding(ground, true);
        p.tickRiding();
        assertEquals(VehicleMoves.Result.ACCEPTED, VehicleMoves.handle(p, 0.5, -60, 0.5, 0, 0));
        assertFalse(p.clientVehicleIsFloating, "바닥 블록이 0.55 아래까지 있다");

        var air = new Boat(level, level.nextEntityId());
        air.moveTo(4.5, -40, 4.5, 0, 0);
        air.setNoGravity(true);
        level.addFreshEntity(air);
        Player q = player(933, GameMode.SURVIVAL);
        q.startRiding(air, true);
        q.tickRiding();
        assertEquals(VehicleMoves.Result.ACCEPTED, VehicleMoves.handle(q, 4.5, -40, 4.5, 0, 0));
        assertFalse(q.clientVehicleIsFloating, "중력 없는 탈것은 떠 있어도 킥하지 않는다");
        assertEquals(Integer.MAX_VALUE, PlayerMoveValidator.maximumFlyingTicks(VehicleMoves.gravityOf(air)));
    }

    @Test
    void fallingVehicleResetsTheFloatingCount() {
        var level = new BlockPlacementTest.TestLevel();
        var boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -40, 0.5, 0, 0);
        level.addFreshEntity(boat);
        Player p = player(934, GameMode.SURVIVAL);
        p.startRiding(boat, true);
        p.tickRiding();
        VehicleMoves.handle(p, 0.5, -40, 0.5, 0, 0);
        for (int i = 0; i < 100; i++) p.tickRiding();
        assertTrue(p.aboveGroundVehicleTickCount > 0);
        VehicleMoves.handle(p, 0.5, -40.5, 0.5, 0, 0);   // 요청 dy −0.5 < −0.03125: 내려앉는 중
        assertFalse(p.clientVehicleIsFloating);
        p.tickRiding();
        assertEquals(0, p.aboveGroundVehicleTickCount);
    }

    // ------------------------------------------------------------------ 탑승 낙하 피해·회전

    @Test
    void vehicleLandingHurtsPlayerPassenger() {
        var level = new BlockPlacementTest.TestLevel();
        var boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(boat);
        Player p = player(935, GameMode.SURVIVAL);
        p.startRiding(boat, true);
        float before = p.vitals().health();
        assertFalse(boat.causeFallDamage(10.0F, 1.0F, Entity.Damage.FALL), "탈것은 피해를 받지 않는다");
        p.drainOwnerTasks();
        assertEquals(before - 7.0F, p.vitals().health(), "ceil((10 − 3) × 1) = 7");
        // 안전 거리 안이면 무피해
        float now = p.vitals().health();
        boat.causeFallDamage(3.0F, 1.0F, Entity.Damage.FALL);
        p.drainOwnerTasks();
        assertEquals(now, p.vitals().health());
        // 크리에이티브(비행 가능)는 피해를 받지 않는다
        Player c = player(936, GameMode.CREATIVE);
        c.startRiding(boat, true);
        boat.causeFallDamage(30.0F, 1.0F, Entity.Damage.FALL);
        c.drainOwnerTasks();
        assertEquals(20.0F, c.vitals().health());
    }

    @Test
    void jumpBoostWidensSafeDistanceForPassengers() {
        var level = new BlockPlacementTest.TestLevel();
        var boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(boat);
        Player p = player(937, GameMode.SURVIVAL);
        p.vitals().addEffect(new MobEffectInstance(MobEffect.JUMP_BOOST, 600, 1));
        p.startRiding(boat, true);
        boat.causeFallDamage(10.0F, 1.0F, Entity.Damage.FALL);
        p.drainOwnerTasks();
        assertEquals(20.0F - 5.0F, p.vitals().health(), "점프 강화 II: 안전 거리 +2 → ceil(10 − 3 − 2) = 5");
    }

    @Test
    void boatTurnsAndClampsPlayerPassengerYaw() {
        var level = new BlockPlacementTest.TestLevel();
        var boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(boat);
        Player p = player(938, GameMode.SURVIVAL);
        p.startRiding(boat, true);
        p.applyRotation(150.0F, 5.0F, false);   // 배 방향 0 에서 150 도 틀어 본다
        boat.positionRider(p);
        assertEquals(105.0F, p.yaw(), 1e-4, "배 기준 ±105 도로 묶는다");
        assertEquals(5.0F, p.pitch());
        p.applyRotation(-20.0F, 0.0F, false);
        boat.positionRider(p);
        assertEquals(-20.0F, p.yaw(), 1e-4, "범위 안이면 그대로(배가 돌지 않을 때)");
    }

    // ------------------------------------------------------------------ 시선 보정

    @Test
    void visibilityPercentSneakInvisibleAndMatchingHead() {
        var level = new BlockPlacementTest.TestLevel();
        var zombie = new Zombie(level, level.nextEntityId());
        var skeleton = new Skeleton(level, level.nextEntityId());
        Player p = player(939, GameMode.SURVIVAL);
        assertEquals(1.0, p.visibilityPercent(zombie));
        p.sneaking(true);
        assertEquals(0.8, p.visibilityPercent(zombie));
        // 투명: 방어구 0칸 → 0.1 로 올려 0.7 × 0.1
        p.vitals().addEffect(new MobEffectInstance(MobEffect.INVISIBILITY, 600, 0));
        assertEquals(0.8 * 0.7 * (double) 0.1F, p.visibilityPercent(zombie), 1e-12);
        // 방어구 2칸(2/4) → 0.7 × 0.5
        p.inventory().set(6, item("minecraft:iron_chestplate"));
        p.inventory().set(7, item("minecraft:iron_leggings"));
        assertEquals(0.8 * 0.7 * 0.5, p.visibilityPercent(zombie), 1e-12);
        // 좀비 머리는 좀비에게만 ×0.5 (해골에겐 안 통한다)
        p.inventory().set(5, item("minecraft:zombie_head"));
        assertEquals(0.8 * 0.7 * 0.75 * 0.5, p.visibilityPercent(zombie), 1e-12);
        assertEquals(0.8 * 0.7 * 0.75, p.visibilityPercent(skeleton), 1e-12);
        assertEquals(0.8 * 0.7 * 0.75, p.visibilityPercent(null), 1e-12, "보는 쪽이 없으면 머리 아이템은 안 본다");
    }

    @Test
    void mobVisibilityUsesInvisibilityAndCreeperHead() {
        var level = new BlockPlacementTest.TestLevel();
        var creeper = new Creeper(level, level.nextEntityId());
        var victim = new Zombie(level, level.nextEntityId());
        assertEquals(1.0, victim.visibilityPercent(creeper));
        victim.setItemSlot(EquipmentSlot.HEAD, item("minecraft:creeper_head"));
        assertEquals(0.5, victim.visibilityPercent(creeper));
        victim.addEffect(new MobEffectInstance(MobEffect.INVISIBILITY, 600, 0));
        // 방어구 1칸(머리) → 0.7 × 0.25
        assertEquals(0.7 * 0.25 * 0.5, victim.visibilityPercent(creeper), 1e-12);
    }

    @Test
    void visibilityHelperMatchesVanillaBranches() {
        assertEquals(1.0, Visibility.percent(false, false, 0.0F, null, null));
        assertEquals(0.8, Visibility.percent(true, false, 0.0F, null, null));
        assertEquals(0.7 * (double) 0.1F, Visibility.percent(false, true, 0.0F, null, null), 1e-12);
        assertEquals(0.7 * 1.0, Visibility.percent(false, true, 1.0F, null, null), 1e-12);
        assertEquals(0.25F, Visibility.armorCoverPercentage(item("minecraft:iron_helmet"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
    }

    // ------------------------------------------------------------------ 공유 플래그

    @Test
    void sharedFlagsCarryFireInvisibleGlowing() {
        Player p = player(940, GameMode.SURVIVAL);
        assertEquals(0, p.sharedFlags());
        p.igniteForSeconds(8.0F);
        assertEquals(1, p.sharedFlags() & 1, "불 붙음 = 플래그 0");
        p.vitals().addEffect(new MobEffectInstance(MobEffect.INVISIBILITY, 600, 0));
        p.vitals().addEffect(new MobEffectInstance(MobEffect.GLOWING, 600, 0));
        assertEquals(0x20 | 0x40 | 1, p.sharedFlags());
        p.vitals().removeEffect(MobEffect.INVISIBILITY);
        assertEquals(0x40 | 1, p.sharedFlags());
        p.clearFire();
        assertEquals(0x40, p.sharedFlags());
        p.sneaking(true);
        assertEquals(0x40 | 2, p.sharedFlags());
    }

    // ------------------------------------------------------------------ 플레이어 불

    @Test
    void burningHurtsEveryTwentyTicksAndWaterExtinguishes() {
        var level = new BlockPlacementTest.TestLevel();
        Player p = seasoned(941, GameMode.SURVIVAL);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        assertEquals(-Player.FIRE_IMMUNE_TICKS, p.remainingFireTicks(), "처음엔 면역 틱 −20");
        p.igniteForSeconds(8.0F);
        assertEquals(160, p.remainingFireTicks());
        PlayerFire.tick(p, level, false);
        assertEquals(19.0F, p.vitals().health(), "첫 틱 160 % 20 == 0 → 1 피해");
        assertEquals(159, p.remainingFireTicks());
        for (int i = 0; i < 20; i++) {
            p.vitals().tick(PlayerVitals.Surroundings.NONE);
            PlayerFire.tick(p, level, false);
        }
        assertEquals(18.0F, p.vitals().health(), 0.01F, "20틱 뒤 한 번 더");
        PlayerFire.tick(p, level, true);
        assertEquals(0, p.remainingFireTicks(), "물에 닿으면 clearFire(0)");
        assertFalse(p.isOnFire());
    }

    @Test
    void lavaIgnitesFifteenSecondsAndHurtsFour() {
        var level = new BlockPlacementTest.TestLevel();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setBlock(new Pos(x, -60, z), org.domi.furnace.server.block.B.defaultState("minecraft:lava"), 3);
        Player p = seasoned(942, GameMode.SURVIVAL);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        PlayerFire.tick(p, level, false);
        assertEquals(15 * 20, p.remainingFireTicks());
        assertEquals(16.0F, p.vitals().health(), "용암 피해 4");
        p.vitals().setFallDistance(10.0F);
        PlayerFire.tick(p, level, false);
        assertEquals(5.0F, p.vitals().fallDistance(), "용암 속에서는 낙하 거리 절반");
    }

    @Test
    void fireBlockIgnitesAfterTwentyTicksStandingAndLeavingResetsToImmune() {
        var level = new BlockPlacementTest.TestLevel();
        Player p = player(943, GameMode.SURVIVAL);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        for (int i = 0; i < 19; i++) PlayerFire.fireInside(p, 1.0F);
        assertEquals(-1, p.remainingFireTicks());
        assertFalse(p.isOnFire());
        PlayerFire.fireInside(p, 1.0F);
        assertEquals(8 * 20, p.remainingFireTicks(), "−20 에서 20번 오르면 0 → 8초 불붙임");
        // 불·용암이 없는 곳에서 이동을 마치면 (꺼진 상태라면) 면역 틱으로
        p.setRemainingFireTicks(0);
        PlayerFire.afterMove(p, level, false);
        assertEquals(-Player.FIRE_IMMUNE_TICKS, p.remainingFireTicks());
        // 불붙은 채 물에 있으면 이동 끝에서 꺼져 면역 틱으로
        p.setRemainingFireTicks(100);
        PlayerFire.afterMove(p, level, true);
        assertEquals(-Player.FIRE_IMMUNE_TICKS, p.remainingFireTicks());
        // 물이 아니면 계속 탄다
        p.setRemainingFireTicks(100);
        PlayerFire.afterMove(p, level, false);
        assertEquals(100, p.remainingFireTicks());
    }

    @Test
    void dyingPutsOutTheFire() {
        Player p = seasoned(948, GameMode.SURVIVAL);
        p.igniteForSeconds(10.0F);
        p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.byKey("generic_kill"), 1000.0F);
        p.drainOwnerTasks();
        assertTrue(p.vitals().isDeadOrDying());
        assertFalse(p.isOnFire());
        assertEquals(0, p.sharedFlags() & 1);
    }

    @Test
    void fireProtectionShortensBurningTime() {
        Player p = player(944, GameMode.SURVIVAL);
        var boots = item("minecraft:iron_boots");
        boots = org.domi.furnace.server.item.ItemTools.with(boots, "minecraft:enchantments", java.util.Map.of("levels", java.util.Map.of("minecraft:fire_protection", 4)));
        p.inventory().set(8, boots);
        // 불 보호 IV: add_multiplied_base −0.15 − 0.15 × 3 = −0.6 → 0.4 배
        assertEquals(0.4, PlayerAttributes.value(p, org.domi.furnace.server.attribute.Attribute.BURNING_TIME), 1e-6);
        p.igniteForSeconds(10.0F);
        assertEquals(80, p.remainingFireTicks(), "ceil(200 × 0.4)");
    }

    @Test
    void fireSurvivesSaveAndLoad() {
        Player p = player(945, GameMode.SURVIVAL);
        p.igniteForSeconds(5.0F);
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        p.saveFire(tag);
        assertEquals((short) 100, tag.tags().get("Fire"));
        Player q = player(946, GameMode.SURVIVAL);
        q.loadFire(tag);
        assertEquals(100, q.remainingFireTicks());
        Player r = player(947, GameMode.SURVIVAL);
        r.loadFire(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        assertEquals(-Player.FIRE_IMMUNE_TICKS, r.remainingFireTicks(), "필드가 없으면 면역 틱 그대로");
    }

    @Test
    void mobIgniteUsesBurningTimeAttribute() {
        var level = new BlockPlacementTest.TestLevel();
        var z = new Zombie(level, level.nextEntityId());
        z.igniteForSeconds(5.0F);
        assertEquals(100, z.remainingFireTicks());
        z.attributes().get(org.domi.furnace.server.attribute.Attribute.BURNING_TIME).setBaseValue(0.5);
        z.clearFire();
        z.igniteForSeconds(5.0F);
        assertEquals(50, z.remainingFireTicks());
    }

    // ------------------------------------------------------------------ 몹 수영·휘두르기

    @Test
    void mobSwingDurationFollowsHasteAndMiningFatigue() {
        var level = new BlockPlacementTest.TestLevel();
        var z = new Zombie(level, level.nextEntityId());
        assertEquals(6, z.getCurrentSwingDuration());
        z.addEffect(new MobEffectInstance(MobEffect.HASTE, 600, 1));
        assertEquals(6 - 2, z.getCurrentSwingDuration());
        z.removeEffect(MobEffect.HASTE);
        z.addEffect(new MobEffectInstance(MobEffect.MINING_FATIGUE, 600, 2));
        assertEquals(6 + 3 * 2, z.getCurrentSwingDuration());
    }
}
