// 바닐라 BellBlockEntity(1.21.1): 흔들림 틱, 종소리를 들은 몹 기억(HEARD_BELL_TIME), 습격자 공명(발광 60틱).
// 클라이언트는 이 블록 엔티티가 있어야 종 몸통을 그린다(BellRenderer) — 청크 패킷에 반드시 실린다.
package org.domi.furnace.server.block.entity;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public class BellBlockEntity extends BlockEntity {

    private static final int BELL = B.id("bell");

    private long lastRingTimestamp;
    public int ticks;
    public boolean shaking;
    public int clickDirection = -1;
    private List<LivingEntity> nearbyEntities;
    private boolean resonating;
    private int resonationTicks;
    /** updateEntities 가 쓰는 레벨(triggerEvent 에는 레벨이 넘어오지 않는다 — tick·onHit 때 받아 둔다). */
    private BlockLevel level;

    public BellBlockEntity(Pos pos, int state) { super("minecraft:bell", pos, state); }

    @Override public boolean isValid(int state) { return B.block(state) == BELL; }

    @Override public boolean ticking() { return true; }

    public void level(BlockLevel level) { this.level = level; }

    /** triggerEvent(1, dir): 주변 몹 갱신 + 흔들림 시작. */
    @Override
    public boolean triggerEvent(int type, int data) {
        if (type == 1) {
            updateEntities();
            resonationTicks = 0;
            clickDirection = data;
            ticks = 0;
            shaking = true;
            return true;
        }
        return super.triggerEvent(type, data);
    }

    /** serverTick → tick(..., makeRaidersGlow). */
    @Override
    public void tick(BlockLevel level) {
        this.level = level;
        if (shaking) ticks++;
        if (ticks >= 50) {
            shaking = false;
            ticks = 0;
        }
        if (ticks >= 5 && resonationTicks == 0 && areRaidersNearby(pos, nearbyEntities)) {
            resonating = true;
            level.playSound(null, pos, "minecraft:block.bell.resonate", "blocks", 1.0F, 1.0F);
        }
        if (resonating) {
            if (resonationTicks < 40) {
                resonationTicks++;
            } else {
                makeRaidersGlow(pos, nearbyEntities);
                resonating = false;
            }
        }
    }

    /** onHit(dir): 흔들리는 중이면 처음부터, 블록 이벤트 1(방향). */
    public void onHit(BlockLevel level, int dir) {
        this.level = level;
        clickDirection = dir;
        if (shaking) ticks = 0;
        else shaking = true;
        level.blockEvent(pos, B.block(level.getBlockState(pos)), 1, dir);
    }

    /** updateEntities: 60틱마다(또는 처음) 48칸 상자 안 살아 있는 것 목록을 다시 모으고, 32 안이면 HEARD_BELL_TIME. */
    private void updateEntities() {
        if (!(level instanceof ChunkLevel cl)) return;
        long now = cl.gameTime();
        if (now > lastRingTimestamp + 60L || nearbyEntities == null) {
            lastRingTimestamp = now;
            AABB box = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1).inflate(48.0, 48.0, 48.0);
            nearbyEntities = cl.getEntitiesOfClass(LivingEntity.class, box, e -> true);
        }
        for (LivingEntity e : nearbyEntities) {
            if (e.isAlive() && !e.isRemoved() && owned(e) && closer(pos, e, 32.0) && e instanceof Mob m && m.getBrain() != null) {
                m.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
            }
        }
    }

    private static boolean closer(Pos pos, LivingEntity e, double d) {
        double dx = pos.x() + 0.5 - e.x(), dy = pos.y() + 0.5 - e.y(), dz = pos.z() + 0.5 - e.z();
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    /**
     * 캐시(60틱) 사이에 다른 region 으로 넘어간 엔티티는 다른 스레드 소유 — 건드리지 않는다.
     * 차이: 바닐라는 같은 레벨이라 그대로 효과·기억을 준다. 넘어간 몹은 이 종소리를 못 듣는다(가정: 경계 근처에서만 드물게 생긴다).
     */
    private boolean owned(LivingEntity e) { return e.level() == level; }

    private boolean areRaidersNearby(Pos pos, List<LivingEntity> list) {
        if (list == null) return false;
        for (LivingEntity e : list) {
            if (e.isAlive() && !e.isRemoved() && owned(e) && closer(pos, e, 32.0) && e.typeInTag("minecraft:raiders")) return true;
        }
        return false;
    }

    private void makeRaidersGlow(Pos pos, List<LivingEntity> list) {
        if (list == null) return;
        for (LivingEntity e : list) {
            if (e.isAlive() && !e.isRemoved() && owned(e) && closer(pos, e, 48.0) && e.typeInTag("minecraft:raiders")) {
                e.addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.GLOWING, 60));
            }
        }
    }
}
