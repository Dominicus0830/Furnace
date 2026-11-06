// 바닐라 RamTarget: 염소가 RAM_TARGET 쪽으로 돌진해, 닿은 생물에게 피해·밀치기를 주거나 뿔이 부러질 블록에 부딪힌다.
// TODO(인챈트): 돌진 피해 뒤 후처리 효과, 방패 막기 판정은 아직 없다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Goat;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class RamTarget extends Behavior<Goat> {
    public static final int TIME_OUT_DURATION = 200;
    public static final float RAM_SPEED_FORCE_FACTOR = 1.65F;
    private final Function<Goat, UniformInt> getTimeBetweenRams;
    private final TargetingConditions ramTargeting;
    private final float speed;
    private final ToDoubleFunction<Goat> getKnockbackForce;
    private double[] ramDirection;
    private final Function<Goat, String> getImpactSound;
    private final Function<Goat, String> getHornBreakSound;

    public RamTarget(Function<Goat, UniformInt> timeBetweenRams, TargetingConditions ramTargeting, float speed, ToDoubleFunction<Goat> knockbackForce, Function<Goat, String> impactSound,
            Function<Goat, String> hornBreakSound) {
        super(Map.of(MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_PRESENT), TIME_OUT_DURATION);
        this.getTimeBetweenRams = timeBetweenRams;
        this.ramTargeting = ramTargeting;
        this.speed = speed;
        this.getKnockbackForce = knockbackForce;
        this.getImpactSound = impactSound;
        this.getHornBreakSound = hornBreakSound;
        this.ramDirection = new double[] {0.0, 0.0, 0.0};
    }

    @Override protected boolean checkExtraStartConditions(ChunkLevel level, Goat goat) { return goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET); }

    @Override protected boolean canStillUse(ChunkLevel level, Goat goat, long gameTime) { return goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET); }

    @Override
    protected void start(ChunkLevel level, Goat goat, long gameTime) {
        Pos pos = goat.blockPosition();
        double[] target = goat.getBrain().getMemory(MemoryModuleType.RAM_TARGET).get();
        double dx = pos.x() - target[0], dz = pos.z() - target[2];
        double len = Math.sqrt(dx * dx + dz * dz);
        ramDirection = len < 1.0E-5F ? new double[] {0.0, 0.0, 0.0} : new double[] {dx / len, 0.0, dz / len};
        goat.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, speed, 0));
    }

    @Override
    protected void tick(ChunkLevel level, Goat goat, long gameTime) {
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, goat.boundingBox(), e -> e != goat && ramTargeting.test(goat, e));
        var brain = goat.getBrain();
        if (!list.isEmpty()) {
            LivingEntity target = list.get(0);
            var damage = new Entity.Damage("mob_attack_no_aggro", false, false, false);
            target.hurtByVia(damage, (float) goat.attributeValue(Attribute.ATTACK_DAMAGE), goat, goat);
            var speedEffect = goat.getEffect(MobEffect.SPEED);
            var slowEffect = goat.getEffect(MobEffect.SLOWNESS);
            int i = speedEffect != null ? speedEffect.amplifier() + 1 : 0;
            int j = slowEffect != null ? slowEffect.amplifier() + 1 : 0;
            float f = 0.25F * (i - j);
            float f1 = Math.clamp(goat.getSpeed() * RAM_SPEED_FORCE_FACTOR, 0.2F, 3.0F) + f;
            target.knockback(f1 * getKnockbackForce.applyAsDouble(goat), ramDirection[0], ramDirection[2]);
            finishRam(level, goat);
            level.playSound(null, goat, getImpactSound.apply(goat), "neutral", 1.0F, 1.0F);
        } else if (hasRammedHornBreakingBlock(level, goat)) {
            level.playSound(null, goat, getImpactSound.apply(goat), "neutral", 1.0F, 1.0F);
            boolean dropped = goat.dropHorn();
            if (dropped) level.playSound(null, goat, getHornBreakSound.apply(goat), "neutral", 1.0F, 1.0F);
            finishRam(level, goat);
        } else {
            Optional<WalkTarget> walk = brain.getMemory(MemoryModuleType.WALK_TARGET);
            Optional<double[]> ram = brain.getMemory(MemoryModuleType.RAM_TARGET);
            boolean closeToTarget = walk.isEmpty() || ram.isEmpty() || closeTo(walk.get().getTarget().currentPosition(), ram.get(), 0.25);
            if (closeToTarget) finishRam(level, goat);
        }
    }

    private static boolean closeTo(double[] a, double[] b, double d) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    private boolean hasRammedHornBreakingBlock(ChunkLevel level, Goat goat) {
        double hl = Math.sqrt(goat.vx() * goat.vx() + goat.vz() * goat.vz());
        double hx = hl < 1.0E-5F ? 0.0 : goat.vx() / hl, hz = hl < 1.0E-5F ? 0.0 : goat.vz() / hl;
        Pos pos = new Pos((int) Math.floor(goat.x() + hx), (int) Math.floor(goat.y()), (int) Math.floor(goat.z() + hz));
        return B.isTag(level.getBlockState(pos), "minecraft:snaps_goat_horn") || B.isTag(level.getBlockState(pos.above()), "minecraft:snaps_goat_horn");
    }

    protected void finishRam(ChunkLevel level, Goat goat) {
        goat.broadcastEntityEvent((byte) 59);
        goat.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, getTimeBetweenRams.apply(goat).sample(level.random()));
        goat.getBrain().eraseMemory(MemoryModuleType.RAM_TARGET);
    }
}
