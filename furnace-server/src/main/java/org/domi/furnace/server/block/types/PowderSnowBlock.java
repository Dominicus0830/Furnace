// 바닐라 PowderSnowBlock: 가루눈. 엔티티를 가두고(0.9, 1.5, 0.9) 불을 끈다 — 불붙은 엔티티가 들어오면(mobGriefing) 녹는다.
// 충돌 모양은 엔티티마다 다르다: 2.5 넘게 떨어지는 중이면 높이 0.9 상자, 떨어지는 블록이거나(가죽 장화를 신었거나 #powder_snow_walkable_mobs 이고
// 블록 윗면보다 위에 있으며 웅크리지 않은) 걸을 수 있는 엔티티면 꽉 찬 블록, 그 밖에는 비었다(빠진다).
// 양동이로 뜨기는 BucketPickup. 블록 동작은 그 밖에 없다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Shape;

import org.domi.furnace.server.block.BlockActor;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.FallingBlockEntity;
import org.domi.furnace.server.entity.LivingEntity;

public class PowderSnowBlock extends BlockBehavior {

    private static final Shape FALLING_COLLISION_SHAPE = new Shape(new double[] {0, 0, 0, 1, (double) 0.9F, 1});

    public PowderSnowBlock(int block) { super(block); }

    /** getCollisionShape(EntityCollisionContext): fallDistance > 2.5 → 0.9 높이, FallingBlockEntity 이거나 걸을 수 있고 위에 있고 웅크리지 않으면 블록. */
    @Override
    public Shape getEntityCollisionShape(int state, BlockLevel level, Pos pos, Entity entity) {
        if (entity != null) {
            if (entity.fallDistance > 2.5F) return FALLING_COLLISION_SHAPE;
            // isAbove(Shapes.block(), pos, false): 발밑이 블록 윗면(1.0) − 1.0E-5F 보다 위
            if (entity instanceof FallingBlockEntity
                    || canEntityWalkOnPowderSnow(entity) && entity.y() > pos.y() + 1.0 - (double) 1.0E-5F && !entity.isShiftKeyDown()) {
                return Shape.BLOCK;
            }
        }
        return Shape.EMPTY;
    }

    /** canEntityWalkOnPowderSnow: #powder_snow_walkable_mobs 이거나 생물이 가죽 장화를 신었다. */
    public static boolean canEntityWalkOnPowderSnow(Entity entity) {
        if (entity.typeInTag("minecraft:powder_snow_walkable_mobs")) return true;
        return entity instanceof LivingEntity living && B.holds(living.getItemBySlot(EquipmentSlot.FEET), "minecraft:leather_boots");
    }

    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, Entity entity) {
        if (!entity.isLiving() || B.is(entity.inBlockState(), block)) entity.makeStuckInBlock(0.9F, 1.5, 0.9F);
        entity.setIsInPowderSnow(true);
        // TODO(플레이어): 바닐라는 플레이어면 mobGriefing 이 꺼져 있어도 녹인다 — 플레이어는 엔티티 계층 밖이라 entityInside 가 오지 않는다.
        // CraftBukkit: mobGriefing 거짓이어도 EntityChangeBlockEvent(새 상태 공기)를 미리 취소한 채로 내고, 처리기가 풀면 녹인다.
        // Paper 는 막히면 return 해 아래 불 표시 끄기를 건너뛰지만, 처리기가 없을 때도 그러면 바닐라와 달라져 바닐라 흐름을 지킨다
        if (entity.isOnFire() && entity.mayInteract(level, pos)
                && B.entityChange(level, entity, pos, B.defaultState("minecraft:air"), !level.gameRule("mobGriefing"))) level.destroyBlock(pos, false);
        entity.setSharedFlagOnFire(false);
    }

    /**
     * entityInside 의 플레이어 쪽: 불붙은 플레이어는 mobGriefing 이 꺼져 있어도 가루눈을 녹인다(`|| entity instanceof Player`), 만질 수 있을 때만.
     * 끼임 속도(makeStuckInBlock)는 클라이언트가 계산한다.
     */
    public void entityInsidePlayer(BlockLevel level, Pos pos, org.domi.furnace.server.entity.Player p, boolean mayInteract) {
        p.markInPowderSnow();   // setIsInPowderSnow(true): 불붙었든 아니든, 눈을 부순 뒤에도 Entity.move 끝의 불 끄기가 본다
        if (p.isOnFire() && mayInteract && B.entityChange(level, p, pos, B.defaultState("minecraft:air"))) level.destroyBlock(pos, false);   // CraftBukkit EntityChangeBlockEvent
    }

    /**
     * fallOn: 4칸 이상 떨어진 생물만 소리(7 미만 작은, 이상 큰 낙하 소리)를 내고, super.fallOn(낙하 피해)은 부르지 않는다 — 몹도 피해가 없다.
     * 플레이어는 PlayerVitals.fallMultiplier 가 가루눈에 -1 을 돌려줘 이미 피해가 없다.
     */
    @Override
    public void fallOn(BlockLevel level, int state, Pos pos, Entity entity, float fallDistance) {
        if (fallDistance < 4.0F || !(entity instanceof LivingEntity living)) return;
        String[] sounds = living.fallSounds();
        entity.playSound(fallDistance < 7.0F ? sounds[0] : sounds[1], 1.0F, 1.0F);
    }

    /** getVisualShape: 빈 모양. */
    @Override
    public Shape getVisualShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return Shape.EMPTY; }
}
