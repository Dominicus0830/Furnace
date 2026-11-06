// 바닐라 ConduitBlockEntity(serverTick): 40틱마다 둘레 프리즈머린 틀을 세어 활성화(16개 이상)·효과(전달체의 힘)·사냥(42개면 적대 몹 공격).
// 회전·눈 애니메이션·입자는 클라이언트가 자기 블록 엔티티로 그린다. 서버는 소리·효과·공격 대상(Target)만 맡는다.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.bossbar.CustomBossEvents;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Enemy;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class ConduitBlockEntity extends BlockEntity {

    /** VALID_BLOCKS: 프리즈머린·프리즈머린 벽돌·바다 랜턴·어두운 프리즈머린. */
    private static final int[] VALID_BLOCKS = {B.id("prismarine"), B.id("prismarine_bricks"), B.id("sea_lantern"), B.id("dark_prismarine")};
    private static final Entity.Damage MAGIC = new Entity.Damage("magic", false, false, false);

    public int tickCount;
    private boolean isActive;
    private boolean isHunting;
    private final List<Pos> effectBlocks = new ArrayList<>();
    private LivingEntity destroyTarget;
    private UUID destroyTargetUUID;
    private long nextAmbientSoundActivation;

    public ConduitBlockEntity(Pos pos, int state) { super("minecraft:conduit", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "conduit"); }

    @Override public boolean ticking() { return true; }

    public boolean isActive() { return isActive; }

    public boolean isHunting() { return isHunting; }

    /** 틀로 센 블록 수(effectBlocks.size()). */
    public int frameSize() { return effectBlocks.size(); }

    public LivingEntity destroyTarget() { return destroyTarget; }

    @Override
    public void tick(BlockLevel level) {
        tickCount++;
        long time = level.gameTime();
        if (time % 40L == 0L) {
            boolean active = updateShape(level, pos, effectBlocks);
            if (active != isActive) {
                level.playSound(null, pos, active ? "minecraft:block.conduit.activate" : "minecraft:block.conduit.deactivate", "blocks", 1.0F, 1.0F);
            }
            isActive = active;
            isHunting = effectBlocks.size() >= 42;
            if (active) {
                applyEffects(level, pos, effectBlocks.size());
                updateDestroyTarget(level);
            }
        }
        if (isActive) {
            if (time % 80L == 0L) level.playSound(null, pos, "minecraft:block.conduit.ambient", "blocks", 1.0F, 1.0F);
            if (time > nextAmbientSoundActivation) {
                nextAmbientSoundActivation = time + 60L + level.random().nextInt(40);
                level.playSound(null, pos, "minecraft:block.conduit.ambient.short", "blocks", 1.0F, 1.0F);
            }
        }
    }

    /** updateShape: 둘레 3×3×3 이 모두 물이고, 5×5×5 의 세 십자 테(축 평면의 바깥 고리)에서 틀 블록이 16개 이상. */
    static boolean updateShape(BlockLevel level, Pos pos, List<Pos> positions) {
        positions.clear();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    if (!level.isWaterAt(new Pos(pos.x() + i, pos.y() + j, pos.z() + k))) return false;
                }
            }
        }
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                for (int k = -2; k <= 2; k++) {
                    int a = Math.abs(i), b = Math.abs(j), c = Math.abs(k);
                    if ((a > 1 || b > 1 || c > 1)
                            && (i == 0 && (b == 2 || c == 2) || j == 0 && (a == 2 || c == 2) || k == 0 && (a == 2 || b == 2))) {
                        Pos p = new Pos(pos.x() + i, pos.y() + j, pos.z() + k);
                        int block = B.block(level.getBlockState(p));
                        for (int v : VALID_BLOCKS) if (block == v) positions.add(p);
                    }
                }
            }
        }
        return positions.size() >= 16;
    }

    /** applyEffects: 범위 = 틀 수/7*16. 그 안(칸 거리)에서 물·비에 닿은 플레이어에게 전달체의 힘 260틱(앰비언트·입자). */
    static void applyEffects(BlockLevel level, Pos pos, int frame) {
        int range = frame / 7 * 16;
        AABB box = AABB.of(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1).inflate(range)
                .expandTowards(0.0, level.maxBuildHeight() - level.minBuildHeight(), 0.0);
        List<Player> players = new ArrayList<>();
        level.forEachPlayerIn(box, players::add);
        if (players.isEmpty()) return;
        ChunkLevel cl = level instanceof ChunkLevel c ? c : null;
        for (Player p : players) {
            Pos bp = p.blockPosition();
            long dx = bp.x() - pos.x(), dy = bp.y() - pos.y(), dz = bp.z() - pos.z();
            if (dx * dx + dy * dy + dz * dz < (long) range * range && org.domi.furnace.server.entity.Tridents.isInWaterOrRain(cl, p)) {
                p.vitals().addEffect(new MobEffectInstance(MobEffect.CONDUIT_POWER, 260, 0, true, true));
            }
        }
    }

    /** getDestroyRangeAABB: 칸 상자 ±8. */
    private AABB destroyRange() {
        return AABB.of(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1).inflate(8.0);
    }

    /**
     * updateDestroyTarget: 사냥 중(42)이 아니면 대상 없음. 저장된 UUID 가 있으면 그 몹을 찾고, 없으면 범위 안 물·비에 닿은 적대 몹 하나를
     * 레벨 난수로 고른다. 대상이 죽었거나 8칸 밖이면 놓는다. 대상이 있으면 공격 소리 + 마법 피해 4. 대상이 바뀌면 블록 갱신(클라이언트 눈이 따라간다).
     */
    private void updateDestroyTarget(BlockLevel level) {
        LivingEntity before = destroyTarget;
        ChunkLevel cl = level instanceof ChunkLevel c ? c : null;
        if (effectBlocks.size() < 42) {
            destroyTarget = null;
        } else if (destroyTarget == null && destroyTargetUUID != null) {
            destroyTarget = cl == null ? null : findDestroyTarget(cl, destroyTargetUUID);
            destroyTargetUUID = null;
        } else if (destroyTarget == null) {
            if (cl != null) {
                List<LivingEntity> list = cl.getEntitiesOfClass(LivingEntity.class, destroyRange(),
                        e -> e instanceof Enemy && e.level() == cl && e.isInWaterOrRain());
                if (!list.isEmpty()) destroyTarget = list.get(level.random().nextInt(list.size()));
            }
        } else if (!destroyTarget.isAlive() || destroyTarget.level() != level || !closer(destroyTarget.blockPosition(), 8.0)) {
            // 차이: 다른 region 으로 넘어간 대상은 이 스레드가 건드리지 않고 놓는다(바닐라는 같은 레벨이라 계속 쫓는다 — 8칸 안이라 경계 근처에서만).
            destroyTarget = null;
        }
        if (destroyTarget != null) {
            level.playSound(null, destroyTarget.x(), destroyTarget.y(), destroyTarget.z(), "minecraft:block.conduit.attack.target", "blocks", 1.0F, 1.0F);
            destroyTarget.hurt(MAGIC, 4.0F);
        }
        if (before != destroyTarget) level.sendBlockUpdated(pos);
    }

    private boolean closer(Pos p, double d) {
        long dx = p.x() - pos.x(), dy = p.y() - pos.y(), dz = p.z() - pos.z();
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    /** findDestroyTarget: 범위 안에서 그 UUID 인 살아 있는 것이 정확히 하나면 그것. */
    private LivingEntity findDestroyTarget(ChunkLevel level, UUID uuid) {
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, destroyRange(), e -> uuid.equals(e.uuid()) && e.level() == level);
        return list.size() == 1 ? list.get(0) : null;
    }

    @Override
    public void load(NbtCompound tag) {
        destroyTargetUUID = tag.tags().get("Target") instanceof int[] a && a.length == 4 ? CustomBossEvents.uuidFromInts(a) : null;
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (destroyTarget != null) tag.putIntArray("Target", CustomBossEvents.uuidToInts(destroyTarget.uuid()));
    }

    /** 청크 패킷/갱신: 바닐라 getUpdateTag = saveCustomOnly(Target). */
    @Override
    public NbtCompound updateTag() { return saveCustomOnly(); }
}
