// 1.21 효과 4종의 발동(바닐라 1.21.1 WindChargedMobEffect·WeavingMobEffect·OozingMobEffect·InfestedMobEffect, javap 대조).
// onMobRemoved(죽어서 KILLED 로 빠질 때): 돌풍 폭발·거미줄·슬라임. onMobHurt(맞을 때): 좀벌레. 난수 순서는 바닐라 그대로다.
package org.domi.furnace.server.effect;

import java.util.HashSet;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.AbstractWindCharge;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Silverfish;
import org.domi.furnace.server.entity.Slime;

public final class MobEffectTriggers {

    private MobEffectTriggers() {}

    /** 효과를 가진 대상: 몹(entity) 또는 플레이어(player) 중 하나. random 은 그 대상의 엔티티 난수. */
    public record Target(ChunkLevel level, Entity entity, Player player, RandomSource random) {
        public static Target of(Entity e) { return new Target(e.level(), e, null, e.random()); }

        public static Target of(ChunkLevel level, Player p) { return new Target(level, null, p, p.random()); }

        double x() { return entity != null ? entity.x() : player.x(); }

        double y() { return entity != null ? entity.y() : player.y(); }

        double z() { return entity != null ? entity.z() : player.z(); }

        float bbHeight() { return entity != null ? entity.bbHeight() : player.bbHeight(); }

        Pos blockPosition() { return entity != null ? entity.blockPosition() : player.blockPosition(); }

        AABB boundingBox() { return entity != null ? entity.boundingBox() : player.boundingBox(); }

        /** getDeltaMovement. 플레이어는 서버가 아는 근사값(Player.serverMovement — 가정). */
        double[] deltaMovement() { return entity != null ? new double[] {entity.vx(), entity.vy(), entity.vz()} : player.serverMovement(); }
    }

    /** WindChargedMobEffect·SimpleExplosionDamageCalculator(AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR) — 브리즈 돌풍과 같다. */
    private static final Explosion.Calculator WIND_CALC = new Explosion.WindCalculator(null);
    /** 바닐라 SoundEvents.BREEZE_WIND_CHARGE_BURST(avp.cQ) — 윈드 차지의 entity.wind_charge.wind_burst 와 다르다. */
    public static final String BREEZE_BURST_SOUND = "minecraft:entity.breeze.wind_burst";

    /** MobEffectInstance.onMobRemoved → MobEffect.onMobRemoved(entity, amplifier, reason). */
    public static void onMobRemoved(Target t, MobEffect effect, int amplifier, Entity.RemovalReason reason) {
        if (reason != Entity.RemovalReason.KILLED || t.level() == null) return;
        if (effect == MobEffect.WIND_CHARGED) windCharged(t);
        else if (effect == MobEffect.WEAVING) weaving(t);
        else if (effect == MobEffect.OOZING) oozing(t);
    }

    /** 플레이어의 LivingEntity.remove(reason) → triggerOnDeathMobEffects: 리스폰 때(PlayerList.respawn 의 removePlayerImmediately(KILLED)) 죽은 자리 region 에서. */
    public static void triggerOnDeath(ChunkLevel level, Player p, Entity.RemovalReason reason) {
        Target t = Target.of(level, p);
        for (MobEffectInstance e : p.vitals().activeEffects()) onMobRemoved(t, e.effect(), e.amplifier(), reason);
    }

    /** MobEffectInstance.onMobHurt → MobEffect.onMobHurt(entity, amplifier, source, amount). */
    public static void onMobHurt(Target t, MobEffect effect, int amplifier) {
        if (effect == MobEffect.INFESTED && t.level() != null) infested(t);
    }

    // ------------------------------------------------------------------ 돌풍(wind_charged)

