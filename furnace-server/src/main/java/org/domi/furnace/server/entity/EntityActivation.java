// 엔티티 활성 범위(Spigot ActivationRange + Paper "Entity Activation Range 2.0", Paper 1.21.1 기준) — D11, 기본 켜짐.
// 바닐라와 결과가 다르다: 플레이어에게서 먼 몹은 틱 대부분을 inactiveTick(나이·타이머만)으로 보낸다. 꺼져 있으면 이 클래스는 아무 데서도 불리지 않는다.
//
// Paper 와 다른 점(의도):
//  - 시계: Paper 의 MinecraftServer.currentTick 대신 그 차원의 gameTime 을 region 틱 첫머리에 한 번 읽어 쓴다(region 마다 따로 돌아도 차원 전체에
//    같은 값). /tick freeze 동안은 엔티티 틱이 없고 gameTime 도 멈춘다.
//  - 깨우기 몫(wake-up-inactive.*)은 월드가 아니라 region 마다 센다(Folia 와 같다). region 이 하나면 Paper 와 같다.
//  - 활성으로 만드는 플레이어는 그 region 의 플레이어다(Folia 와 같다 — 활성 범위 안의 칸은 플레이어와 같은 region 에 든다).
//  - Paper 1.21.3 의 EAR 수정 두 가지를 따랐다: 활성 엔티티를 4틱에 한 번 건너뛰던 Spigot 동작을 없앴고, 아이템은 중력 틱(바닐라가 땅 위
//    아이템을 4틱에 한 번만 움직이는 틱)에 늘 활성이다. 둘 다 바닐라에 더 가깝다(docs/user-todo/entries/2026-11-14-entity-activation-range.md).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.config.EntityActivationSettings;
import org.domi.furnace.server.entity.ai.brain.Activity;

public final class EntityActivation {

    private EntityActivation() {}

    // Paper ActivationType(순서는 의미 없음)
    public static final byte WATER = 0, FLYING_MONSTER = 1, VILLAGER = 2, MONSTER = 3, ANIMAL = 4, RAIDER = 5, MISC = 6;
    private static final int TYPES = 7;

    private static final Activity[] VILLAGER_PANIC_IMMUNITIES = {Activity.HIDE, Activity.PRE_RAID, Activity.RAID, Activity.PANIC};

    /** 레벨(region) 하나의 활성 상태: 이번 틱 시계, 종류별 상자, 깨우기 몫. 레벨의 주인 스레드만 쓴다. */
    public static final class LevelState {
        /** 이번 틱에 활성 범위를 적용하는가(설정이 켜졌고 이번 틱에 activate 를 돌렸다). */
        boolean on;
        long now;
        EntityActivationSettings settings = EntityActivationSettings.OFF;
        int wakeAnimals, wakeVillagers, wakeMonsters, wakeFlying;
        // 종류별 상자(플레이어마다 다시 쓴다 — Paper ActivationType.boundingBox)
        private final double[][] box = new double[TYPES][6];

        public boolean on() { return on; }

        public long now() { return now; }

        /** 끈다(설정이 꺼졌거나 시험 레벨). 다음 activate 전까지 엔티티 틱은 바닐라대로. */
        public void off() { on = false; }
    }

    /** Paper ActivationRange.initializeEntityActivationType. Entity 생성 때 한 번. */
    static byte typeOf(Entity e) {
        if (e instanceof WaterAnimal) return WATER;
        if (e instanceof Villager) return VILLAGER;
        if (e instanceof FlyingMob && e instanceof Enemy) return FLYING_MONSTER;
        if (e instanceof Raider) return RAIDER;
        if (e instanceof Enemy) return MONSTER;
        if (e instanceof PathfinderMob) return ANIMAL;   // 바닐라 AmbientCreature(박쥐)는 Furnace 에서 PathfinderMob 이다 — 같은 ANIMAL
        return MISC;
    }

    private static int range(EntityActivationSettings s, byte type) {
        return switch (type) {
            case WATER -> s.water();
            case FLYING_MONSTER -> s.flyingMonsters();
            case VILLAGER -> s.villagers();
            case MONSTER -> s.monsters();
            case ANIMAL -> s.animals();
            case RAIDER -> s.raiders();
            default -> s.misc();
        };
    }

    /** Paper initializeEntityActivationState: 늘 도는 엔티티(범위 0 이하인 종류, 투사체·탈것·폭발물·보스 등). */
    static boolean alwaysActive(Entity e, EntityActivationSettings s) {
        return range(s, e.activationType) <= 0
                || e instanceof EyeOfEnder
                || e instanceof ThrowableProjectile
                || e instanceof EnderDragon
                || e instanceof WitherBoss
                || e instanceof AbstractHurtingProjectile
                || e instanceof LightningBolt
                || e instanceof PrimedTnt
                || e instanceof FallingBlockEntity
                || e instanceof AbstractMinecart
                || e instanceof Boat
                || e instanceof EndCrystal
                || e instanceof FireworkRocketEntity
                || e instanceof ThrownTrident;
    }

