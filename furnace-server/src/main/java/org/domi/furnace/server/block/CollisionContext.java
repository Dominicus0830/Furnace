// 바닐라 CollisionContext / EntityCollisionContext: 충돌 모양이 엔티티마다 다른 블록(비계·물·용암·가루눈)이 보는 문맥.
// EMPTY(문맥 없음)는 isAbove 가 넘긴 기본값을 그대로 돌려주고, 웅크림 아님·유체 위에 서지 못함이다.
// 엔티티 문맥은 만들 때의 발 높이(getY)와 웅크림(isDescending = isShiftKeyDown)을 붙잡는다 — 바닐라도 BlockCollisions·ClipContext 를 만들 때 한 번 붙잡는다.
// 들고 있는 아이템(isHoldingItem)은 윤곽 모양(getShape)만 쓰고 충돌 모양은 쓰지 않아 아직 담지 않는다.
// 다른 스레드에서 쓸 문맥(포털 출구 자리 찾기)은 snapshot() 으로 엔티티에서 늦게 읽던 값(가루눈: 낙하 거리·걸을 수 있음)까지 미리 떠 둔다.
package org.domi.furnace.server.block;

import org.domi.furnace.server.block.types.PowderSnowBlock;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.FallingBlockEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;

public final class CollisionContext {

    /** CollisionContext.empty(): 엔티티 없음. isAbove(…, d) = d, 웅크림 아님, 유체 위에 서지 못함, 발 높이 -Double.MAX_VALUE. */
    public static final CollisionContext EMPTY = new CollisionContext(null, false, -Double.MAX_VALUE, false, false);

    private final Rider entity;
    private final boolean descending;
    private final double entityBottom;
    // LivingEntity.canStandOnFluid(원천) — 액체 블록 충돌은 level 0(원천)일 때만 묻으므로 원천 두 가지만 미리 구해 둔다.
    private final boolean standsOnWaterSource, standsOnLavaSource;
    // 스냅샷 문맥(snapshot())만: 떠 둔 엔티티 값. 산 문맥은 entity 에서 그때 읽는다.
    private final boolean detached, walksOnPowderSnow, fallingBlock;
    private final float fallDistance;

    private CollisionContext(Rider entity, boolean descending, double entityBottom, boolean standsOnWaterSource, boolean standsOnLavaSource) {
        this(entity, descending, entityBottom, standsOnWaterSource, standsOnLavaSource, false, false, false, 0.0F);
    }

    private CollisionContext(Rider entity, boolean descending, double entityBottom, boolean standsOnWaterSource, boolean standsOnLavaSource,
                             boolean detached, boolean walksOnPowderSnow, boolean fallingBlock, float fallDistance) {
        this.entity = entity;
        this.descending = descending;
        this.entityBottom = entityBottom;
        this.standsOnWaterSource = standsOnWaterSource;
        this.standsOnLavaSource = standsOnLavaSource;
        this.detached = detached;
        this.walksOnPowderSnow = walksOnPowderSnow;
        this.fallingBlock = fallingBlock;
        this.fallDistance = fallDistance;
    }

    /**
     * 엔티티를 놓은 문맥: 엔티티에서 늦게 읽던 값(가루눈의 낙하 거리·걸을 수 있음·떨어지는 블록인가)을 지금 떠 둔다.
     * 엔티티 소유 스레드에서 만들어 다른 스레드(포털 출구 자리 찾기 — 바닐라 findFreePosition(entity) 의 getBlockCollisions(entity))로 넘길 때 쓴다.
     */
    public CollisionContext snapshot() {
        if (entity == null) return this;
        return new CollisionContext(null, descending, entityBottom, standsOnWaterSource, standsOnLavaSource,
                true, PowderSnowBlock.canEntityWalkOnPowderSnow(entity), entity instanceof FallingBlockEntity, fallDistanceOf(entity));
    }

    /**
     * 갓 만든 플레이어의 문맥(ServerPlayer 생성자 안의 adjustSpawnLocation — Player 생성자가 이미 공유 스폰 (x+0.5, y+1, z+0.5) 로 옮겼다):
     * 발 높이 bottom, 웅크림 아님, 유체 위에 서지 못함, 낙하 거리 0, 장비 없음(가루눈을 걷지 못함).
     */
    public static CollisionContext newPlayer(double bottom) {
        return new CollisionContext(null, false, bottom, false, false, true, false, false, 0.0F);
    }

    private static float fallDistanceOf(Rider who) {
        return who instanceof Entity e ? e.fallDistance : ((Player) who).vitals().fallDistance();
    }

    /** CollisionContext.of(entity): 지금 발 높이로. null 이면 EMPTY(바닐라 BlockCollisions 의 entity == null 분기). */
    public static CollisionContext of(Rider entity) {
        return entity == null ? EMPTY : of(entity, entity.y());
    }

    /**
     * 발 높이를 따로 준 엔티티 문맥 — 바닐라가 엔티티를 이미 옮긴 뒤 문맥을 만드는 곳(플레이어 이동 검증의 noCollision·getCollisions)을
     * Furnace 는 옮기기 전에 계산하므로 옮긴 자리의 높이를 넘긴다.
     */
    public static CollisionContext of(Rider entity, double bottom) {
        if (entity == null) return EMPTY;
        // 바닐라 LivingEntity.canStandOnFluid 는 기본 false, 덮어쓰는 것은 Strider 뿐(1.21.1). 플레이어·생물 아닌 엔티티는 언제나 false.
        boolean water = false, lava = false;
        if (entity instanceof Mob m) {
            water = m.canStandOnFluid(Fluids.WATER_SOURCE);
            lava = m.canStandOnFluid(Fluids.LAVA_SOURCE);
        }
        return new CollisionContext(entity, entity.isShiftKeyDown(), bottom, water, lava);
    }

