// 엔티티 활성 범위(D11, Paper 1.21.1 EAR) 단위 시험: 종류 분류, 늘 도는 엔티티, 먼 아이템 낙하 시작 틱, 범위 상자, 20틱 면역 검사 주기, 깨우기 몫, 잠깐 깨운 엔티티의 이동 건너뛰기,
// 피스톤 면역, 꺼졌을 때 아무것도 안 하는 것.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.config.EntityActivationSettings;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.scene.SceneKit;
import org.domi.furnace.server.scene.SceneLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityActivationTest {

    private SceneLevel level;
    private Player player;

    @BeforeEach
    void setUp() {
        EntityActivationSettings.install(EntityActivationSettings.DEFAULT);   // 시험 JVM 기본은 꺼짐(EntityActivationOffExtension)
        SceneKit.install();
        level = new SceneLevel("ear-test", 6);
        player = SceneKit.addPlayer(level, "A", 0.5, SceneLevel.FLOOR_Y, 0.5);
    }

    @AfterEach
    void restore() { EntityActivationSettings.install(EntityActivationSettings.OFF); }

    private Entity spawn(String type, double x, double z) {
        Entity e = Entities.create(level, type);
        e.moveTo(x, SceneLevel.FLOOR_Y, z, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    /** 깨우기 몫을 0 으로(면역 주기만 보이게). */
    private static EntityActivationSettings noWake() {
        var d = EntityActivationSettings.DEFAULT;
        return new EntityActivationSettings(true, d.animals(), d.monsters(), d.raiders(), d.misc(), d.water(), d.villagers(), d.flyingMonsters(),
                d.tickInactiveVillagers(), d.ignoreSpectators(), 0, d.wakeAnimalsEvery(), d.wakeAnimalsFor(), 0, d.wakeMonstersEvery(), d.wakeMonstersFor(),
                0, d.wakeVillagersEvery(), d.wakeVillagersFor(), 0, d.wakeFlyingEvery(), d.wakeFlyingFor(),
                d.villagersWorkImmunityAfter(), d.villagersWorkImmunityFor(), d.villagersActiveForPanic());
    }

    @Test
    void classifiesLikePaper() {
        assertEquals(EntityActivation.ANIMAL, spawn("minecraft:cow", 0, 0).activationType);
        assertEquals(EntityActivation.ANIMAL, spawn("minecraft:bat", 0, 0).activationType);
        assertEquals(EntityActivation.MONSTER, spawn("minecraft:zombie", 0, 0).activationType);
        assertEquals(EntityActivation.MONSTER, spawn("minecraft:slime", 0, 0).activationType);
        assertEquals(EntityActivation.MONSTER, spawn("minecraft:hoglin", 0, 0).activationType, "Enemy 인 동물");
        assertEquals(EntityActivation.FLYING_MONSTER, spawn("minecraft:ghast", 0, 0).activationType);
        assertEquals(EntityActivation.FLYING_MONSTER, spawn("minecraft:phantom", 0, 0).activationType);
        assertEquals(EntityActivation.WATER, spawn("minecraft:squid", 0, 0).activationType);
        assertEquals(EntityActivation.WATER, spawn("minecraft:cod", 0, 0).activationType);
        assertEquals(EntityActivation.VILLAGER, spawn("minecraft:villager", 0, 0).activationType);
        assertEquals(EntityActivation.ANIMAL, spawn("minecraft:wandering_trader", 0, 0).activationType);
        assertEquals(EntityActivation.RAIDER, spawn("minecraft:pillager", 0, 0).activationType);
        assertEquals(EntityActivation.RAIDER, spawn("minecraft:witch", 0, 0).activationType);
        assertEquals(EntityActivation.MISC, spawn("minecraft:item", 0, 0).activationType);
        assertEquals(EntityActivation.MISC, spawn("minecraft:experience_orb", 0, 0).activationType);
    }

    @Test
    void alwaysActiveKinds() {
        var s = EntityActivationSettings.DEFAULT;
        for (String t : List.of("minecraft:tnt", "minecraft:falling_block", "minecraft:minecart", "minecraft:boat", "minecraft:chest_boat",
                "minecraft:end_crystal", "minecraft:firework_rocket", "minecraft:trident", "minecraft:snowball", "minecraft:fireball",
                "minecraft:wind_charge", "minecraft:lightning_bolt", "minecraft:eye_of_ender", "minecraft:wither")) {
            assertTrue(EntityActivation.alwaysActive(spawn(t, 0, 0), s), t);
        }
        for (String t : List.of("minecraft:cow", "minecraft:zombie", "minecraft:item", "minecraft:arrow", "minecraft:armor_stand")) {
            assertFalse(EntityActivation.alwaysActive(spawn(t, 0, 0), s), t);
        }
        // 범위 0 이하인 종류는 늘 활성
        var noAnimals = new EntityActivationSettings(true, 0, 32, 64, 16, 16, 32, 32, true, false, 4, 1200, 100, 8, 400, 100, 4, 600, 100, 8, 200, 100, 100, 20, true);
        assertTrue(EntityActivation.alwaysActive(spawn("minecraft:cow", 0, 0), noAnimals));
        assertFalse(EntityActivation.alwaysActive(spawn("minecraft:zombie", 0, 0), noAnimals));
    }

    @Test
    void rangeBoxMarksNearOnly() {
        EntityActivationSettings.install(EntityActivationSettings.DEFAULT);
        Entity near = spawn("minecraft:cow", 30.5, 0.5), far = spawn("minecraft:cow", 34.5, 0.5);   // 동물 32 + 상자 반폭
        Entity zombieFar = spawn("minecraft:zombie", 0.5, 31.5), raider = spawn("minecraft:pillager", 60.5, 0.5);
        level.gameTime = 1000;
        level.activateEntities(level.players);
        assertTrue(level.activation().on());
        assertTrue(EntityActivation.isMarkedActive(near));
        assertFalse(EntityActivation.isMarkedActive(far));
        assertTrue(EntityActivation.isMarkedActive(zombieFar));
        assertTrue(EntityActivation.isMarkedActive(raider), "습격 몹은 64");
    }

    @Test
    void offDoesNothing() {
        EntityActivationSettings.install(EntityActivationSettings.OFF);
        Entity far = spawn("minecraft:cow", 80.5, 0.5);
        level.activateEntities(level.players);
        assertFalse(level.activation().on());
        assertTrue(EntityActivation.isMarkedActive(far));
        EntityActivation.immunize(far, 20);
        assertEquals(Integer.MIN_VALUE, far.activatedTick, "꺼져 있으면 피스톤 면역도 쓰지 않는다");
    }

    @Test
    void inactiveIdleAnimalRunsOneTickInTwenty() {
        EntityActivationSettings.install(noWake());
        Entity cow = spawn("minecraft:cow", 80.5, 0.5);
        cow.tickCount = 400;
        cow.setOnGround(true);
        cow.setDeltaMovement(0, 0, 0);
        int active = 0;
        for (int t = 0; t < 60; t++) {
            level.gameTime = 5000 + t;
            level.activateEntities(level.players);
            if (EntityActivation.checkIfActive(cow, level.activation())) {
                active++;
                assertTrue(cow.isTemporarilyActive, "면역 없는 깨움은 잠깐 활성(이동 건너뜀)");
            }
            cow.tickCount++;
        }
        assertEquals(3, active, "면역이 없으면 20틱에 한 번만");
    }

    @Test
    void wakeUpBudgetGivesImmunity() {
        EntityActivationSettings.install(EntityActivationSettings.DEFAULT);   // 동물 깨우기 4/틱, 1200틱 넘게 비활성이면, 100틱
        Entity cow = spawn("minecraft:cow", 80.5, 0.5);
        cow.tickCount = 400;
        cow.setOnGround(true);
        long now = 0;
        boolean woke = false;
        for (int t = 0; t < 20 && !woke; t++) {
            level.gameTime = now = 7000 + t;
            level.activateEntities(level.players);
            woke = EntityActivation.checkIfActive(cow, level.activation()) && cow.activatedTick == now + 100;
            cow.tickCount++;
        }
        assertTrue(woke, "한 번도 활성이 아니었던 동물은 깨우기 몫으로 100틱 활성");
        assertFalse(cow.isTemporarilyActive);
    }

    @Test
    void movingOrAirborneIsImmune() {
        EntityActivationSettings.install(noWake());
        Entity cow = spawn("minecraft:cow", 80.5, 0.5);
        cow.tickCount = 400;
        cow.setOnGround(false);
        level.gameTime = 9000;
        level.activateEntities(level.players);
        assertEquals(100, EntityActivation.checkEntityImmunities(cow, level.activation(), EntityActivationSettings.current()));
        cow.setOnGround(true);
        cow.setDeltaMovement(0.1, 0, 0);
        assertEquals(100, EntityActivation.checkEntityImmunities(cow, level.activation(), EntityActivationSettings.current()));
        cow.setDeltaMovement(0, 0, 0);
        assertEquals(-1, EntityActivation.checkEntityImmunities(cow, level.activation(), EntityActivationSettings.current()));
        cow.setRemainingFireTicks(10);
        assertEquals(2, EntityActivation.checkEntityImmunities(cow, level.activation(), EntityActivationSettings.current()));
    }

    @Test
    void youngAndLeashedAreAlwaysActive() {
        EntityActivationSettings.install(noWake());
        Entity cow = spawn("minecraft:cow", 80.5, 0.5);
        cow.setOnGround(true);
        level.gameTime = 11001;
        level.activateEntities(level.players);
        cow.tickCount = 199;
        assertTrue(EntityActivation.checkIfActive(cow, level.activation()), "10초 안의 새 엔티티");
        cow.tickCount = 400;
        ((Mob) cow).setLeashedTo(player, true);
        assertTrue(EntityActivation.checkIfActive(cow, level.activation()), "플레이어가 묶은 몹");
    }

    @Test
    void temporarilyActiveSkipsOwnVelocityMoveButItemsMove() {
        Entity cow = spawn("minecraft:cow", 80.5, 0.5);
        cow.isTemporarilyActive = true;
        cow.setDeltaMovement(0.2, 0, 0);
        double x0 = cow.x();
        cow.move(Entity.MOVER_SELF, cow.vx(), cow.vy(), cow.vz());
        assertEquals(x0, cow.x());
        assertEquals(0.0, cow.vx());
        cow.setDeltaMovement(0.2, 0, 0);
        cow.move(Entity.MOVER_SELF, 0.1, 0, 0);   // 제 속도가 아닌 이동은 그대로
        assertNotEquals(x0, cow.x());

        Entity item = spawn("minecraft:item", 80.5, 5.5);
        item.isTemporarilyActive = true;
        item.setDeltaMovement(0.2, 0, 0);
        double ix = item.x();
        item.move(Entity.MOVER_SELF, item.vx(), item.vy(), item.vz());
        assertNotEquals(ix, item.x(), "아이템은 건너뛰지 않는다");
    }

    @Test
    void pistonPushImmunizesWhenOn() {
        EntityActivationSettings.install(EntityActivationSettings.DEFAULT);
        Entity cow = spawn("minecraft:cow", 80.5, 0.5);
        level.gameTime = 3000;
        level.activateEntities(level.players);
        cow.move(Entity.MOVER_PISTON, 0.1, 0, 0);
        assertEquals(3020, cow.activatedTick);
        assertEquals(3020, cow.activatedImmunityTick);
    }

    /**
     * 기둥 위 먼 아이템(플레이어에게서 80칸): 300틱 뒤 기둥을 지우고 y 가 처음 내려간 틱(지운 뒤 몇 틱째)과, 지운 뒤 첫 중력 틱을 돌려준다.
     * extraIds 로 아이템 id 를 밀어 중력 틱 위상((tickCount + id) % 4)을 바꾼다.
     */
    private static int[] farItemFall(EntityActivationSettings s, int extraIds) {
        EntityActivationSettings.install(s);
        SceneLevel lv = new SceneLevel("ear-item", 7);
        SceneKit.addPlayer(lv, "A", 0.5, SceneLevel.FLOOR_Y, 0.5);
        int stone = B.defaultState("minecraft:stone"), air = B.defaultState("minecraft:air");
        Pos pillar = new Pos(80, SceneLevel.FLOOR_Y, 0);
        lv.setBlock(pillar, stone, 2);
        for (int i = 0; i < extraIds; i++) lv.nextEntityId();
        ItemEntity item = new ItemEntity(lv, lv.nextEntityId(), 80.5, SceneLevel.FLOOR_Y + 1, 0.5,
                new ItemStack(ItemContext.id("minecraft:item", "diamond"), 1));
        item.setNeverPickUp();
        item.setDeltaMovement(0, 0, 0);
        lv.addFreshEntity(item);
        for (int t = 0; t < 300; t++) lv.tick();
        assertTrue(item.onGround(), "기둥 위에 앉아 있어야 한다");
        lv.setBlock(pillar, air, 2);
        int first = -1, firstGravity = -1;
        double last = item.y();
        for (int t = 1; t <= 20; t++) {
            if (firstGravity < 0 && (item.tickCount + 1 + item.id()) % 4 == 0) firstGravity = t;   // 이번 틱의 tickCount++ 뒤 중력 틱인가
            lv.tick();
            if (first < 0 && item.y() < last - 1e-9) first = t;
            last = item.y();
        }
        return new int[]{first, firstGravity};
    }

    /** 먼 아이템도 바닐라처럼 받침이 사라진 뒤 첫 중력 틱에 떨어지기 시작한다(Spigot 1.21.3 식 +1 — 빼면 20틱 동안 안 떨어진다). id 위상 4가지 모두. */
    @Test
    void farItemStartsFallingOnItsFirstGravityTick() {
        for (int k = 0; k < 4; k++) {
            int[] off = farItemFall(EntityActivationSettings.OFF, k), on = farItemFall(noWake(), k);
            assertEquals(off[1], off[0], "바닐라: 첫 중력 틱에 떨어지기 시작 (위상 " + k + ")");
            assertEquals(on[1], on[0], "EAR 켬: 바닐라처럼 첫 중력 틱에 떨어지기 시작 (위상 " + k + ")");
        }
    }
}