    /**
     * Paper ActivationRange.activateEntities: 이번 틱 시계를 정하고, 깨우기 몫을 1씩 채우고, 플레이어마다 종류별 범위 상자 안의 엔티티를 활성으로.
     * 엔티티 틱 바로 앞(드래곤 전투 뒤)에서 부른다. 설정이 꺼져 있으면 state 를 끄기만 한다.
     */
    public static void activate(ChunkLevel level, LevelState st, Iterable<Player> players, int simulationDistance) {
        EntityActivationSettings s = EntityActivationSettings.current();
        if (!s.enabled()) {
            st.on = false;
            return;
        }
        st.on = true;
        st.settings = s;
        long now = level.gameTime();
        st.now = now;
        st.wakeAnimals = Math.min(st.wakeAnimals + 1, s.wakeAnimalsMax());
        st.wakeVillagers = Math.min(st.wakeVillagers + 1, s.wakeVillagersMax());
        st.wakeMonsters = Math.min(st.wakeMonsters + 1, s.wakeMonstersMax());
        st.wakeFlying = Math.min(st.wakeFlying + 1, s.wakeFlyingMax());
        int maxRange = Math.max(s.monsters(), s.animals());
        maxRange = Math.max(maxRange, s.raiders());
        maxRange = Math.max(maxRange, s.misc());
        maxRange = Math.max(maxRange, s.flyingMonsters());
        maxRange = Math.max(maxRange, s.water());
        maxRange = Math.max(maxRange, s.villagers());
        maxRange = Math.min((simulationDistance << 4) - 8, maxRange);
        int height = level.maxBuildHeight() - level.minBuildHeight();
        for (Player p : players) {
            if (s.ignoreSpectators() && p.isSpectator()) continue;
            AABB pb = p.boundingBox();
            for (byte t = 0; t < TYPES; t++) {
                int r = range(s, t);
                double[] b = st.box[t];
                // AABB.inflate(r, height, r) — 음수 범위면 상자가 뒤집혀 아무것과도 안 겹친다(Paper 와 같다)
                b[0] = pb.minX() - r; b[1] = pb.minY() - height; b[2] = pb.minZ() - r;
                b[3] = pb.maxX() + r; b[4] = pb.maxY() + height; b[5] = pb.maxZ() + r;
            }
            AABB maxBB = pb.inflate(maxRange, height, maxRange);
            for (Entity e : level.getEntities(null, maxBB, null)) activateEntity(e, st, s, now);
        }
    }

    /** Paper activateEntity. */
    private static void activateEntity(Entity e, LevelState st, EntityActivationSettings s, long now) {
        if (now > e.activatedTick) {
            if (alwaysActive(e, s)) {
                e.activatedTick = now;
                return;
            }
            double[] b = st.box[e.activationType];
            AABB eb = e.boundingBox();
            if (eb.intersects(b[0], b[1], b[2], b[3], b[4], b[5])) e.activatedTick = now;
        }
    }

    /** Paper checkInactiveWakeup: 오래 비활성이고 몫이 남았으면 깨울 틱 수, 아니면 -1(몫을 하나 쓴다). */
    private static int checkInactiveWakeup(Entity e, LevelState st, EntityActivationSettings s) {
        long inactiveFor = st.now - e.activatedTick;
        switch (e.activationType) {
            case VILLAGER -> {
                if (inactiveFor > s.wakeVillagersEvery() && st.wakeVillagers > 0) {
                    st.wakeVillagers--;
                    return s.wakeVillagersFor();
                }
            }
            case ANIMAL -> {
                if (inactiveFor > s.wakeAnimalsEvery() && st.wakeAnimals > 0) {
                    st.wakeAnimals--;
                    return s.wakeAnimalsFor();
                }
            }
            case FLYING_MONSTER -> {
                if (inactiveFor > s.wakeFlyingEvery() && st.wakeFlying > 0) {
                    st.wakeFlying--;
                    return s.wakeFlyingFor();
                }
            }
            case MONSTER, RAIDER -> {
                if (inactiveFor > s.wakeMonstersEvery() && st.wakeMonsters > 0) {
                    st.wakeMonsters--;
                    return s.wakeMonstersFor();
                }
            }
            default -> {}
        }
        return -1;
    }