    /** 몸 가운데에서 반경 3 + nextFloat×2 의 윈드 폭발(블록은 건드리기만, 작은·큰 gust_emitter, breeze 돌풍 소리). 원인 = 죽은 대상. */
    private static void windCharged(Target t) {
        double x = t.x(), y = t.y() + t.bbHeight() / 2.0F, z = t.z();
        float radius = 3.0F + t.random().nextFloat() * 2.0F;
        Explosion e = new Explosion(t.level(), t.entity(), x, y, z, radius, false, Explosion.BlockInteraction.TRIGGER_BLOCK)
                .withCalculator(WIND_CALC, AbstractWindCharge.GUST_EMITTER_SMALL, AbstractWindCharge.GUST_EMITTER_LARGE, BREEZE_BURST_SOUND);
        if (t.player() != null) e.withSourcePlayer(t.player());
        e.explode();
        e.finalizeExplosion(true);
    }

    // ------------------------------------------------------------------ 거미줄 짜기(weaving)

    /** 플레이어이거나 mobGriefing 이면 둘레 3×3×3 에 거미줄 2~3 개(randomBetweenInclusive(2, 3)). */
    private static void weaving(Target t) {
        if (t.player() == null && !t.level().gameRule("mobGriefing")) return;
        spawnCobwebsRandomlyAround(t.level(), t.random(), t.blockPosition());
    }

    /**
     * spawnCobwebsRandomlyAround: BlockPos.randomInCube(random, 15, center, 1) 를 하나씩 뽑아(뽑을 때마다 x·y·z 난수 셋 — 다 차면 더 뽑지 않는다)
     * 바꿀 수 있는 칸이고 아래 칸 윗면이 튼튼하면 모은다. HashSet 순서로 거미줄을 놓고 levelEvent 3018.
     */
    static void spawnCobwebsRandomlyAround(ChunkLevel level, RandomSource random, Pos center) {
        Set<VanillaPos> set = new HashSet<>();
        int max = random.nextIntBetweenInclusive(2, 3);
        for (int n = 0; n < 15; n++) {
            int px = center.x() - 1 + random.nextInt(3);
            int py = center.y() - 1 + random.nextInt(3);
            int pz = center.z() - 1 + random.nextInt(3);
            VanillaPos p = new VanillaPos(px, py, pz);
            if (set.contains(p)) continue;
            Pos pos = new Pos(px, py, pz), below = new Pos(px, py - 1, pz);
            if (BlockInfo.get().replaceable(level.getBlockState(pos)) && B.isFaceSturdy(level.getBlockState(below), level, below, Dir.UP)) {
                set.add(p);
                if (set.size() >= max) break;
            }
        }
        int cobweb = B.defaultState("minecraft:cobweb");
        for (VanillaPos p : set) {
            Pos pos = new Pos(p.x, p.y, p.z);
            level.setBlock(pos, cobweb, 3);
            level.levelEvent(null, 3018, pos, 0);
        }
    }

    /** 바닐라 Vec3i.hashCode((y + z×31)×31 + x) 를 가진 좌표 — HashSet 순회 순서를 바닐라 BlockPos 와 맞춘다. */
    private record VanillaPos(int x, int y, int z) {
        @Override public int hashCode() { return (y + z * 31) * 31 + x; }
    }

    // ------------------------------------------------------------------ 스며 나옴(oozing)

    /** 크기 2 슬라임 2 마리 — maxEntityCramming 이 1 이상이면 주변(상자 +2) 다른 슬라임 수만큼 줄인다. */
    private static void oozing(Target t) {
        int num = 2;   // ToIntFunction: 늘 2(난수 없음)
        ChunkLevel level = t.level();
        int cramming = level.gameRuleInt("maxEntityCramming");
        int n = numToSpawn(cramming, limit -> nearbySlimes(t, limit), num);
        for (int i = 0; i < n; i++) spawnSlimeOffspring(level, t.x(), t.y() + 0.5, t.z());
    }

