// 바닐라 Entity 의 서버 쪽 바탕: 위치·속도·회전·몸통 상자, move(블록 충돌·디딤 블록·낙하·칸 안 블록 동작·속도 계수),
// baseTick(유체 높이와 흐름에 밀리기·눈 높이 유체·불·용암·세계 밑), 엔티티 데이터(0..7), 저장 공통 부분.
// 엔티티는 자기 청크를 가진 region 의 레벨(ChunkLevel)에 들어 있고 그 region 스레드만 만진다.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.FluidTicker;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract non-sealed class Entity implements Rider {

    /** 엔티티 데이터 번호(Entity.DATA_*): 0 공유 플래그, 1 공기, 2 이름, 3 이름 보임, 4 조용함, 5 중력 없음, 6 자세, 7 얼음 틱. */
    public static final int DATA_SHARED_FLAGS = 0, DATA_AIR_SUPPLY = 1, DATA_CUSTOM_NAME = 2, DATA_CUSTOM_NAME_VISIBLE = 3, DATA_SILENT = 4,
            DATA_NO_GRAVITY = 5, DATA_POSE = 6, DATA_TICKS_FROZEN = 7;
    private static final int FLAG_ONFIRE = 0;
    private static final int FIRE_IMMUNE_TICKS = 1;   // getFireImmuneTicks

    /** 제거 이유(Entity.RemovalReason): 저장 안 함(죽음·버림)·저장(내려감)·차원 이동. */
    public enum RemovalReason {
        KILLED(true, false), DISCARDED(true, false), UNLOADED_TO_CHUNK(false, true), UNLOADED_WITH_PLAYER(false, false), CHANGED_DIMENSION(false, false);

        final boolean destroy, save;

        RemovalReason(boolean destroy, boolean save) { this.destroy = destroy; this.save = save; }

        public boolean shouldDestroy() { return destroy; }

        public boolean shouldSave() { return save; }
    }

    /** 엔티티가 받는 피해의 종류(바닐라 DamageSource 중 엔티티 동작이 보는 성질만). */
    public record Damage(String type, boolean fire, boolean explosion, boolean bypassesInvulnerability, ItemStack weapon) {
        public Damage(String type, boolean fire, boolean explosion, boolean bypassesInvulnerability) {
            this(type, fire, explosion, bypassesInvulnerability, null);
        }

        /** DamageSource.getWeaponItem: 직접 때린 쪽의 주 손 무기(관통 등 방어력 보정이 본다). */
        public Damage withWeapon(ItemStack w) { return new Damage(type, fire, explosion, bypassesInvulnerability, w); }

        public static final Damage IN_FIRE = new Damage("in_fire", true, false, false);
        public static final Damage ON_FIRE = new Damage("on_fire", true, false, false);
        public static final Damage LAVA = new Damage("lava", true, false, false);
        public static final Damage HOT_FLOOR = new Damage("hot_floor", true, false, false);
        public static final Damage CRAMMING = new Damage("cramming", false, false, false);
        public static final Damage CACTUS = new Damage("cactus", false, false, false);
        public static final Damage WITHER = new Damage("wither", false, false, false);
        public static final Damage SWEET_BERRY_BUSH = new Damage("sweet_berry_bush", false, false, false);
        public static final Damage EXPLOSION = new Damage("explosion", false, true, false);
        public static final Damage OUT_OF_WORLD = new Damage("out_of_world", false, false, true);
        public static final Damage GENERIC_KILL = new Damage("generic_kill", false, false, true);

        /** 피해 유형 하나에서(성질은 damage_type 태그). */
        public static Damage of(org.domi.furnace.server.entity.damage.DamageType t) {
            return new Damage(t.path(), t.isFire(), t.isExplosion(), t.bypassesInvulnerability());
        }
    }

    protected final EntityType type;
    private final int id;
    protected UUID uuid;
    protected ChunkLevel level;
    protected final RandomSource random = RandomSource.create();
    protected final SynchedData entityData = new SynchedData();

    private double x, y, z;
    private int blockX, blockY, blockZ;
    private long sectionKey;
    public double xo, yo, zo, xOld, yOld, zOld;
    private double vx, vy, vz;
    private float yRot, xRot;
    public float yRotO, xRotO;
    private AABB bb;
    private boolean onGround;
    public boolean horizontalCollision, verticalCollision, verticalCollisionBelow, minorHorizontalCollision;
    /** hurtMarked: 다음 sendChanges 에서 속도를 바로 보낸다. hasImpulse: 이번 갱신 주기를 앞당긴다. */
    public boolean hurtMarked, hasImpulse;
    public boolean noPhysics;
    private double stuckX, stuckY, stuckZ;
    public float fallDistance;
    protected boolean wasTouchingWater, wasEyeInWater, firstTick = true;
    private double waterHeight, lavaHeight;
    private boolean eyeInWater, eyeInLava;
    private int remainingFireTicks = -FIRE_IMMUNE_TICKS;
    public int tickCount;
    private boolean isInPowderSnow, wasInPowderSnow;
    private boolean invulnerable;
    private RemovalReason removalReason;
    /** mainSupportingBlockPos 와 onGroundNoBlocks(바닐라 checkSupportingBlock). */
    private Pos supportingBlock;
    private boolean onGroundNoBlocks;
    private int inBlockState = -1;
    /** 추적기(region 이 레벨에 넣을 때 만든다). */
    public EntityTracker tracker;
    // 탑승(바닐라 Entity.vehicle/passengers/boardingCooldown). 승객이 없으면 공유 빈 목록이라 틱 비용이 없다.
    private Entity vehicle;
    private java.util.List<Rider> passengers = java.util.List.of();
    private int boardingCooldown;

    protected Entity(EntityType type, ChunkLevel level, int id) {
        this.type = type;
        this.level = level;
        this.id = id;
        this.uuid = randomUuid(random);
        entityData.define(DATA_SHARED_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_AIR_SUPPLY, SetEntityData.SERIALIZER_INT, 300);
        entityData.define(DATA_CUSTOM_NAME, SetEntityData.SERIALIZER_OPTIONAL_COMPONENT, null);   // Optional<Component>, null = 없음
        entityData.define(DATA_CUSTOM_NAME_VISIBLE, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_SILENT, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_NO_GRAVITY, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_POSE, SetEntityData.SERIALIZER_POSE, 0);
        entityData.define(DATA_TICKS_FROZEN, SetEntityData.SERIALIZER_INT, 0);
        defineSynchedData();
        this.dimW = type.width();
        this.dimH = type.height();
        this.dimEye = type.eyeHeight();
        this.bb = makeBb(0, 0, 0);
        setPos(0, 0, 0);
    }

    /** Mth.createInsecureUUID(random). */
    static UUID randomUuid(RandomSource r) {
        long msb = r.nextLong() & -61441L | 16384L;
        long lsb = r.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(msb, lsb);
    }

    protected void defineSynchedData() {}

    // ------------------------------------------------------------------ 기본

    public EntityType type() { return type; }

    public int id() { return id; }

    public UUID uuid() { return uuid; }

    /** setUUID(/data 가 load 뒤 원래 UUID 를 되돌릴 때). */
    public void setUuid(UUID id) { this.uuid = id; }

    public ChunkLevel level() { return level; }

    public void setLevel(ChunkLevel level) { this.level = level; }

    public SynchedData entityData() { return entityData; }

    public RandomSource random() { return random; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public int blockX() { return blockX; }

    public int blockY() { return blockY; }

    public int blockZ() { return blockZ; }

    public Pos blockPosition() { return new Pos(blockX, blockY, blockZ); }

    public int chunkX() { return blockX >> 4; }

    public int chunkZ() { return blockZ >> 4; }

    /** SectionPos.asLong(블록 위치 >> 4). */
    public long sectionKey() { return sectionKey; }

    public static long sectionKey(int sx, int sy, int sz) {
        return ((long) sx & 4194303L) << 42 | ((long) sy & 1048575L) | ((long) sz & 4194303L) << 20;
    }

    public double vx() { return vx; }

    public double vy() { return vy; }

    public double vz() { return vz; }

    public void setDeltaMovement(double vx, double vy, double vz) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }

    public void addDeltaMovement(double dx, double dy, double dz) { setDeltaMovement(vx + dx, vy + dy, vz + dz); }

    public float yRot() { return yRot; }

    public float xRot() { return xRot; }

    public void setYRot(float v) { if (Float.isFinite(v)) yRot = v; }

    public void setXRot(float v) { if (Float.isFinite(v)) xRot = v; }

    /** 머리 방향(살아 있는 것만 따로 — 나머지는 0). */
    public float yHeadRot() { return 0.0F; }

    public AABB boundingBox() { return bb; }

    public boolean onGround() { return onGround; }

    public void setOnGround(boolean v) {
        onGround = v;
        checkSupportingBlock(v, null);
    }

    protected float dimW, dimH, dimEye;

    /** 지금 위치로 경계 상자를 다시 만든다(상자 모양이 상태에 따라 바뀌는 몹용). */
    protected final void refreshBoundingBox() { bb = makeBb(x, y, z); }

    protected AABB makeBb(double px, double py, double pz) {
        double hw = dimW / 2.0F;
        return new AABB(px - hw, py, pz - hw, px + hw, py + dimH, pz + hw);
    }

    /** refreshDimensions: 몸 크기를 바꾸고(아기 등) 지금 위치 기준으로 상자를 다시 만든다. */
    protected void setDimensions(float w, float h, float eye) {
        dimW = w;
        dimH = h;
        dimEye = eye;
        bb = makeBb(x, y, z);
    }

    public float bbWidth() { return dimW; }

    public float bbHeight() { return dimH; }

    public double eyeY() { return y + dimEye; }

    public boolean isRemoved() { return removalReason != null; }

    public RemovalReason removalReason() { return removalReason; }

    public boolean isAlive() { return !isRemoved(); }

    public boolean isInvulnerable() { return invulnerable; }

    public void setInvulnerable(boolean v) { invulnerable = v; }

    public boolean isSilent() { return (Boolean) entityData.get(DATA_SILENT); }

    public void setSilent(boolean v) { entityData.set(DATA_SILENT, v); }

    public boolean isNoGravity() { return (Boolean) entityData.get(DATA_NO_GRAVITY); }

    public void setNoGravity(boolean v) { entityData.set(DATA_NO_GRAVITY, v); }

    public boolean fireImmune() { return type.fireImmune(); }

    public boolean isPushedByFluid() { return true; }

    /** 그 엔티티가 움직임으로 블록 동작(압력판·인계선 등)을 일으키지 않는가. */
    public boolean isIgnoringBlockTriggers() { return false; }

    public boolean isSpectator() { return false; }

    public boolean isSteppingCarefully() { return false; }

    public boolean isSuppressingBounce() { return false; }

    // ------------------------------------------------------------------ 위치

    /** setPosRaw: 바뀌면 블록 위치·구역을 갱신하고 레벨에 구역 이동을 알린다. */
    public final void setPosRaw(double nx, double ny, double nz) {
        if (x == nx && y == ny && z == nz && bb != null) return;
        x = nx;
        y = ny;
        z = nz;
        int bx = floor(nx), by = floor(ny), bz = floor(nz);
        if (bx != blockX || by != blockY || bz != blockZ) {
            blockX = bx;
            blockY = by;
            blockZ = bz;
            inBlockState = -1;
        }
        long key = sectionKey(bx >> 4, by >> 4, bz >> 4);
        if (key != sectionKey) {
            long old = sectionKey;
            sectionKey = key;
            if (level != null && tracker != null) level.entitySectionChanged(this, old);
        }
    }

    public void setPos(double nx, double ny, double nz) {
        setPosRaw(nx, ny, nz);
        bb = makeBb(nx, ny, nz);
    }

    /** moveTo(x, y, z, yRot, xRot): 이전 위치까지 맞춘다. */
    public void moveTo(double nx, double ny, double nz, float yr, float xr) {
        setPosRaw(nx, ny, nz);
        setYRot(yr);
        setXRot(xr);
        setOldPosAndRot();
        setPos(nx, ny, nz);
    }

    public final void setOldPosAndRot() {
        xo = xOld = x;
        yo = yOld = y;
        zo = zOld = z;
        yRotO = yRot;
        xRotO = xRot;
    }

    public void teleportTo(double nx, double ny, double nz) { moveTo(nx, ny, nz, yRot, xRot); }

    /**
     * Entity.teleportTo(level, x, y, z, 상대 없음, yRot, xRot) 의 같은 레벨 경로: 위아래 각을 ±90 으로 자르고 옮긴 뒤 승객 자리를
     * 다시 맞춘다(reapplyPosition). 머리 각도는 호출한 쪽(LivingEntity)이 맞춘다. 주인 region 스레드에서만 부른다.
     */
    public void teleportSameLevel(double nx, double ny, double nz, float yr, float xr) {
        moveTo(nx, ny, nz, yr, Math.clamp(xr, -90.0F, 90.0F));
        reapplyPosition();
    }

    /** Entity.reapplyPosition: 나와 내 승객 사슬 전체의 승객을 탈것 위치에 다시 놓는다. */
    public void reapplyPosition() {
        for (Rider r : passengers) {
            positionRider(r);
            if (r instanceof Entity e) e.reapplyPosition();
        }
    }

    /** absMoveTo(x, y, z, yRot, xRot): x·z 를 ±3e7 로 자르고 이전 위치까지 맞춘 뒤 회전(yRot % 360, xRot 은 ±90 으로 잘라 % 360). */
    public void absMoveTo(double nx, double ny, double nz, float yr, float xr) {
        double cx = Math.clamp(nx, -3.0E7, 3.0E7), cz = Math.clamp(nz, -3.0E7, 3.0E7);
        xo = cx;
        yo = ny;
        zo = cz;
        setPos(cx, ny, cz);
        setYRot(yr % 360.0F);
        setXRot(Math.clamp(xr, -90.0F, 90.0F) % 360.0F);
        yRotO = yRot;
        xRotO = xRot;
    }

    // ------------------------------------------------------------------ 틱

    public void tick() { baseTick(); }

    public void baseTick() {
        inBlockState = -1;
        if (vehicle != null && vehicle.isRemoved()) stopRiding();
        if (boardingCooldown > 0) boardingCooldown--;
        xRotO = xRot;
        yRotO = yRot;
        handlePortal();
        wasInPowderSnow = isInPowderSnow;
        isInPowderSnow = false;
        updateInWaterStateAndDoFluidPushing();
        updateFluidOnEyes();
        updateSwimming();
        if (remainingFireTicks > 0) {
            if (fireImmune()) {
                setRemainingFireTicks(remainingFireTicks - 4);
                if (remainingFireTicks < 0) clearFire();
            } else {
                if (remainingFireTicks % 20 == 0 && !isInLava()) hurt(Damage.ON_FIRE, 1.0F);
                setRemainingFireTicks(remainingFireTicks - 1);
            }
            if ((Integer) entityData.get(DATA_TICKS_FROZEN) > 0) {
                entityData.set(DATA_TICKS_FROZEN, 0);
                level.levelEvent(null, 1009, blockPosition(), 1);
            }
        }
        if (isInLava()) {
            lavaHurt();
            fallDistance *= 0.5F;
        }
        checkBelowWorld();
        setSharedFlagOnFire(remainingFireTicks > 0);
        firstTick = false;
    }

    // ------------------------------------------------------------------ 포털

    /** 포털 재사용 대기(바닐라 portalCooldown). */
    public int portalCooldown;
    protected PortalProcessor portalProcess;

    public PortalProcessor portalProcess() { return portalProcess; }

    /** getDimensionChangingDelay: 넘어간 뒤 포털을 다시 못 쓰는 틱. */
    public int getDimensionChangingDelay() { return 300; }

    public void setPortalCooldown() { portalCooldown = getDimensionChangingDelay(); }

    public boolean isOnPortalCooldown() { return portalCooldown > 0; }

    /** canUsePortal(allowPassengers): 탄 것이 아니고(허용 시 예외) 살아 있다. */
    public boolean canUsePortal(boolean allowPassengers) { return (allowPassengers || vehicle == null) && isAlive(); }

    /** canChangeDimensions(old, new): 기본은 언제나. */
    public boolean canChangeDimensions(ChunkLevel from, ChunkLevel to) { return canChangeDimensions(from.dimension(), to.dimension()); }

    /** 차원 이름판(목적 레벨이 다른 스레드 소유일 때). */
    public boolean canChangeDimensions(String from, String to) { return true; }

    /** setAsInsidePortal: 대기 중이면 대기만 다시 채우고, 같은 포털이면 들어온 위치·이번 틱 표시를 갱신, 아니면 새로 시작. */
    public void setAsInsidePortal(Portal portal, Pos pos) {
        if (isOnPortalCooldown()) {
            setPortalCooldown();
        } else if (portalProcess != null && portalProcess.isSamePortal(portal)) {
            portalProcess.updateEntryPosition(pos);
            portalProcess.setAsInsidePortalThisTick(true);
        } else {
            portalProcess = new PortalProcessor(portal, pos);
        }
    }

    /**
     * handlePortal: 대기를 줄이고, 시간이 차면 대기를 걸고 목적지로 넘어간다. 오래 벗어나 있으면 잊는다.
     * 서버(다른 차원이 다른 region 스레드 소유)에서는 원천 쪽 계획만 여기서 만들고 목적 차원 스레드로 넘긴다(travelAcross).
     */
    protected void handlePortal() {
        if (level == null) return;
        if (portalCooldown > 0) portalCooldown--;
        if (portalProcess == null) return;
        if (portalProcess.processPortalTeleportation(level, this, canUsePortal(false))) {
            setPortalCooldown();
            var travel = level.dimensionTravel();
            if (travel != null) {
                PortalTravel plan = portalProcess.prepareTravel(level, Traveler.of(this));
                if (plan != null && !plan.targetDimension().equals(level.dimension()) && travel.isLevelEnabled(plan.targetDimension())
                        && canChangeDimensions(level.dimension(), plan.targetDimension())) {
                    travelAcross(plan, travel);
                }
                return;
            }
            DimensionTransition t = portalProcess.getPortalDestination(level, this);
            if (t != null) {
                ChunkLevel target = t.newLevel();
                if (target == level || target.dimension().equals(level.dimension()) || canChangeDimensions(level, target)) changeDimension(t);
            }
        } else if (portalProcess.hasExpired()) {
            portalProcess = null;
        }
    }

    /**
     * 다른 스레드 소유 차원으로: 승객을 내리고 저장 데이터를 떠서 이쪽은 CHANGED_DIMENSION 으로 뺀 뒤, 목적 region 스레드에서 같은 종류를
     * 새로 만들어 넣는다(바닐라 changeDimension 의 create + restoreFrom + addDuringTeleport). 목적지가 없으면 원천에 되돌린다.
     */
    private void travelAcross(PortalTravel plan, org.domi.furnace.server.block.LevelServices.DimensionTravel travel) {
        moveAcross(plan, travel, (e, t) -> {
            e.moveTo(t.x(), t.y(), t.z(), t.yRot(), e.xRot());
            e.setDeltaMovement(t.vx(), t.vy(), t.vz());
        }, null);
    }

    /**
     * 명령 텔레포트의 다른 레벨 경로(바닐라 Entity.teleportTo(ServerLevel, …) 의 level != this.level 쪽): 승객을 내리고(unRide) 같은 종류를
     * 목적 차원에 새로 만들어 저장 데이터를 옮긴 뒤(restoreFrom — 속도도 그대로) moveTo(x, y, z, yaw, pitch)·setYHeadRot(yaw), 이쪽은
     * CHANGED_DIMENSION 으로 뺀다. 소유 region 스레드에서 부른다. 목적 차원 길이 없으면(시험 레벨) 거짓. done 은 도착(또는 되돌림) 뒤
     * 목적·원천 region 스레드에서 한 번 불린다.
     */
    public boolean teleportToDimension(String dimension, double tx, double ty, double tz, float yaw, float pitch, Runnable done) {
        if (level == null || isRemoved()) return false;
        var travel = level.dimensionTravel();
        if (travel == null) return false;
        float p = Math.clamp(pitch, -90.0F, 90.0F);
        PortalTravel plan = new PortalTravel(dimension, floor(tx), floor(tz), 0, 0,
                lvl -> new DimensionTransition(lvl, tx, ty, tz, 0, 0, 0, yaw, p));
        moveAcross(plan, travel, (e, t) -> {
            e.moveTo(tx, ty, tz, yaw, p);
            if (e instanceof LivingEntity le) le.setYHeadRot(yaw);
        }, done);
        return true;
    }

    private void moveAcross(PortalTravel plan, org.domi.furnace.server.block.LevelServices.DimensionTravel travel,
                            java.util.function.BiConsumer<Entity, DimensionTransition> place, Runnable done) {
        stopRiding();
        ejectPassengers();
        final NbtCompound tag = saveWithoutId(new NbtCompound());
        final int cooldown = portalCooldown;
        final PortalProcessor process = portalProcess;
        final String typeName = type.name();
        final double ox = x, oz = z;
        setRemoved(RemovalReason.CHANGED_DIMENSION);
        travel.travel(plan, (target, t) -> {
            try {
                arriveAcross(target, t, travel, tag, cooldown, process, typeName, ox, oz, place);
            } finally {
                if (done != null) done.run();
            }
        });
    }

    private static void arriveAcross(ChunkLevel target, DimensionTransition t, org.domi.furnace.server.block.LevelServices.DimensionTravel travel,
                                     NbtCompound tag, int cooldown, PortalProcessor process, String typeName, double ox, double oz,
                                     java.util.function.BiConsumer<Entity, DimensionTransition> place) {
        if (target == null || t == null) {   // 갈 곳이 없다 — 원래 자리에 되살린다
            travel.runAtSource(floor(ox), floor(oz), src -> {
                Entity back = Entities.create(src, typeName);
                if (back == null) return;
                back.load(tag);
                back.portalCooldown = cooldown;
                back.portalProcess = process;
                src.addFreshEntity(back);
            });
            return;
        }
        Entity e = Entities.create(target, typeName);
        if (e == null) return;
        e.load(tag);
        e.portalCooldown = cooldown;
        e.portalProcess = process;
        place.accept(e, t);
        target.addFreshEntity(e);
        target.afterDimensionTransition(t);
    }

    /**
     * changeDimension: 같은 차원이면 제자리 이동, 다른 차원이면 같은 종류를 새로 만들어 저장 데이터를 옮기고(restoreFrom) 이쪽은
     * CHANGED_DIMENSION 으로 뺀다. 승객은 내려놓는다. 목적 레벨을 같은 스레드에서 쓸 수 있을 때(시험 레벨)의 경로.
     */
    public Entity changeDimension(DimensionTransition t) {
        if (isRemoved()) return null;
        ChunkLevel target = t.newLevel();
        stopRiding();
        ejectPassengers();
        Entity e = target == level ? this : Entities.create(target, type.name());
        if (e == null) return null;
        if (e != this) {
            e.restoreFrom(this);
            setRemoved(RemovalReason.CHANGED_DIMENSION);
        }
        e.moveTo(t.x(), t.y(), t.z(), t.yRot(), e.xRot());
        e.setDeltaMovement(t.vx(), t.vy(), t.vz());
        if (e != this) target.addFreshEntity(e);
        target.afterDimensionTransition(t);
        return e;
    }

    /** restoreFrom: 저장 데이터·UUID·포털 상태를 그대로 옮긴다. */
    protected void restoreFrom(Entity old) {
        NbtCompound tag = old.saveWithoutId(new NbtCompound());
        load(tag);
        portalCooldown = old.portalCooldown;
        portalProcess = old.portalProcess;
    }

    public void checkBelowWorld() {
        if (y < level.minBuildHeight() - 64) onBelowWorld();
    }

    protected void onBelowWorld() { discard(); }

    public void lavaHurt() {
        if (!fireImmune()) {
            igniteForSeconds(15.0F);
            if (hurt(Damage.LAVA, 4.0F)) playSound("minecraft:entity.generic.burn", 0.4F, 2.0F + random.nextFloat() * 0.4F);
        }
    }

    public final void igniteForSeconds(float seconds) { igniteForTicks(floor(seconds * 20.0F)); }

    public void igniteForTicks(int ticks) { if (remainingFireTicks < ticks) setRemainingFireTicks(ticks); }

    public void setRemainingFireTicks(int t) { remainingFireTicks = t; }

    public int remainingFireTicks() { return remainingFireTicks; }

    public void clearFire() { setRemainingFireTicks(0); }

    public boolean isOnFire() { return !fireImmune() && remainingFireTicks > 0; }

    /** Pose 번호(net.minecraft.world.entity.Pose 순서). */
    public static final int POSE_STANDING = 0, POSE_FALL_FLYING = 1, POSE_SLEEPING = 2, POSE_SWIMMING = 3, POSE_SPIN_ATTACK = 4, POSE_CROUCHING = 5,
            POSE_LONG_JUMPING = 6, POSE_DYING = 7, POSE_CROAKING = 8, POSE_USING_TONGUE = 9, POSE_SITTING = 10, POSE_ROARING = 11, POSE_SNIFFING = 12,
            POSE_EMERGING = 13, POSE_DIGGING = 14, POSE_SLIDING = 15, POSE_SHOOTING = 16, POSE_INHALING = 17;

    public int getPose() { return (Integer) entityData.get(DATA_POSE); }

    public void setPose(int pose) { entityData.set(DATA_POSE, pose); }

    private static final int FLAG_SPRINTING = 3;

    public boolean isSprinting() { return ((Byte) entityData.get(DATA_SHARED_FLAGS) & 1 << FLAG_SPRINTING) != 0; }

    public void setSprinting(boolean v) { setSharedFlag(FLAG_SPRINTING, v); }

    private static final int FLAG_INVISIBLE = 5;

    public boolean isInvisible() { return ((Byte) entityData.get(DATA_SHARED_FLAGS) & 1 << FLAG_INVISIBLE) != 0; }

    public void setInvisible(boolean v) { setSharedFlag(FLAG_INVISIBLE, v); }

    private static final int FLAG_SWIMMING = 4;

    public boolean isSwimming() { return ((Byte) entityData.get(DATA_SHARED_FLAGS) & 1 << FLAG_SWIMMING) != 0; }

    public void setSwimming(boolean v) { setSharedFlag(FLAG_SWIMMING, v); }

    /** Entity.updateSwimming: 전력 질주 중이고 물속일 때만 헤엄(몹은 대개 항상 거짓). TODO(자세): 헤엄 자세(POSE_SWIMMING) 전환 */
    protected void updateSwimming() {
        if (isSwimming()) setSwimming(isSprinting() && isInWater());
        else setSwimming(isSprinting() && isInWater() && level.isWaterAt(blockPosition()));
    }

    /** setSharedFlagOnFire: 불 표시는 실제 불 또는 HasVisualFire. */
    public void setSharedFlagOnFire(boolean on) { setSharedFlag(FLAG_ONFIRE, on || hasVisualFire); }

    private static final int FLAG_GLOWING = 6;
    /** Entity.hasGlowingTag(NBT "Glowing"). */
    private boolean hasGlowingTag;
    /** Entity.hasVisualFire(NBT "HasVisualFire"): 불 없이 불붙은 모습. */
    private boolean hasVisualFire;

    public final boolean hasGlowingTag() { return hasGlowingTag; }

    /** setGlowingTag: 공유 플래그 6 = isCurrentlyGlowing(). */
    public final void setGlowingTag(boolean v) {
        hasGlowingTag = v;
        setSharedFlag(FLAG_GLOWING, isCurrentlyGlowing());
    }

    /** isCurrentlyGlowing(서버): 태그 값. LivingEntity 는 발광 효과도 본다. */
    public boolean isCurrentlyGlowing() { return hasGlowingTag; }

    protected boolean getSharedFlag(int flag) { return ((Byte) entityData.get(DATA_SHARED_FLAGS) & 1 << flag) != 0; }

    /** LivingEntity.updateGlowingStatus 용. */
    protected final void syncGlowingFlag() {
        boolean g = isCurrentlyGlowing();
        if (getSharedFlag(FLAG_GLOWING) != g) setSharedFlag(FLAG_GLOWING, g);
    }

    public boolean hasVisualFire() { return hasVisualFire; }

    public void setHasVisualFire(boolean v) { hasVisualFire = v; }

    protected void setSharedFlag(int flag, boolean on) {
        byte b = (Byte) entityData.get(DATA_SHARED_FLAGS);
        entityData.set(DATA_SHARED_FLAGS, (byte) (on ? b | 1 << flag : b & ~(1 << flag)));
    }

    /** hurt(source, amount): 피해를 받았는가. 기본 엔티티는 무적 검사만 하고 받지 않는다. */
    public boolean hurt(Damage source, float amount) {
        return !isInvulnerableTo(source) && false;
    }

    public boolean isInvulnerableTo(Damage source) {
        return isRemoved() || invulnerable && !source.bypassesInvulnerability() || source.fire() && fireImmune();
    }

    /** markHurt: 다음 추적 갱신에서 속도를 바로 보낸다. */
    public void markHurt() { hurtMarked = true; }

    /** 때린 쪽이 있는 피해(플레이어가 액자·그림을 칠 때). 기본은 hurt. */
    public boolean hurtBy(Damage source, float amount, Targetable attacker) { return hurt(source, amount); }

    /** Entity.skipAttackInteraction: true 면 일반 공격 피해 대신 이 엔티티가 직접 처리한다(액자·그림). */
    public boolean skipAttackInteraction(Player attacker) { return false; }

    /** 플레이어가 이 엔티티를 보게 됐다/보지 않게 됐다(보스 바 등). */
    public void onStartSeenBy(Player p) {}

    public void onStopSeenBy(Player p) {}

    /** Entity.thunderHit: 번개에 맞으면 불이 붙고(이미 붙었으면 시간 연장) 5 피해. */
    public void thunderHit(ChunkLevel level, LightningBolt bolt) {
        remainingFireTicks++;
        if (remainingFireTicks == 0) igniteForSeconds(8.0F);
        hurt(new Damage("lightning_bolt", false, false, false), 5.0F);
    }

    /** causeFallDamage: 기본 엔티티는 낙하 피해를 받지 않고, 승객에게 넘긴다(fall_damage_immune 종류는 넘기지 않는다). */
    public boolean causeFallDamage(float distance, float multiplier) {
        if (typeInTag("minecraft:fall_damage_immune")) return false;
        if (!passengers.isEmpty()) for (Rider r : passengers) if (r instanceof Entity e) e.causeFallDamage(distance, multiplier);   // TODO(플레이어 낙하): 탄 플레이어
        return false;
    }

    // ------------------------------------------------------------------ 엔티티끼리 밀기(Entity.push)

    /** isPushable: 다른 엔티티가 밀 수 있다(기본 아니오). */
    public boolean isPushable() { return false; }

    /** canBeCollidedWith: 단단한 엔티티(보트 등). */
    public boolean canBeCollidedWith() { return false; }

    /** push(dx, dy, dz): 속도에 더하고 이번 갱신을 앞당긴다. */
    public void push(double dx, double dy, double dz) {
        setDeltaMovement(vx + dx, vy + dy, vz + dz);
        hasImpulse = true;
    }

    /** push(entity): 같은 탈것이 아니고 둘 다 물리가 있으면 가로로 서로 민다(가까울수록 세게, 0.05 배). */
    public void push(Entity e) {
        if (isPassengerOfSameVehicle(e) || e.noPhysics || noPhysics) return;
        double dx = e.x - x, dz = e.z - z;
        double d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d >= 0.01F) {
            d = Math.sqrt(d);
            dx /= d;
            dz /= d;
            double f = 1.0 / d;
            if (f > 1.0) f = 1.0;
            dx *= f;
            dz *= f;
            dx *= 0.05F;
            dz *= 0.05F;
            if (!isVehicle() && isPushable()) push(-dx, 0.0, -dz);
            if (!e.isVehicle() && e.isPushable()) e.push(dx, 0.0, dz);
        }
    }

    /**
     * 플레이어와 서로 미는 Entity.push 에서 내 몫(플레이어 쪽 속도는 클라이언트 권위라 버린다). 서버 상태는 이쪽만 바뀐다.
     * 바닐라 push(other) 의 this=플레이어, other=나.
     */
    public void pushByPlayer(Player p) {
        if (isPassengerOfSameVehicle(p) || noPhysics) return;
        double dx = x - p.x(), dz = z - p.z();
        double d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d >= 0.01F) {
            d = Math.sqrt(d);
            dx /= d;
            dz /= d;
            double f = 1.0 / d;
            if (f > 1.0) f = 1.0;
            dx *= f;
            dz *= f;
            dx *= 0.05F;
            dz *= 0.05F;
            if (!isVehicle() && isPushable()) push(dx, 0.0, dz);
        }
    }

    /** Entity.getTeam: 점수판 이름(UUID 문자열)이 든 팀. 팀이 하나도 없으면 락 없이 null. */
    public org.domi.furnace.server.scores.PlayerTeam getTeam() {
        var b = level == null ? null : level.scoreboard();
        return b == null || !b.hasTeams() ? null : b.getPlayersTeam(uuid.toString());
    }

    public void resetFallDistance() { fallDistance = 0.0F; }

    /** 소리 분류(SoundSource) — 기본 neutral, 적대 몹은 hostile. */
    public String soundSource() { return "neutral"; }

    public void playSound(String sound, float volume, float pitch) {
        if (!isSilent()) level.playSound(null, x, y, z, sound, soundSource(), volume, pitch);
    }

    // ------------------------------------------------------------------ 제거

    public void discard() { remove(RemovalReason.DISCARDED); }

    /** Entity.kill: KILLED 로 제거하고 entity_die 게임 이벤트. */
    public void kill() {
        remove(RemovalReason.KILLED);
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DIE);
    }

    public void remove(RemovalReason reason) { setRemoved(reason); }

    public final void setRemoved(RemovalReason reason) {
        if (removalReason == null) removalReason = reason;
        if (removalReason.shouldDestroy()) stopRiding();
        if (!passengers.isEmpty()) for (Rider r : passengers) r.stopRiding();
        if (level != null) level.entityRemoved(this);
    }

    // ------------------------------------------------------------------ 이동(바닐라 Entity.move)

    public static final int MOVER_SELF = 0, MOVER_PLAYER = 1, MOVER_PISTON = 2, MOVER_SHULKER_BOX = 3, MOVER_SHULKER = 4;

    public void move(int mover, double dx, double dy, double dz) {
        if (noPhysics) {
            setPos(x + dx, y + dy, z + dz);
            return;
        }
        boolean wasOnFire = isOnFire();
        if (mover == MOVER_PISTON) {
            double[] lim = limitPistonMovement(dx, dy, dz);
            if (lim == null) return;
            dx = lim[0];
            dy = lim[1];
            dz = lim[2];
            // movement.equals(Vec3.ZERO): Double.compare 라 +0.0 셋일 때만
            if (Double.compare(dx, 0.0) == 0 && Double.compare(dy, 0.0) == 0 && Double.compare(dz, 0.0) == 0) return;
        }
        if (stuckX * stuckX + stuckY * stuckY + stuckZ * stuckZ > 1.0E-7) {
            dx *= stuckX;
            dy *= stuckY;
            dz *= stuckZ;
            stuckX = stuckY = stuckZ = 0.0;
            setDeltaMovement(0.0, 0.0, 0.0);
        }
        double[] m = collide(dx, dy, dz);
        double mx = m[0], my = m[1], mz = m[2];
        double lenSqr = mx * mx + my * my + mz * mz;
        if (lenSqr > 1.0E-7) setPos(x + mx, y + my, z + mz);
        boolean collX = !equalish(dx, mx), collZ = !equalish(dz, mz);
        horizontalCollision = collX || collZ;
        verticalCollision = dy != my;
        verticalCollisionBelow = verticalCollision && dy < 0.0;
        minorHorizontalCollision = false;
        onGround = verticalCollisionBelow;
        checkSupportingBlock(verticalCollisionBelow, new double[] {mx, my, mz});
        Pos onPos = getOnPosLegacy();
        int onState = level.getBlockState(onPos);
        checkFallDamage(my, onGround, onState, onPos);
        if (isRemoved()) return;
        if (horizontalCollision) setDeltaMovement(collX ? 0.0 : vx, vy, collZ ? 0.0 : vz);
        var behavior = BlockBehaviors.of(onState);
        if (dy != my) behavior.updateEntityAfterFallOn(level, this);
        if (onGround) behavior.stepOn(level, onPos, onState, this);
        applyMovementEmission(onPos, onState, mx, my, mz, dx, dy, dz);
        if (isRemoved()) return;
        checkInsideBlocks();
        float f = getBlockSpeedFactor();
        setDeltaMovement(vx * f, vy, vz * f);
        if (noneFireOrLava(bb.deflate(1.0E-6))) {
            if (remainingFireTicks <= 0) setRemainingFireTicks(-FIRE_IMMUNE_TICKS);
            if (wasOnFire && (isInPowderSnow || isInWaterRainOrBubble())) playEntityOnFireExtinguishedSound();
        }
        if (isOnFire() && (isInPowderSnow || isInWaterRainOrBubble())) setRemainingFireTicks(-FIRE_IMMUNE_TICKS);
    }

    /** 바닐라 Entity.pistonDeltas·pistonDeltasGameTime: 한 게임 틱에 피스톤이 한 축으로 미는 총량을 ±0.51 로 묶는다. */
    private final double[] pistonDeltas = new double[3];
    private long pistonDeltasGameTime;

    /** limitPistonMovement: 한 축만 본다(x → y → z 순). 남은 양이 1e-5 이하면 null(=Vec3.ZERO, 움직이지 않음). */
    private double[] limitPistonMovement(double dx, double dy, double dz) {
        if (dx * dx + dy * dy + dz * dz <= 1.0E-7) return new double[] {dx, dy, dz};
        long t = level.gameTime();
        if (t != pistonDeltasGameTime) {
            java.util.Arrays.fill(pistonDeltas, 0.0);
            pistonDeltasGameTime = t;
        }
        int axis;
        double d;
        if (dx != 0.0) { axis = 0; d = dx; }
        else if (dy != 0.0) { axis = 1; d = dy; }
        else if (dz != 0.0) { axis = 2; d = dz; }
        else return null;
        double c = Math.clamp(d + pistonDeltas[axis], -0.51, 0.51);
        double m = c - pistonDeltas[axis];
        pistonDeltas[axis] = c;
        if (Math.abs(m) <= 1.0E-5F) return null;
        return new double[] {axis == 0 ? m : 0.0, axis == 1 ? m : 0.0, axis == 2 ? m : 0.0};
    }

    /** isSupportedBy: mainSupportingBlockPos 가 그 칸인가. */
    public boolean isSupportedBy(Pos pos) { return supportingBlock != null && supportingBlock.equals(pos); }

    /** getPistonPushReaction 이 IGNORE 인가(구름·표시·상호작용·마커·불길한 아이템 생성기·마커 갑옷 거치대). 기본 NORMAL. */
    public boolean ignoresPistonPush() { return false; }

    // ------------------------------------------------------------------ 이동 방출(바닐라 Entity.move 의 MovementEmission 부분)

    /** walkDist·moveDist·flyDist, nextStep. */
    public float walkDist, moveDist, flyDist;
    protected float nextStep = 1.0F;

    /** Entity.MovementEmission: 소리·게임 이벤트를 내는가. */
    public enum MovementEmission {
        NONE(false, false), SOUNDS(true, false), EVENTS(false, true), ALL(true, true);

        final boolean sounds, events;

        MovementEmission(boolean sounds, boolean events) {
            this.sounds = sounds;
            this.events = events;
        }

        public boolean emitsAnything() { return sounds || events; }

        public boolean emitsEvents() { return events; }

        public boolean emitsSounds() { return sounds; }
    }

    /**
     * getMovementEmission: 바닐라 기본은 ALL 이지만 여기서는 비생물(아이템·투사체·수레 등)이 대부분 NONE/EVENTS 라 기본을 NONE 으로 두고
     * LivingEntity 가 ALL 로 덮는다. TODO(엔티티): 보트·수레(EVENTS) 등 비생물 쪽은 아직 NONE.
     */
    protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }

    /** nextStep(): (int) moveDist + 1. */
    protected float nextStep() { return (int) moveDist + 1; }

    /** isFlapping: 날갯짓 몹(박쥐·앵무새 등)이 덮는다. */
    protected boolean isFlapping() { return false; }

    protected void onFlap() {}

    /** Entity.gameEvent(event): 자기 위치에서, 자기를 출처로. */
    public void gameEvent(org.domi.furnace.server.world.gameevent.GameEvent event) { gameEvent(event, this); }

    public void gameEvent(org.domi.furnace.server.world.gameevent.GameEvent event, Object source) {
        if (level != null) level.gameEvent(event, new Vec3(x, y, z), org.domi.furnace.server.world.gameevent.GameEventContext.of(source));
    }

    /** dampensVibrations: 양털 아이템 등. */
    public boolean dampensVibrations() { return false; }

    /** getControllingPassenger 가 있으면 그것, 아니면 자기(waterSwimSound). */
    protected String getSwimSound() { return "minecraft:entity.generic.swim"; }

    /** waterSwimSound → playSwimSound: 난수 두 번(높이). */
    protected void waterSwimSound() {
        Rider ctl = getControllingPassenger();
        Entity e = ctl instanceof Entity ce ? ce : this;
        float f = e == this ? 0.35F : 0.4F;
        float f1 = Math.min(1.0F, (float) Math.sqrt(e.vx * e.vx * 0.2F + e.vy * e.vy + e.vz * e.vz * 0.2F) * f);
        playSound(getSwimSound(), f1, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
    }

    private static final int POWDER_SNOW = B.id("powder_snow");

    private void applyMovementEmission(Pos legacyOnPos, int legacyOnState, double mx, double my, double mz, double dx, double dy, double dz) {
        MovementEmission em = getMovementEmission();
        if (!em.emitsAnything() || isPassenger()) return;
        flyDist += (float) (Math.sqrt(mx * mx + my * my + mz * mz) * 0.6);
        Pos onPos = getOnPos();
        int onState = level.getBlockState(onPos);
        boolean climbable = B.isTag(onState, "minecraft:climbable") || B.is(onState, POWDER_SNOW);
        double ey = climbable ? my : 0.0;
        walkDist += (float) Math.sqrt(mx * mx + mz * mz) * 0.6F;
        moveDist += (float) Math.sqrt(mx * mx + ey * ey + mz * mz) * 0.6F;
        if (moveDist > nextStep && !B.isAir(onState)) {
            boolean same = onPos.equals(legacyOnPos);
            boolean any = vibrationAndSoundEffectsFromBlock(legacyOnPos, legacyOnState, em.emitsSounds(), same, dy);
            if (!same) any |= vibrationAndSoundEffectsFromBlock(onPos, onState, false, em.emitsEvents(), dy);
            if (any) {
                nextStep = nextStep();
            } else if (isInWater()) {
                nextStep = nextStep();
                if (em.emitsSounds()) waterSwimSound();
                if (em.emitsEvents()) gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.SWIM);
            }
        } else if (B.isAir(onState)) {
            if (isFlapping()) {
                onFlap();
                if (em.emitsEvents()) gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.FLAP);
            }
        }
    }

    /** vibrationAndSoundEffectsFromBlock. TODO(소리): 걸음 소리(블록 SoundType)는 아직 없다 — 난수를 쓰지 않으므로 상태는 같다. */
    private boolean vibrationAndSoundEffectsFromBlock(Pos pos, int state, boolean playStepSound, boolean broadcastGameEvent, double wantedDy) {
        if (B.isAir(state)) return false;
        boolean climbable = B.isTag(state, "minecraft:climbable") || B.is(state, POWDER_SNOW);
        if ((onGround() || climbable || getPose() == POSE_CROUCHING && wantedDy == 0.0) && !isSwimming()) {
            if (broadcastGameEvent) {
                level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.STEP, new Vec3(x, y, z),
                        org.domi.furnace.server.world.gameevent.GameEventContext.of(this, state));
            }
            return true;
        }
        return false;
    }

    /** 이 엔티티가 가진 이동 게임 이벤트 리스너(워든·알레이). 없으면 null. */
    public org.domi.furnace.server.world.gameevent.DynamicGameEventListener<?> dynamicGameEventListener() { return null; }

    /** Mth.equal(double, double): 차이가 1e-5 보다 작은가. */
    static boolean equalish(double a, double b) { return Math.abs(b - a) < 1.0E-5F; }

    protected void playEntityOnFireExtinguishedSound() {
        playSound("minecraft:entity.generic.extinguish_fire", 0.7F, 1.6F + (random.nextFloat() - random.nextFloat()) * 0.4F);
    }

    /** level.getBlockStatesIfLoaded(box).noneMatch(불 태그 또는 용암). 로드 안 됐으면 참. */
    private boolean noneFireOrLava(AABB box) {
        int x0 = floor(box.minX()), y0 = floor(box.minY()), z0 = floor(box.minZ());
        int x1 = floor(box.maxX()), y1 = floor(box.maxY()), z1 = floor(box.maxZ());
        if (!level.hasChunksAt(x0, z0, x1, z1)) return true;
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    int s = level.getBlockState(new Pos(bx, by, bz));
                    if (B.isTag(s, "minecraft:fire") || B.is(s, LAVA)) return false;
                }
            }
        }
        return true;
    }

    private static final int LAVA = B.id("lava"), WATER = B.id("water"), BUBBLE_COLUMN = B.id("bubble_column"),
            HONEY_BLOCK = B.id("honey_block"), FROSTED_ICE = B.id("frosted_ice"), ICE = B.id("ice");

    public boolean isInWater() { return wasTouchingWater; }

    public boolean isInLava() { return !firstTick && lavaHeight > 0.0; }

    public boolean isInBubbleColumn() { return B.is(inBlockState(), BUBBLE_COLUMN); }

    public boolean isInRain() {
        return level.isRainingAt(blockPosition()) || level.isRainingAt(new Pos(floor(x), floor(bb.maxY()), floor(z)));
    }

    public boolean isInWaterRainOrBubble() { return isInWater() || isInRain() || isInBubbleColumn(); }

    public boolean isInPowderSnow() { return isInPowderSnow; }

    public boolean isInWaterOrBubble() { return isInWater() || isInBubbleColumn(); }

    public void setIsInPowderSnow(boolean v) { isInPowderSnow = v; }

    public boolean wasInPowderSnow() { return wasInPowderSnow; }

    /** getInBlockState: 발 칸의 블록 상태(틱마다 캐시). */
    public int inBlockState() {
        if (inBlockState < 0) inBlockState = level.getBlockState(blockPosition());
        return inBlockState;
    }

    /** onAboveBubbleCol(down): 거품 기둥 수면 — 아래로면 최대 -0.9 까지 -0.03, 위로면 최대 1.8 까지 +0.1. */
    public void onAboveBubbleCol(boolean down) {
        double d = down ? Math.max(-0.9, vy - 0.03) : Math.min(1.8, vy + 0.1);
        setDeltaMovement(vx, d, vz);
    }

    /** onInsideBubbleColumn(down): 기둥 안 — 아래로면 최대 -0.3 까지 -0.03, 위로면 최대 0.7 까지 +0.06. 낙하 거리를 지운다. */
    public void onInsideBubbleColumn(boolean down) {
        double d = down ? Math.max(-0.3, vy - 0.03) : Math.min(0.7, vy + 0.06);
        setDeltaMovement(vx, d, vz);
        resetFallDistance();
    }

    /** LivingEntity 인가(압력판 감도·블록 동작 대상). */
    public boolean isLiving() { return false; }

    /** mayInteract(level, pos): 플레이어가 아니면 언제나 참. */
    public boolean mayInteract(org.domi.furnace.server.block.BlockLevel level, Pos pos) { return true; }

    /** level.broadcastEntityEvent(entity, event): 보는 사람에게 엔티티 이벤트. */
    public void broadcastEntityEvent(byte event) {
        if (tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.EntityEvent(id, event));
    }

    /** makeStuckInBlock(state, multiplier): 낙하 거리를 지우고 다음 이동을 그만큼 줄인다. */
    public void makeStuckInBlock(double mx, double my, double mz) {
        resetFallDistance();
        stuckX = mx;
        stuckY = my;
        stuckZ = mz;
    }

    /** Entity.maxUpStep: 걸어 오를 수 있는 높이. 기본 0, LivingEntity 는 step_height 속성. */
    public float maxUpStep() { return 0.0F; }

    /**
     * Entity.collide(movement) 1.21.1: 블록 충돌로 줄인 이동. 바닥(또는 낙하 착지)에서 옆으로 막히면
     * 충돌 모양의 Y 좌표로 만든 후보 높이(step 이하)를 낮은 것부터 시도해 더 멀리 가는 첫 높이로 오른다.
     * 엔티티 충돌(보트·셜커)·월드 보더 모양은 아직 목록에 없다(블록 충돌만).
     */
    protected double[] collide(double dx, double dy, double dz) {
        AABB box = bb;
        var shapes = level.blockCollisions(box.expandTowards(dx, dy, dz));
        double[] r = dx * dx + dy * dy + dz * dz == 0.0 ? new double[] {dx, dy, dz} : collideWithShapes(dx, dy, dz, box, shapes);
        boolean cx = dx != r[0], cy = dy != r[1], cz = dz != r[2];
        boolean below = cy && dy < 0.0;
        float step = maxUpStep();
        if (step > 0.0F && (below || onGround()) && (cx || cz)) {
            AABB base = below ? box.move(0.0, r[1], 0.0) : box;
            AABB search = base.expandTowards(dx, step, dz);
            if (!below) search = search.expandTowards(0.0, -1.0E-5F, 0.0);
            var stepShapes = level.blockCollisions(search);
            for (float h : stepHeightCandidates(base, stepShapes, step, (float) r[1])) {
                double[] c = collideWithShapes(dx, h, dz, base, stepShapes);
                if (c[0] * c[0] + c[2] * c[2] > r[0] * r[0] + r[2] * r[2]) {
                    c[1] -= box.minY() - base.minY();
                    return c;
                }
            }
        }
        return r;
    }

    /**
     * collectCandidateStepUpHeights: 모양들의 Y 좌표 - base.minY 중 0 이상·step 이하·(float)dy 아닌 값, 오름차순.
     * 바닐라는 VoxelShape 격자 좌표 전부를 쓰지만 여기서는 상자들의 minY/maxY 만 쓴다(오를 높이인 윗면은 둘 다 포함).
     */
    static float[] stepHeightCandidates(AABB base, java.util.List<Shape> shapes, float step, float dy) {
        java.util.TreeSet<Float> set = new java.util.TreeSet<>();
        for (Shape s : shapes) {
            for (int i = 0; i < s.boxCount(); i++) {
                double[] b = s.box(i);
                for (double y : new double[] {b[1], b[4]}) {
                    float h = (float) (y - base.minY());
                    if (!(h < 0.0F) && h != dy && !(h > step)) set.add(h);
                }
            }
        }
        float[] out = new float[set.size()];
        int i = 0;
        for (float h : set) out[i++] = h;
        return out;
    }

    /** Entity.collideWithShapes: y 먼저, 그다음 |x| < |z| 면 z·x, 아니면 x·z. */
    public static double[] collideWithShapes(double dx, double dy, double dz, AABB box, java.util.List<Shape> shapes) {
        if (shapes.isEmpty()) return new double[] {dx, dy, dz};
        if (dy != 0.0) {
            dy = collideAxis(1, box, shapes, dy);
            if (dy != 0.0) box = box.move(0.0, dy, 0.0);
        }
        boolean zFirst = Math.abs(dx) < Math.abs(dz);
        if (zFirst && dz != 0.0) {
            dz = collideAxis(2, box, shapes, dz);
            if (dz != 0.0) box = box.move(0.0, 0.0, dz);
        }
        if (dx != 0.0) {
            dx = collideAxis(0, box, shapes, dx);
            if (!zFirst && dx != 0.0) box = box.move(dx, 0.0, 0.0);
        }
        if (!zFirst && dz != 0.0) dz = collideAxis(2, box, shapes, dz);
        return new double[] {dx, dy, dz};
    }

    /** Shapes.collide(axis, box, shapes, d). */
    private static double collideAxis(int axis, AABB box, java.util.List<Shape> shapes, double d) {
        for (Shape s : shapes) {
            if (Math.abs(d) < 1.0E-7) return 0.0;
            d = s.collide(axis, box, d);
        }
        return d;
    }

    /** checkSupportingBlock(onGround, movement): 디딘 블록 위치(가장 가까운 충돌 블록)를 기억한다. */
    protected void checkSupportingBlock(boolean ground, double[] movement) {
        if (ground) {
            AABB below = new AABB(bb.minX(), bb.minY() - 1.0E-6, bb.minZ(), bb.maxX(), bb.minY(), bb.maxZ());
            Pos found = level.findSupportingBlock(below, x, y, z);
            if (found != null || onGroundNoBlocks) {
                supportingBlock = found;
            } else if (movement != null) {
                supportingBlock = level.findSupportingBlock(below.move(-movement[0], 0.0, -movement[2]), x, y, z);
                found = supportingBlock;
            }
            onGroundNoBlocks = found == null;
        } else {
            onGroundNoBlocks = false;
            supportingBlock = null;
        }
    }

    /** getOnPosLegacy: 0.2 아래. */
    public Pos getOnPosLegacy() { return getOnPos(0.2F); }

    /** getBlockPosBelowThatAffectsMyMovement: 0.500001 아래. */
    public Pos getBlockPosBelowThatAffectsMyMovement() { return getOnPos(0.500001F); }

    public Pos getOnPos() { return getOnPos(1.0E-5F); }

    protected Pos getOnPos(float below) {
        if (supportingBlock != null) {
            if (!(below > 1.0E-5F)) return supportingBlock;
            int s = level.getBlockState(supportingBlock);
            boolean keep = (double) below <= 0.5 && B.isTag(s, "minecraft:fences") || B.isTag(s, "minecraft:walls")
                    || B.behavior(s) instanceof org.domi.furnace.server.block.types.FenceGateBlock;
            return keep ? supportingBlock : new Pos(supportingBlock.x(), floor(y - below), supportingBlock.z());
        }
        return new Pos(floor(x), floor(y - below), floor(z));
    }

    /** checkFallDamage(dy, onGround, state, pos). */
    protected void checkFallDamage(double dy, boolean ground, int state, Pos pos) {
        if (ground) {
            if (fallDistance > 0.0F) {
                BlockBehaviors.of(state).fallOn(level, state, pos, this, fallDistance);
                int hit = supportingBlock != null ? level.getBlockState(supportingBlock) : state;
                level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.HIT_GROUND, new Vec3(x, y, z),
                        org.domi.furnace.server.world.gameevent.GameEventContext.of(this, hit));
            }
            resetFallDistance();
        } else if (dy < 0.0) {
            fallDistance -= (float) dy;
        }
    }

    /** checkInsideBlocks: 몸통(1e-7 안쪽)이 걸친 칸마다 entityInside(로드된 경우만). */
    protected void checkInsideBlocks() {
        int x0 = floor(bb.minX() + 1.0E-7), y0 = floor(bb.minY() + 1.0E-7), z0 = floor(bb.minZ() + 1.0E-7);
        int x1 = floor(bb.maxX() - 1.0E-7), y1 = floor(bb.maxY() - 1.0E-7), z1 = floor(bb.maxZ() - 1.0E-7);
        if (!level.hasChunksAt(x0, z0, x1, z1)) return;
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (!isAlive()) return;
                    Pos p = new Pos(bx, by, bz);
                    int s = level.getBlockState(p);
                    BlockBehaviors.of(s).entityInside(s, level, p, this);
                }
            }
        }
    }

    /** getBlockSpeedFactor: 발 칸(물·거품 기둥이 아니고 1.0 이면 아래 칸)의 속도 계수. */
    protected float getBlockSpeedFactor() {
        int s = level.getBlockState(blockPosition());
        float f = BlockInfo.get().speedFactor(B.block(s));
        if (!B.is(s, WATER) && !B.is(s, BUBBLE_COLUMN)) {
            return (double) f == 1.0 ? BlockInfo.get().speedFactor(B.block(level.getBlockState(getBlockPosBelowThatAffectsMyMovement()))) : f;
        }
        return f;
    }

    /** 디딘 블록의 미끄러움(아이템·생물이 쓴다). */
    protected float frictionBelow() {
        return BlockInfo.get().friction(B.block(level.getBlockState(getBlockPosBelowThatAffectsMyMovement())));
    }

    /** applyGravity. */
    protected void applyGravity() {
        double g = isNoGravity() ? 0.0 : defaultGravity();
        if (g != 0.0) setDeltaMovement(vx, vy - g, vz);
    }

    protected double defaultGravity() { return 0.0; }

    /** 투사체가 맞출 수 있는 엔티티인가(Entity.isPickable). 생물만 기본 참. */
    public boolean isPickable() { return false; }

    public int getAirSupply() { return (Integer) entityData.get(DATA_AIR_SUPPLY); }

    public void setAirSupply(int v) { entityData.set(DATA_AIR_SUPPLY, v); }

    public int getMaxAirSupply() { return 300; }

    /** 불러온 CustomName JSON 원문. 이름을 안 바꾸면 그대로 다시 쓴다(서식 손실 없음). */
    private String customNameJson;

    public boolean hasCustomName() { return entityData.get(DATA_CUSTOM_NAME) != null; }

    public org.domi.furnace.api.text.Text getCustomName() { return (org.domi.furnace.api.text.Text) entityData.get(DATA_CUSTOM_NAME); }

    /** setCustomName(null = 지움). */
    public void setCustomName(org.domi.furnace.api.text.Text name) {
        customNameJson = name == null ? null : name.toJson();
        entityData.set(DATA_CUSTOM_NAME, name);
        onCustomNameChanged();
    }

    /** 이름이 바뀐 뒤(보스 바 이름 갱신 등). */
    protected void onCustomNameChanged() {}

    public boolean isCustomNameVisible() { return (Boolean) entityData.get(DATA_CUSTOM_NAME_VISIBLE); }

    public void setCustomNameVisible(boolean v) { entityData.set(DATA_CUSTOM_NAME_VISIBLE, v); }

    /** Entity.getName: 커스텀 이름, 없으면 종류 이름. */
    public org.domi.furnace.api.text.Text getName() {
        var n = getCustomName();
        return n != null ? n : typeName(type);
    }

    static org.domi.furnace.api.text.Text typeName(EntityType type) {
        String n = type.name();
        int i = n.indexOf(':');
        return org.domi.furnace.api.text.Text.translatable("entity." + n.substring(0, i) + "." + n.substring(i + 1));
    }

    /** Entity.ignoreExplosion: 폭발이 건드리지 않는 엔티티. */
    public boolean ignoreExplosion() { return false; }

    /** getBlockExplosionResistance: 내가 일으킨 폭발에서 그 블록의 저항(기본 그대로). */
    public float blockExplosionResistance(ChunkLevel level, Pos pos, int state, float resistance) { return resistance; }

    /** shouldBlockExplode: 내가 일으킨 폭발이 그 블록을 부수는가(기본 예). */
    public boolean shouldBlockExplode(ChunkLevel level, Pos pos, int state, float power) { return true; }

    /** 폭발 밀림 방향 계산에 쓰는 높이(생물은 눈높이, 점화된 TNT 는 발치). */
    public double eyeYForExplosion() { return y() + bbHeight() * 0.85; }

    /**
     * moveTowardsClosestSpace(x, y, z): 블록에 끼었으면 가까운 빈 쪽(북·남·서·동·위 중 꽉 찬 블록이 아닌 곳)으로 민다.
     */
    protected void moveTowardsClosestSpace(double px, double py, double pz) {
        int bx = floor(px), by = floor(py), bz = floor(pz);
        double fx = px - bx, fy = py - by, fz = pz - bz;
        int best = Dir.UP;
        double bestDist = Double.MAX_VALUE;
        for (int dir : new int[] {Dir.NORTH, Dir.SOUTH, Dir.WEST, Dir.EAST, Dir.UP}) {
            Pos n = new Pos(bx + Dir.DX[dir], by + Dir.DY[dir], bz + Dir.DZ[dir]);
            if (!B.isCollisionShapeFullBlock(level.getBlockState(n))) {
                int axis = Dir.axis(dir);
                double v = axis == Dir.AXIS_X ? fx : axis == Dir.AXIS_Y ? fy : fz;
                double d = Dir.positive(dir) ? 1.0 - v : v;
                if (d < bestDist) {
                    bestDist = d;
                    best = dir;
                }
            }
        }
        float speed = random.nextFloat() * 0.2F + 0.1F;
        float step = Dir.positive(best) ? 1.0F : -1.0F;
        double mx = vx * 0.75, my = vy * 0.75, mz = vz * 0.75;
        switch (Dir.axis(best)) {
            case Dir.AXIS_X -> setDeltaMovement(step * speed, my, mz);
            case Dir.AXIS_Y -> setDeltaMovement(mx, step * speed, mz);
            default -> setDeltaMovement(mx, my, step * speed);
        }
    }

    // ------------------------------------------------------------------ 유체(바닐라 updateInWaterStateAndDoFluidPushing)

    protected boolean updateInWaterStateAndDoFluidPushing() {
        waterHeight = lavaHeight = 0.0;
        updateInWaterStateAndDoWaterCurrentPushing();
        double lavaSpeed = level.ultraWarm() ? 0.007 : 0.0023333333333333335;
        boolean inLava = updateFluidHeightAndDoFluidPushing(false, lavaSpeed);
        return isInWater() || inLava;
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        if (updateFluidHeightAndDoFluidPushing(true, 0.014)) {
            if (!wasTouchingWater && !firstTick) doWaterSplashEffect();
            resetFallDistance();
            wasTouchingWater = true;
            clearFire();
        } else {
            wasTouchingWater = false;
        }
    }

    /** doWaterSplashEffect: 물에 처음 들어갈 때 첨벙 소리(입자는 서버에서 보내지 않는다). */
    protected void doWaterSplashEffect() {
        float f = Math.min(1.0F, (float) Math.sqrt(vx * vx * 0.2F + vy * vy + vz * vz * 0.2F) * 0.2F);
        playSound(f < 0.25F ? swimSplashSound() : swimHighSpeedSplashSound(), f, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
        float w = bbWidth();
        for (int i = 0; (float) i < 1.0F + w * 20.0F; i++) {
            random.nextDouble();
            random.nextDouble();
            random.nextDouble();
        }
        for (int i = 0; (float) i < 1.0F + w * 20.0F; i++) {
            random.nextDouble();
            random.nextDouble();
        }
    }

    protected String swimSplashSound() { return "minecraft:entity.generic.splash"; }

    protected String swimHighSpeedSplashSound() { return "minecraft:entity.generic.splash"; }

    public double fluidHeight(boolean water) { return water ? waterHeight : lavaHeight; }

    /** updateFluidHeightAndDoFluidPushing(tag, speed): 걸친 유체 칸의 높이를 재고 흐름 쪽으로 민다. */
    public boolean updateFluidHeightAndDoFluidPushing(boolean water, double speed) {
        if (touchingUnloadedChunk()) return false;
        AABB box = bb.deflate(0.001);
        int x0 = floor(box.minX()), x1 = ceil(box.maxX()), y0 = floor(box.minY()), y1 = ceil(box.maxY()), z0 = floor(box.minZ()), z1 = ceil(box.maxZ());
        double height = 0.0;
        boolean pushed = isPushedByFluid();
        boolean found = false;
        double fx = 0, fy = 0, fz = 0;
        int count = 0;
        for (int bx = x0; bx < x1; bx++) {
            for (int by = y0; by < y1; by++) {
                for (int bz = z0; bz < z1; bz++) {
                    Pos p = new Pos(bx, by, bz);
                    int fluid = level.getFluidState(p);
                    if (water ? !Fluids.isWater(fluid) : !Fluids.isLava(fluid)) continue;
                    double top = (float) by + FluidTicker.height(fluid, level, p);
                    if (top >= box.minY()) {
                        found = true;
                        height = Math.max(top - box.minY(), height);
                        if (pushed) {
                            double[] flow = FluidTicker.flow(level, p, fluid);
                            if (height < 0.4) {
                                flow[0] *= height;
                                flow[1] *= height;
                                flow[2] *= height;
                            }
                            fx += flow[0];
                            fy += flow[1];
                            fz += flow[2];
                            count++;
                        }
                    }
                }
            }
        }
        if (Math.sqrt(fx * fx + fy * fy + fz * fz) > 0.0) {
            if (count > 0) {
                fx *= 1.0 / count;
                fy *= 1.0 / count;
                fz *= 1.0 / count;
            }
            if (!isPlayer()) {
                double len = Math.sqrt(fx * fx + fy * fy + fz * fz);
                if (len < 1.0E-4) {
                    fx = fy = fz = 0.0;
                } else {
                    fx /= len;
                    fy /= len;
                    fz /= len;
                }
            }
            fx *= speed;
            fy *= speed;
            fz *= speed;
            if (Math.abs(vx) < 0.003 && Math.abs(vz) < 0.003 && Math.sqrt(fx * fx + fy * fy + fz * fz) < 0.0045000000000000005) {
                double len = Math.sqrt(fx * fx + fy * fy + fz * fz);
                if (len < 1.0E-4) {
                    fx = fy = fz = 0.0;
                } else {
                    fx = fx / len * 0.0045000000000000005;
                    fy = fy / len * 0.0045000000000000005;
                    fz = fz / len * 0.0045000000000000005;
                }
            }
            setDeltaMovement(vx + fx, vy + fy, vz + fz);
        }
        if (water) waterHeight = height;
        else lavaHeight = height;
        return found;
    }

    public boolean isPlayer() { return false; }

    /** touchingUnloadedChunk: 몸통(1 넓힌) 아래 청크가 다 로드됐는가의 반대. */
    public boolean touchingUnloadedChunk() {
        AABB box = bb.inflate(1.0);
        return !level.hasChunksAt(floor(box.minX()), floor(box.minZ()), ceil(box.maxX()), ceil(box.maxZ()));
    }

    /** updateFluidOnEyes: 눈 높이가 유체 표면 아래인가. */
    private void updateFluidOnEyes() {
        wasEyeInWater = eyeInWater;
        eyeInWater = eyeInLava = false;
        double eye = eyeY() - 0.11111111F;
        Pos p = new Pos(floor(x), floor(eye), floor(z));
        int fluid = level.getFluidState(p);
        double top = (float) p.y() + FluidTicker.height(fluid, level, p);
        if (top > eye && !Fluids.isEmpty(fluid)) {
            eyeInWater = Fluids.isWater(fluid);
            eyeInLava = Fluids.isLava(fluid);
        }
    }

    public boolean isEyeInWater() { return eyeInWater; }

    // ------------------------------------------------------------------ 저장(Entity.saveWithoutId / load)

    /** saveWithoutId: 공통 필드 뒤 addAdditionalSaveData. */
    public NbtCompound saveWithoutId(NbtCompound tag) {
        NbtList pos = new NbtList((byte) 6);
        pos.add(vehicle != null ? vehicle.x : x);   // 탄 것은 탈것의 x·z
        pos.add(y);
        pos.add(vehicle != null ? vehicle.z : z);
        tag.put("Pos", pos);
        NbtList motion = new NbtList((byte) 6);
        motion.add(vx);
        motion.add(vy);
        motion.add(vz);
        tag.put("Motion", motion);
        NbtList rot = new NbtList((byte) 5);
        rot.add(yRot);
        rot.add(xRot);
        tag.put("Rotation", rot);
        tag.putFloat("FallDistance", fallDistance);
        tag.putShort("Fire", (short) remainingFireTicks);
        tag.putShort("Air", (short) (int) (Integer) entityData.get(DATA_AIR_SUPPLY));
        tag.putBoolean("OnGround", onGround);
        tag.putBoolean("Invulnerable", invulnerable);
        tag.putInt("PortalCooldown", portalCooldown);
        tag.putIntArray("UUID", uuidToInts(uuid));
        if (hasCustomName()) tag.putString("CustomName", customNameJson != null ? customNameJson : getCustomName().toJson());
        if (isCustomNameVisible()) tag.putBoolean("CustomNameVisible", true);
        if (isSilent()) tag.putBoolean("Silent", true);
        if (isNoGravity()) tag.putBoolean("NoGravity", true);
        if (hasGlowingTag) tag.putBoolean("Glowing", true);
        int frozen = (Integer) entityData.get(DATA_TICKS_FROZEN);
        if (frozen > 0) tag.putInt("TicksFrozen", frozen);
        if (hasVisualFire) tag.putBoolean("HasVisualFire", true);
        if (!commandTags.isEmpty()) {
            NbtList tagList = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_STRING);
            for (String t : commandTags) tagList.add(t);
            tag.put("Tags", tagList);
        }
        addAdditionalSaveData(tag);
        if (!passengers.isEmpty()) {
            NbtList list = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
            for (Rider r : passengers) {
                if (!(r instanceof Entity e)) continue;   // 플레이어는 자기 데이터에 저장된다
                NbtCompound t = e.saveAsPassenger();
                if (t != null) list.add(t);
            }
            if (list.size() > 0) tag.put("Passengers", list);
        }
        return tag;
    }

    /** EntityType.canSerialize: 번개처럼 저장하지 않는 종류는 false. */
    public boolean shouldSave() { return true; }

    /** Entity.save: 탄 것은 탈것이 저장한다(null). 아니면 saveAsPassenger(승객까지 재귀). */
    public NbtCompound save() { return vehicle != null ? null : saveAsPassenger(); }

    public void load(NbtCompound tag) {
        NbtList pos = tag.getList("Pos"), motion = tag.getList("Motion"), rot = tag.getList("Rotation");
        double mx = motion == null || motion.size() < 3 ? 0 : num(motion, 0);
        double my = motion == null || motion.size() < 3 ? 0 : num(motion, 1);
        double mz = motion == null || motion.size() < 3 ? 0 : num(motion, 2);
        setDeltaMovement(Math.abs(mx) > 10.0 ? 0.0 : mx, Math.abs(my) > 10.0 ? 0.0 : my, Math.abs(mz) > 10.0 ? 0.0 : mz);
        double px = pos == null || pos.size() < 3 ? 0 : num(pos, 0);
        double py = pos == null || pos.size() < 3 ? 0 : num(pos, 1);
        double pz = pos == null || pos.size() < 3 ? 0 : num(pos, 2);
        px = Math.clamp(px, -3.0000512E7, 3.0000512E7);
        py = Math.clamp(py, -2.0E7, 2.0E7);
        pz = Math.clamp(pz, -3.0000512E7, 3.0000512E7);
        setPosRaw(px, py, pz);
        xo = xOld = px;
        yo = yOld = py;
        zo = zOld = pz;
        float yr = rot == null || rot.size() < 2 ? 0 : (float) num(rot, 0), xr = rot == null || rot.size() < 2 ? 0 : (float) num(rot, 1);
        setYRot(yr);
        setXRot(xr);
        yRotO = yRot;
        xRotO = xRot;
        fallDistance = tag.getFloat("FallDistance");
        remainingFireTicks = tag.getShort("Fire");
        if (tag.contains("Air")) entityData.set(DATA_AIR_SUPPLY, (int) tag.getShort("Air"));
        onGround = tag.getBoolean("OnGround");
        invulnerable = tag.getBoolean("Invulnerable");
        portalCooldown = tag.getIntOr0("PortalCooldown");
        if (tag.tags().get("UUID") instanceof int[] u && u.length == 4) uuid = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) setPos(x, y, z);
        if (tag.tags().get("CustomName") instanceof String json) {
            try {
                var name = org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json);
                entityData.set(DATA_CUSTOM_NAME, name);
                customNameJson = name == null ? null : json;
                onCustomNameChanged();
            } catch (RuntimeException ignored) {
                // 바닐라: 경고만 남기고 이름 없이 계속
            }
        }
        setCustomNameVisible(tag.getBoolean("CustomNameVisible"));
        entityData.set(DATA_SILENT, tag.getBoolean("Silent"));
        entityData.set(DATA_NO_GRAVITY, tag.getBoolean("NoGravity"));
        setGlowingTag(tag.getBoolean("Glowing"));
        entityData.set(DATA_TICKS_FROZEN, tag.getIntOr0("TicksFrozen"));
        hasVisualFire = tag.getBoolean("HasVisualFire");
        commandTags.clear();
        if (tag.tags().get("Tags") instanceof NbtList tagList) {
            int n = Math.min(tagList.size(), 1024);
            for (int i = 0; i < n; i++) commandTags.add(tagList.get(i) instanceof String t ? t : "");
        }
        readAdditionalSaveData(tag);
    }

    private static double num(NbtList list, int i) { return list.get(i) instanceof Number n ? n.doubleValue() : 0.0; }

    protected abstract void addAdditionalSaveData(NbtCompound tag);

    protected abstract void readAdditionalSaveData(NbtCompound tag);

    static int[] uuidToInts(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    // ------------------------------------------------------------------ 탑승(바닐라 Entity 의 vehicle/passengers 부분)

    @Override
    public final Entity vehicle() { return vehicle; }

    @Override
    public final boolean isPassenger() { return vehicle != null; }

    /** getPassengers: 바꿀 수 없는 목록(바뀔 때마다 새 목록). */
    public final java.util.List<Rider> passengers() { return passengers; }

    public final boolean isVehicle() { return !passengers.isEmpty(); }

    public final Rider firstPassenger() { return passengers.isEmpty() ? null : passengers.get(0); }

    public final boolean hasPassenger(Rider r) { return passengers.contains(r); }

    @Override
    public void setVehicleField(Entity v) { vehicle = v; }

    @Override
    public void setBoardingCooldown(int ticks) { boardingCooldown = ticks; }

    public int boardingCooldown() { return boardingCooldown; }

    @Override
    public float riderYRot() { return yRot; }

    @Override public float riderXRot() { return xRot; }

    @Override public String dimensionKey() { return level.dimension(); }

    @Override public String typeName() { return type.name(); }

    /** 선택자 name=: 커스텀 이름의 문자열. 없으면 null(종류 번역 이름 비교는 아직 없음). */
    @Override public String selectorName() { return hasCustomName() ? getCustomName().getString() : null; }

    /** 바닐라 Entity.getDisplayName: getName(팀 서식·hover 는 아직 없음). */
    @Override public org.domi.furnace.api.text.Text displayName() { return getName(); }

    // ------------------------------------------------------------------ 명령 태그(Entity.tags, NBT "Tags")

    /** 바닐라 Entity.tags: HashSet 순서가 저장·목록 순서가 된다. */
    private final java.util.Set<String> commandTags = new java.util.HashSet<>();

    @Override public java.util.Set<String> getTags() { return java.util.Collections.unmodifiableSet(commandTags); }

    @Override public boolean addTag(String tag) { return commandTags.size() < 1024 && commandTags.add(tag); }

    @Override public boolean removeTag(String tag) { return commandTags.remove(tag); }

    private static final int FLAG_SHIFT_KEY_DOWN = 1;

    @Override
    public boolean isShiftKeyDown() { return ((Byte) entityData.get(DATA_SHARED_FLAGS) & 1 << FLAG_SHIFT_KEY_DOWN) != 0; }

    public void setShiftKeyDown(boolean v) { setSharedFlag(FLAG_SHIFT_KEY_DOWN, v); }

    /** getRootVehicle. */
    public final Entity rootVehicle() {
        Entity e = this;
        while (e.vehicle != null) e = e.vehicle;
        return e;
    }

    /** getRootVehicle 의 승객판(플레이어 승객은 자기 탈것에서 올라간다). */
    public static Entity rootVehicleOf(Rider r) { return r instanceof Entity e ? e.rootVehicle() : r.vehicle() == null ? null : r.vehicle().rootVehicle(); }

    /** isPassengerOfSameVehicle: 뿌리 탈것이 같다. */
    public final boolean isPassengerOfSameVehicle(Rider r) { return rootVehicle() == rootVehicleOf(r); }

    /** hasIndirectPassenger: 승객의 승객까지. */
    public final boolean hasIndirectPassenger(Rider r) {
        Entity v = r.vehicle();
        if (v == null) return false;
        return v == this || hasIndirectPassenger(v);
    }

    /** getIndirectPassengers(승객의 승객까지, 앞에서부터). */
    public final void collectIndirectPassengers(java.util.List<Rider> out) {
        for (Rider r : passengers) {
            out.add(r);
            if (r instanceof Entity e) e.collectIndirectPassengers(out);
        }
    }

    /** startRiding(vehicle) = startRiding(vehicle, false). */
    public boolean startRiding(Entity v) { return startRiding(v, false); }

    /** startRiding(vehicle, force): 승객 쪽 canRide·탈것 쪽 canAddPassenger 를 보고(force 면 생략) 탄다. */
    public boolean startRiding(Entity v, boolean force) { return mount(this, v, force); }

    /**
     * 바닐라 Entity.startRiding 본문(Rider 공용): 같은 탈것이면 거짓, 탈것이 승객을 못 받으면 거짓, 탈것 사슬에 내가 있으면 거짓.
     * 이미 탔으면 내리고, 자세를 서기로 바꾸고, 연결한다. TODO(발전 과제): started_riding 트리거.
     */
    static boolean mount(Rider r, Entity v, boolean force) {
        if (v == r.vehicle()) return false;
        if (!v.couldAcceptPassenger()) return false;
        for (Entity e = v; e.vehicle != null; e = e.vehicle) if (e.vehicle == r) return false;
        if (!force && !(r.canRide(v) && v.canAddPassenger(r))) return false;
        if (r.isPassenger()) r.stopRiding();
        if (r instanceof Entity e) e.setPose(POSE_STANDING);
        r.setVehicleField(v);
        v.addPassenger(r);
        return true;
    }

    /** canRide(vehicle): 웅크리지 않았고 다시 타기 기다림이 끝났다. */
    @Override
    public boolean canRide(Entity v) { return !isShiftKeyDown() && boardingCooldown <= 0; }

    /** canAddPassenger: 기본은 승객이 없을 때만. */
    protected boolean canAddPassenger(Rider r) { return passengers.isEmpty(); }

    /** couldAcceptPassenger. */
    protected boolean couldAcceptPassenger() { return true; }

    /** addPassenger: 서버에서 플레이어는 첫 승객이 플레이어가 아니면 맨 앞에 넣는다(조종자가 된다). TODO(게임 이벤트): entity_mount. */
    protected void addPassenger(Rider r) {
        if (r.vehicle() != this) throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        if (passengers.isEmpty()) {
            passengers = java.util.List.of(r);
        } else {
            java.util.ArrayList<Rider> l = new java.util.ArrayList<>(passengers);
            if (r instanceof Player && !(firstPassenger() instanceof Player)) l.add(0, r);
            else l.add(r);
            passengers = java.util.List.copyOf(l);
        }
    }

    /** removePassenger: 빼고 그 승객의 다시 타기 기다림을 60 틱으로. TODO(게임 이벤트): entity_dismount. */
    protected void removePassenger(Rider r) {
        if (r.vehicle() == this) throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        if (passengers.size() == 1 && passengers.get(0) == r) {
            passengers = java.util.List.of();
        } else {
            java.util.ArrayList<Rider> l = new java.util.ArrayList<>(passengers.size());
            for (Rider p : passengers) if (p != r) l.add(p);
            passengers = java.util.List.copyOf(l);
        }
        r.setBoardingCooldown(60);
    }

    /** removeVehicle: 연결만 끊는다. */
    public final void removeVehicle() { unlinkVehicle(this); }

    static void unlinkVehicle(Rider r) {
        Entity v = r.vehicle();
        if (v != null) {
            r.setVehicleField(null);
            v.removePassenger(r);
        }
    }

    /** stopRiding: 기본은 removeVehicle(살아 있는 것은 내릴 자리로 옮긴다 — LivingEntity). */
    @Override
    public void stopRiding() { removeVehicle(); }

    /** ejectPassengers: 뒤에서부터 내린다. */
    public void ejectPassengers() {
        for (int i = passengers.size() - 1; i >= 0; i--) passengers.get(i).stopRiding();
    }

    /** positionRider(passenger): 내 승객이면 setPos 로 옮긴다. */
    public final void positionRider(Rider r) {
        if (hasPassenger(r)) placeRider(r);
    }

    /** positionRider(passenger, moveFunction): 승객 위치 = 탈것의 승객 부착점 − 승객의 탈것 부착점. 덮어쓰는 종류는 뒤에 몸 회전 등을 더 맞춘다. */
    protected void placeRider(Rider r) {
        Vec3 at = getPassengerRidingPosition(r);
        Vec3 off = r.vehicleAttachmentPoint(this);
        r.setRidingPos(at.x() - off.x(), at.y() - off.y(), at.z() - off.z());
    }

    /** getPassengerRidingPosition: 위치 + getPassengerAttachmentPoint(승객, 몸 크기, 1). */
    public Vec3 getPassengerRidingPosition(Rider r) { return new Vec3(x, y, z).add(getPassengerAttachmentPoint(r, 1.0F)); }

    /** getPassengerAttachmentPoint(passenger, dimensions, scale): 기본은 그 승객 순번의 부착점. */
    protected Vec3 getPassengerAttachmentPoint(Rider r, float scale) { return getDefaultPassengerAttachmentPoint(r); }

    /** getDefaultPassengerAttachmentPoint(vehicle, passenger, attachments): 승객 순번(없으면 -1 → 0 번)으로 고른다. */
    protected final Vec3 getDefaultPassengerAttachmentPoint(Rider r) {
        return EntityAttachments.passenger(type, passengers.indexOf(r), yRot, dimH, dimensionScale());
    }

    /** getVehicleAttachmentPoint(vehicle): 종류의 탈것 부착점. */
    @Override
    public Vec3 vehicleAttachmentPoint(Entity v) { return EntityAttachments.vehicle(type, yRot, dimensionScale()); }

    @Override
    public void setRidingPos(double nx, double ny, double nz) { setPos(nx, ny, nz); }

    /** 지금 몸 크기가 종류 크기의 몇 배인가(EntityDimensions.scale — 부착점에 곱한다). */
    protected float dimensionScale() { return 1.0F; }

    /** dismountTo: 내린 자리로 옮긴다. */
    public void dismountTo(double nx, double ny, double nz) { teleportTo(nx, ny, nz); }

    /** getCollisionHorizontalEscapeVector(vehicleWidth, passengerWidth, yaw): 그 방향으로 두 몸이 겹치지 않을 만큼(사각형 가장자리까지). */
    protected static Vec3 collisionHorizontalEscapeVector(double vehicleWidth, double passengerWidth, float yaw) {
        double d = (vehicleWidth + passengerWidth + 1.0E-5F) / 2.0;
        float f = -org.domi.furnace.server.entity.ai.control.Mth.sin(yaw * (float) (Math.PI / 180.0));
        float g = org.domi.furnace.server.entity.ai.control.Mth.cos(yaw * (float) (Math.PI / 180.0));
        float h = Math.max(Math.abs(f), Math.abs(g));
        return new Vec3(f * d / h, 0.0, g * d / h);
    }

    /** getDismountLocationForPassenger: 기본은 내 몸통 꼭대기 가운데. */
    public Vec3 getDismountLocationForPassenger(Rider r) { return new Vec3(x, bb.maxY(), z); }

    /** getControllingPassenger: 조종하는 승객(기본 없음). */
    public Rider getControllingPassenger() { return null; }

    public final boolean hasControllingPassenger() { return getControllingPassenger() != null; }

    /** getControlledVehicle: 내가 조종하는 탈것. */
    public final Entity getControlledVehicle() { return vehicle != null && vehicle.getControllingPassenger() == this ? vehicle : null; }

    /** dismountsUnderwater: dismounts_underwater 종류 태그. */
    public boolean dismountsUnderwater() { return typeInTag("minecraft:dismounts_underwater"); }

    @Override
    public boolean canControlVehicle() { return !typeInTag("minecraft:non_controlling_rider"); }

    public boolean typeInTag(String tag) {
        int[] ids = EntityTypeTags.ids(tag);
        if (ids == null) return false;
        for (int i : ids) if (i == type.id()) return true;
        return false;
    }

    /** rideTick: 속도를 지우고 틱한 뒤 탈것이 다시 자리를 잡아 준다. */
    public void rideTick() {
        setDeltaMovement(0.0, 0.0, 0.0);
        tick();
        if (vehicle != null) vehicle.positionRider(this);
    }

    /** 승객을 저장할 묶음(saveAsPassenger): 저장하지 않는 상태면 null. */
    public NbtCompound saveAsPassenger() {
        if (!shouldSave()) return null;
        if (isRemoved() && !removalReason.shouldSave()) return null;
        NbtCompound tag = new NbtCompound();
        tag.putString("id", type.name());
        return saveWithoutId(tag);
    }

    // ------------------------------------------------------------------ 추적(ServerEntity 가 쓴다)

    /** 스폰 패킷의 data 칸(getAddEntityPacket). 대부분 0. */
    public int spawnData() { return 0; }

    /** 스폰 패킷을 add_entity 대신 따로 보내는 종류(경험치 구슬 등)의 패킷. 기본 null. */
    public org.domi.furnace.server.net.protocol.Packet customSpawnPacket(double baseX, double baseY, double baseZ) { return null; }

    /** 시야 갱신 대상인가(broadcastToPlayer). */
    public boolean broadcastToPlayer(Player p) { return true; }

    /** 플레이어가 닿았을 때(playerTouch). */
    public void playerTouch(Player p) {}

    // ------------------------------------------------------------------ 수학

    public static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static int ceil(double v) {
        int i = (int) v;
        return v > i ? i + 1 : i;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + " " + String.format("%.2f, %.2f, %.2f", x, y, z) + "]";
    }
}
