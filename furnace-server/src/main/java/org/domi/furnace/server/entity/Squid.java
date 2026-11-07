// 바닐라 Squid: 물속을 이동 벡터(tx, ty, tz)를 따라 밀려 다닌다. 맞으면 먹물을 뿜고(30개 입자) 때린 쪽에서 도망친다. 체력 10.
// TODO(파티클): 먹물·거품 입자 패킷(난수만 같은 수 소비). 촉수·몸통 회전 애니메이션은 클라이언트 몫이라 생략.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.Mth;

public class Squid extends WaterAnimal {
    /** canBeLeashed(디컴파일 y): 오징어(cgd)는 맬 수 있다. */
    @Override
    public boolean canBeLeashed() { return true; }


    private float tentacleMovement;
    private float tentacleSpeed;
    private float speed;
    private float rotateSpeed;
    private float tx, ty, tz;

    public Squid(ChunkLevel level, int id) { this(EntityType.SQUID, level, id); }

    protected Squid(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        ((LegacyRandomSource) random).setSeed(id());
        this.tentacleSpeed = 1.0F / (random.nextFloat() + 1.0F) * 0.2F;
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new SquidRandomMovementGoal(this));
        goalSelector.addGoal(1, new SquidFleeGoal());
    }

    @Override
    protected double defaultGravity() { return 0.08; }

    @Override public String getAmbientSound() { return "entity.squid.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.squid.hurt"; }

    @Override protected String getDeathSound() { return "entity.squid.death"; }

    protected String getSquirtSound() { return "entity.squid.squirt"; }

    @Override
    protected void aiStep() {
        super.aiStep();
        tentacleMovement += tentacleSpeed;
        if (tentacleMovement > (float) (Math.PI * 2)) {
            tentacleMovement -= (float) (Math.PI * 2);
            if (random.nextInt(10) == 0) tentacleSpeed = 1.0F / (random.nextFloat() + 1.0F) * 0.2F;
            broadcastEntityEvent((byte) 19);
        }
        if (isInWaterOrBubble()) {
            if (tentacleMovement < (float) Math.PI) {
                float f = tentacleMovement / (float) Math.PI;
                if (f > 0.75) {
                    speed = 1.0F;
                    rotateSpeed = 1.0F;
                } else {
                    rotateSpeed *= 0.8F;
                }
            } else {
                speed *= 0.9F;
                rotateSpeed *= 0.99F;
            }
            setDeltaMovement(tx * speed, ty * speed, tz * speed);
            double vx = vx(), vz = vz();
            yBodyRot += (-((float) Mth.atan2(vx, vz)) * (180.0F / (float) Math.PI) - yBodyRot) * 0.1F;
            setYRot(yBodyRot);
        } else {
            double vy = vy();
            if (hasEffect(MobEffect.LEVITATION)) vy = 0.05 * (getEffect(MobEffect.LEVITATION).amplifier() + 1);
            else vy -= isNoGravity() ? 0.0 : defaultGravity();
            setDeltaMovement(0.0, vy * 0.98F, 0.0);
        }
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (super.hurtBy(source, amount, attacker) && getLastHurtByMob() != null) {
            spawnInk();
            return true;
        }
        return false;
    }

    private void spawnInk() {
        playSound(getSquirtSound(), getSoundVolume(), getVoicePitch());
        for (int i = 0; i < 30; i++) {
            random.nextFloat();
            random.nextFloat();
            random.nextFloat();   // 입자 속도: 위치 x, z 와 배율(0.3 + r × 2) — 입자를 보내지 않아도 소비 수는 같다
        }
    }

    @Override
    protected void travel(float inX, float inY, float inZ) { move(MOVER_SELF, vx(), vy(), vz()); }

    public void setMovementVector(float x, float y, float z) {
        this.tx = x;
        this.ty = y;
        this.tz = z;
    }

    public boolean hasMovementVector() { return tx != 0.0F || ty != 0.0F || tz != 0.0F; }

    static final class SquidRandomMovementGoal extends Goal {
        private final Squid squid;

        SquidRandomMovementGoal(Squid squid) { this.squid = squid; }

        @Override public boolean canUse() { return true; }

        @Override
        public void tick() {
            int i = squid.getNoActionTime();
            if (i > 100) {
                squid.setMovementVector(0.0F, 0.0F, 0.0F);
            } else if (squid.random.nextInt(reducedTickDelay(50)) == 0 || !squid.isInWater() || !squid.hasMovementVector()) {
                float f = squid.random.nextFloat() * (float) (Math.PI * 2);
                float x = Mth.cos(f) * 0.2F;
                float y = -0.1F + squid.random.nextFloat() * 0.2F;
                float z = Mth.sin(f) * 0.2F;
                squid.setMovementVector(x, y, z);
            }
        }
    }

    final class SquidFleeGoal extends Goal {
        private int fleeTicks;

        @Override
        public boolean canUse() {
            Targetable last = getLastHurtByMob();
            if (!isInWater() || last == null) return false;
            double dx = x() - last.x(), dy = y() - last.y(), dz = z() - last.z();
            return dx * dx + dy * dy + dz * dz < 100.0;
        }

        @Override public void start() { fleeTicks = 0; }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            fleeTicks++;
            Targetable last = getLastHurtByMob();
            if (last != null) {
                double vx = x() - last.x(), vy = y() - last.y(), vz = z() - last.z();
                Pos p = new Pos((int) Math.floor(x() + vx), (int) Math.floor(y() + vy), (int) Math.floor(z() + vz));
                int state = level.getBlockState(p);
                int fluid = level.getFluidState(p);
                if (Fluids.isWater(fluid) || B.isAir(state)) {
                    double d = Math.sqrt(vx * vx + vy * vy + vz * vz);
                    if (d > 0.0) {
                        // 바닐라는 vec.normalize() 결과를 버린다 — 정규화하지 않고 그대로 배율만 곱한다
                        double s = 3.0;
                        if (d > 5.0) s -= (d - 5.0) / 5.0;
                        if (s > 0.0) {
                            vx *= s;
                            vy *= s;
                            vz *= s;
                        }
                    }
                    if (B.isAir(state)) vy = 0.0;
                    setMovementVector((float) vx / 20.0F, (float) vy / 20.0F, (float) vz / 20.0F);
                }
            }
        }
    }

    /** getMovementEmission = EVENTS: 게임 이벤트만 내고 걸음·헤엄 소리는 이동으로 내지 않는다. */
    @Override
    protected MovementEmission getMovementEmission() { return MovementEmission.EVENTS; }
}