    /** OozingMobEffect.numberOfSlimesToSpawn(cramming, nearby, toSpawn). */
    static int numToSpawn(int cramming, java.util.function.IntUnaryOperator nearby, int toSpawn) {
        return cramming < 1 ? toSpawn : Math.clamp(cramming - nearby.applyAsInt(cramming), 0, toSpawn);
    }

    /** NearbySlimes.closeTo: 상자를 2 늘린 범위의 슬라임(정확히 slime 종류, 자기 제외)을 최대 limit 마리까지 센다. */
    private static int nearbySlimes(Target t, int limit) {
        int[] count = {0};
        Entity self = t.entity();
        for (Entity e : t.level().getEntities(null, t.boundingBox().inflate(2.0), e -> e.type() == EntityType.SLIME && e != self)) {
            if (count[0] >= limit) break;
            count[0]++;
        }
        return count[0];
    }

    private static void spawnSlimeOffspring(ChunkLevel level, double x, double y, double z) {
        Slime slime = new Slime(level, level.nextEntityId());
        slime.setSize(2, true);
        slime.moveTo(x, y, z, level.random().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(slime);
    }

    // ------------------------------------------------------------------ 벌레 들끓음(infested)

    /** 맞을 때 nextFloat ≤ 0.1 이면 좀벌레 1~2 마리(randomBetweenInclusive(1, 2)) — 몸 가운데에서. */
    private static void infested(Target t) {
        if (t.random().nextFloat() > 0.1F) return;
        int n = t.random().nextIntBetweenInclusive(1, 2);
        for (int i = 0; i < n; i++) spawnSilverfish(t, t.x(), t.y() + t.bbHeight() / 2.0, t.z());
    }

    /**
     * spawnSilverfish: 방향 g = randomBetween(-π/2, π/2)(대상 난수), 속도 = deltaMovement(float)×0.3×(1, 1.5, 1) 을 y 축으로 g 만큼 돌린 것.
     * 레벨 난수로 yRot, 넣은 뒤 entity.silverfish.hurt 소리(레벨 난수 씨앗).
     */
    private static void spawnSilverfish(Target t, double x, double y, double z) {
        ChunkLevel level = t.level();
        Silverfish sf = new Silverfish(level, level.nextEntityId());
        RandomSource r = t.random();
        float g = r.nextFloat() * ((float) (Math.PI / 2) - (float) (-Math.PI / 2)) + (float) (-Math.PI / 2);
        double[] dm = t.deltaMovement();
        float vx = (float) dm[0] * 0.3F, vy = (float) dm[1] * 0.3F * 1.5F, vz = (float) dm[2] * 0.3F;
        float[] rot = rotateY(vx, vz, g);
        sf.moveTo(x, y, z, level.random().nextFloat() * 360.0F, 0.0F);
        sf.setDeltaMovement(rot[0], vy, rot[1]);
        level.addFreshEntity(sf);
        sf.playSound("minecraft:entity.silverfish.hurt", 1.0F, 1.0F);
    }

    /** JOML Vector3f.rotateY(angle)(FASTMATH 꺼짐): sin = (float) Math.sin, cos = cosFromSin(sin, angle). {x', z'}. */
    static float[] rotateY(float x, float z, float angle) {
        float sin = (float) Math.sin(angle);
        float cos = cosFromSin(sin, angle);
        return new float[] {x * cos + z * sin, -x * sin + z * cos};
    }

    /** JOML Math.cosFromSin(float, float): √(1 − sin²) 에 사분면 부호. */
    static float cosFromSin(float sin, float angle) {
        float cos = (float) Math.sqrt(1.0F - sin * sin);
        float a = angle + (float) (Math.PI * 0.5);
        float b = a - (int) (a / (float) (Math.PI * 2)) * (float) (Math.PI * 2);
        if (b < 0.0) b = (float) (Math.PI * 2) + b;
        if (b >= (float) Math.PI) return -cos;
        return cos;
    }
}