    /** Paper checkEntityImmunities: 범위 밖이어도 잠깐 돌려야 하는 상태면 활성 틱 수(0 이상), 아니면 -1. */
    static int checkEntityImmunities(Entity e, LevelState st, EntityActivationSettings s) {
        int wake = checkInactiveWakeup(e, st, s);
        if (wake > -1) return wake;
        if (e.remainingFireTicks() > 0) return 2;
        if (e.activatedImmunityTick >= st.now) return 1;
        long inactiveFor = st.now - e.activatedTick;
        if (e.activationType != WATER && e.isInWater() && e.isPushedByFluid()) return 100;
        if (!e.onGround() || e.vx() * e.vx() + e.vz() * e.vz() > 9.999999747378752E-6D) return 100;
        if (!(e instanceof AbstractArrow arrow)) {
            if (!e.onGround() && !(e instanceof FlyingMob)) return 10;   // 위에서 이미 걸러져 닿지 않는다(Paper 와 같음)
        } else if (!arrow.inGround) {
            return 1;
        }
        if (e instanceof LivingEntity living) {
            if (living.onClimbable() || living.jumping || living.hurtTime > 0 || !living.getActiveEffects().isEmpty() || living.isFreezing()) return 1;
            if (e instanceof Mob mob && mob.getTarget() != null) return 20;
            if (e instanceof Bee bee) {
                var moving = bee.movingTarget;
                if (bee.isAngry() || bee.hivePos != null && bee.hivePos.equals(moving) || bee.savedFlowerPos != null && bee.savedFlowerPos.equals(moving)) {
                    return 20;
                }
            }
            if (e instanceof Villager villager) {
                var brain = villager.getBrain();
                if (s.villagersActiveForPanic()) {
                    for (Activity a : VILLAGER_PANIC_IMMUNITIES) if (brain.isActive(a)) return 20 * 5;
                }
                if (s.villagersWorkImmunityAfter() > 0 && inactiveFor >= s.villagersWorkImmunityAfter() && brain.isActive(Activity.WORK)) {
                    return s.villagersWorkImmunityFor();
                }
            }
            if (e instanceof Llama llama && llama.inCaravan()) return 1;
            if (e instanceof Animal animal) {
                if (animal.isBaby() || animal.isInLove()) return 5;
                if (e instanceof Sheep sheep && sheep.isSheared()) return 1;
            }
            if (e instanceof Creeper creeper && creeper.isIgnited()) return 20;
            if (e instanceof Mob mob && mob.targetSelector().hasTasks()) return 0;
        }
        if (e instanceof ExperienceOrb) return 20;   // SPIGOT-6644: 표적 다시 찾는 틱을 놓치지 않게
        return -1;
    }

    /** 아이템이 이번 틱(tickCount 를 올린 뒤)에 바닐라 땅 위 4틱 주기 이동 검사를 받는가 — ItemEntity.tick 의 (tickCount + id) % 4 == 0. */
    private static boolean itemGravityTick(Entity e) { return (e.tickCount + 1 + e.id()) % 4 == 0; }

    /**
     * Paper ActivationRange.checkIfActive: 이번 틱에 tick(활성) 을 돌릴지, inactiveTick 을 돌릴지. tickCount 를 올리기 전에 부른다.
     * st.on 이 거짓이면(꺼짐) 부르지 않는다.
     */
    public static boolean checkIfActive(Entity e, LevelState st) {
        EntityActivationSettings s = st.settings;
        if (e instanceof FireworkRocketEntity || e instanceof ItemEntity && itemGravityTick(e)) return true;
        // 늘 활성: 늘 도는 종류, 새로 생긴 지 10초 안, 죽음, 포털 안·포털 대기
        if (alwaysActive(e, s) || e.tickCount < 20 * 10 || !e.isAlive() || e.portalProcess != null && !e.portalProcess.hasExpired() || e.portalCooldown > 0) {
            return true;
        }
        if (e instanceof Mob mob && mob.getLeashHolder() instanceof Player) return true;   // 플레이어가 묶은 몹
        long now = st.now;
        boolean active = e.activatedTick >= now;
        e.isTemporarilyActive = false;
        if (!active) {
            if ((now - e.activatedTick - 1) % 20 == 0) {   // 비활성은 20틱마다 면역을 본다
                int immunity = checkEntityImmunities(e, st, s);
                if (immunity >= 0) e.activatedTick = now + immunity;
                else e.isTemporarilyActive = true;   // 이 틱만: 제 속도로 움직이는 이동은 건너뛴다(Entity.move)
                active = true;
            }
        }
        return active;
    }

    /** 지금 활성 표시(범위 안이거나 면역 기간)인가 — 운영 명령·시험용. 꺼져 있으면 늘 참. */
    public static boolean isMarkedActive(Entity e) {
        ChunkLevel level = e.level();
        if (level == null || !level.activation().on) return true;
        return e.activatedTick >= level.activation().now;
    }

    /** Paper: 피스톤에 밀린(ticks=20)·점액 피스톤에 튕긴(ticks=10) 엔티티는 그만큼 활성·면역. 꺼져 있으면 아무것도. */
    public static void immunize(Entity e, int ticks) {
        ChunkLevel level = e.level();
        if (level == null) return;
        LevelState st = level.activation();
        if (!st.on) return;
        e.activatedTick = Math.max(e.activatedTick, st.now + ticks);
        e.activatedImmunityTick = Math.max(e.activatedImmunityTick, st.now + ticks);
    }
}