    /**
     * 블록 행위자(설치하는 플레이어 등)의 문맥: 행위자 본체가 플레이어·엔티티면 그 문맥, 없으면 EMPTY(바닐라 BlockItem.canPlace 의 player == null).
     * 본체가 없는 행위자(시험용)는 발 높이와 웅크림(isSecondaryUseActive — 플레이어는 isShiftKeyDown)만으로 만든다.
     */
    public static CollisionContext of(BlockActor actor) {
        if (actor == null) return EMPTY;
        if (actor.handle() instanceof Rider r) return of(r);
        return new CollisionContext(null, actor.isSecondaryUseActive(), actor.y(), false, false);
    }

    /**
     * Rider 가 아닌 엔티티(엔더 드래곤 부위)의 문맥: 발 높이만 있고 웅크림·유체 위 서기는 없다(부위는 생물이 아니고 웅크리지 않는다).
     * 가루눈은 이 문맥을 엔티티 없음과 같게 본다 — 부위는 떨어지지 않고 걸을 수 있는 종류도 아니다.
     */
    public static CollisionContext atBottom(double bottom) {
        return new CollisionContext(null, false, bottom, false, false);
    }

    /**
     * 엔티티 없이 값만으로 만든 문맥 — 바닐라 EntityCollisionContext 의 5인자 생성자(descending, entityBottom, heldItem, canStandOnFluid, entity=null).
     * 바닐라 대조 시험이 바닐라와 같은 문맥 값을 넣을 때 쓴다.
     */
    public static CollisionContext of(double bottom, boolean descending, boolean standsOnWaterSource, boolean standsOnLavaSource) {
        return new CollisionContext(null, descending, bottom, standsOnWaterSource, standsOnLavaSource);
    }

    /** 문맥 엔티티(EMPTY·본체 없는 행위자·스냅샷이면 null). 바닐라 EntityCollisionContext.getEntity. */
    public Rider entity() { return entity; }

    /** 엔티티 문맥인가(산 엔티티이거나 엔티티를 떠 둔 스냅샷) — 가루눈이 getEntity() instanceof EntityCollisionContext 로 가르는 것. */
    public boolean hasEntity() { return entity != null || detached; }

    /** 문맥 엔티티의 낙하 거리(엔티티 문맥만). */
    public float fallDistance() { return detached ? fallDistance : fallDistanceOf(entity); }

    /** PowderSnowBlock.canEntityWalkOnPowderSnow(문맥 엔티티)(엔티티 문맥만). */
    public boolean canWalkOnPowderSnow() { return detached ? walksOnPowderSnow : PowderSnowBlock.canEntityWalkOnPowderSnow(entity); }

    /** 문맥 엔티티가 FallingBlockEntity 인가(엔티티 문맥만). */
    public boolean isFallingBlock() { return detached ? fallingBlock : entity instanceof FallingBlockEntity; }

    /** isDescending(): 엔티티가 웅크리고 있다(비계를 타고 내려가기, 가루눈 빠지기). */
    public boolean isDescending() { return descending; }

    /**
     * isAbove(shape, pos, defaultValue): 발 높이 > pos.y + 모양의 최대 Y − 1.0E-5F. EMPTY 는 defaultValue.
     * shapeMaxY 는 모양.max(Y) 를 넘긴다(블록 1.0, 액체 원천 0.5, 비계 아래칸 BELOW_BLOCK 0.0).
     */
    public boolean isAbove(double shapeMaxY, Pos pos, boolean defaultValue) {
        if (this == EMPTY) return defaultValue;
        return entityBottom > pos.y() + shapeMaxY - (double) 1.0E-5F;
    }

    /**
     * canStandOnFluid(위 칸 유체, 이 칸 유체): 엔티티가 이 유체 위에 설 수 있고 위 칸 유체가 같은 종류(물·흐르는 물 / 용암·흐르는 용암)가 아니다.
     * fluid 는 원천이어야 한다(액체 블록 충돌이 level 0 에서만 묻는다).
     */
    public boolean canStandOnFluid(int aboveFluid, int fluid) {
        boolean stands = Fluids.isWater(fluid) ? standsOnWaterSource : Fluids.isLava(fluid) && standsOnLavaSource;
        return stands && !sameFluidType(aboveFluid, fluid);
    }

    /** 원천 유체 어느 것에라도 설 수 있는가 — 충돌 수집이 이 문맥에서 액체 칸을 통째로 건너뛰어도 되는지(아니면 모양이 늘 비었다). */
    public boolean standsOnAnyFluid() { return standsOnWaterSource || standsOnLavaSource; }

    /** Fluid.isSame: 물 원천·흐름끼리, 용암 원천·흐름끼리 같다. 빈 유체는 어느 것과도 다르다. */
    private static boolean sameFluidType(int a, int b) {
        return Fluids.isWater(a) && Fluids.isWater(b) || Fluids.isLava(a) && Fluids.isLava(b);
    }
}
