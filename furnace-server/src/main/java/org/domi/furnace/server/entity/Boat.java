// 바닐라 Boat(+ VehicleEntity 의 피해 부분). 1.21.1 은 minecraft:boat 하나에 나무 종류(Type 데이터)를 둔다.
// 서버 권위가 약하다: 플레이어가 조종하면(isControlledByLocalInstance 거짓) 서버는 속도를 지우고 위치는 move_vehicle·노 상태는 paddle_boat 를 따른다.
// 비었거나 몹이 조종하면 서버가 floatBoat(상태별 마찰·부력) 뒤 move 한다. controlBoat(입력으로 회전·가속)는 클라이언트 몫이라 서버에 없다.
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.FluidTicker;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Boat extends Entity implements Leashable {

    /** VehicleEntity: 8 맞은 시간, 9 맞은 방향, 10 피해. Boat: 11 종류, 12·13 왼쪽·오른쪽 노, 14 거품 시간. */
    public static final int DATA_ID_HURT = 8, DATA_ID_HURTDIR = 9, DATA_ID_DAMAGE = 10, DATA_ID_TYPE = 11, DATA_ID_PADDLE_LEFT = 12,
            DATA_ID_PADDLE_RIGHT = 13, DATA_ID_BUBBLE_TIME = 14;

    /** Boat.Type: 순번이 데이터 값. 판자·아이템 이름. */
    public enum Type {
        OAK("oak"), SPRUCE("spruce"), BIRCH("birch"), JUNGLE("jungle"), ACACIA("acacia"), CHERRY("cherry"), DARK_OAK("dark_oak"), MANGROVE("mangrove"),
        BAMBOO("bamboo");

        public final String name;

        Type(String name) { this.name = name; }

        public String planks() { return "minecraft:" + name + "_planks"; }

        /** byName: 없으면 참나무. */
        public static Type byName(String n) {
            for (Type t : values()) if (t.name.equals(n)) return t;
            return OAK;
        }

        public static Type byId(int id) { return id >= 0 && id < values().length ? values()[id] : OAK; }
    }

    /** Boat.Status. */
    public enum Status { IN_WATER, UNDER_WATER, UNDER_FLOWING_WATER, ON_LAND, IN_AIR }

    private final float[] paddlePositions = new float[2];
    private float invFriction;
    private float outOfControlTicks;
    private float deltaRotation;
    private double waterLevel;
    private float landFriction;
    private Status status, oldStatus;
    private double lastYd;
    private boolean isAboveBubbleColumn, bubbleColumnDirectionIsDown;

    public Boat(ChunkLevel level, int id) { this(EntityType.BOAT, level, id); }

    protected Boat(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        // blocksBuilding = true
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ID_HURT, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_ID_HURTDIR, SetEntityData.SERIALIZER_INT, 1);
        entityData.define(DATA_ID_DAMAGE, SetEntityData.SERIALIZER_FLOAT, 0.0F);
        entityData.define(DATA_ID_TYPE, SetEntityData.SERIALIZER_INT, Type.OAK.ordinal());
        entityData.define(DATA_ID_PADDLE_LEFT, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_ID_PADDLE_RIGHT, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_ID_BUBBLE_TIME, SetEntityData.SERIALIZER_INT, 0);
    }

    // ------------------------------------------------------------------ 데이터

    public Type getVariant() { return Type.byId((Integer) entityData.get(DATA_ID_TYPE)); }

    public void setVariant(Type t) { entityData.set(DATA_ID_TYPE, t.ordinal()); }

    public int getHurtTime() { return (Integer) entityData.get(DATA_ID_HURT); }

    public void setHurtTime(int v) { entityData.set(DATA_ID_HURT, v); }

    public int getHurtDir() { return (Integer) entityData.get(DATA_ID_HURTDIR); }

    public void setHurtDir(int v) { entityData.set(DATA_ID_HURTDIR, v); }

    public float getDamage() { return (Float) entityData.get(DATA_ID_DAMAGE); }

    public void setDamage(float v) { entityData.set(DATA_ID_DAMAGE, v); }

    private int getBubbleTime() { return (Integer) entityData.get(DATA_ID_BUBBLE_TIME); }

    private void setBubbleTime(int v) { entityData.set(DATA_ID_BUBBLE_TIME, v); }

    public void setPaddleState(boolean left, boolean right) {
        entityData.set(DATA_ID_PADDLE_LEFT, left);
        entityData.set(DATA_ID_PADDLE_RIGHT, right);
    }

    /** getPaddleState(i): 노를 젓고 있고 조종 승객이 있다. */
    public boolean getPaddleState(int side) {
        return (Boolean) entityData.get(side == 0 ? DATA_ID_PADDLE_LEFT : DATA_ID_PADDLE_RIGHT) && getControllingPassenger() != null;
    }

    public Status status() { return status; }

    // ------------------------------------------------------------------ 성질

    @Override public boolean canBeCollidedWith() { return true; }

    @Override public boolean isPushable() { return true; }

    @Override public boolean isPickable() { return !isRemoved(); }

    /** canVehicleCollide(this, e). */
    public boolean canCollideWith(Entity e) { return (e.canBeCollidedWith() || e.isPushable()) && !isPassengerOfSameVehicle(e); }

    @Override protected double defaultGravity() { return 0.04; }

    /** getDropItem: 나무 종류별 배(대나무는 뗏목). */
    public String dropItem() { return "minecraft:" + getVariant().name + (getVariant() == Type.BAMBOO ? "_raft" : "_boat"); }

    protected int getMaxPassengers() { return 2; }

    /** getSinglePassengerXOffset: 혼자 탈 때 앞뒤 자리(상자 배는 0.15). */
    protected float getSinglePassengerXOffset() { return 0.0F; }

    /** hasEnoughSpaceFor: 나보다 좁다. */
    protected boolean hasEnoughSpaceFor(Entity e) { return e.bbWidth() < bbWidth(); }

    /** canAddPassenger: 두 자리 미만이고 눈높이가 물에 잠기지 않았다. */
    @Override
    protected boolean canAddPassenger(Rider r) { return passengers().size() < getMaxPassengers() && !isEyeInWater(); }

    /** getControllingPassenger: 첫 승객이 살아 있는 것(플레이어 포함)이면 그가 조종한다. */
    @Override
    public Rider getControllingPassenger() {
        Rider r = firstPassenger();
        return r instanceof LivingEntity || r instanceof Player ? r : super.getControllingPassenger();
    }

    /** isControlledByLocalInstance(서버): 플레이어가 조종하면 거짓(클라이언트가 움직인다), 아니면 참. */
    public boolean isControlledByLocalInstance() { return !(getControllingPassenger() instanceof Player); }

    public boolean isUnderWater() { return status == Status.UNDER_WATER || status == Status.UNDER_FLOWING_WATER; }

    // ------------------------------------------------------------------ 피해(VehicleEntity.hurt)

    @Override
    public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isRemoved()) return true;
        if (isInvulnerableTo(source)) return false;
        setHurtDir(-getHurtDir());
        setHurtTime(10);
        markHurt();
        setDamage(getDamage() + amount * 10.0F);
        boolean creative = attacker instanceof Player p && p.vitals().isCreative();
        if ((creative || !(getDamage() > 40.0F))) {
            if (creative) discard();
        } else {
            destroy();
        }
        return true;
    }

    /** destroy(source): 지우고 엔티티 드롭 규칙이면 배 아이템(TODO(이름표): 사용자 지정 이름을 아이템 이름으로). */
    protected void destroy() {
        kill();
        if (level.gameRule("doEntityDrops")) spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", dropItem().substring(10)), 1));
    }

    /** Entity.spawnAtLocation(stack): 발 위치에 아이템 엔티티(줍기 지연 10). */
    protected void spawnAtLocation(ItemStack stack) {
        if (stack.isEmpty()) return;
        var ie = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), stack);
        ie.setDefaultPickUpDelay();
        level.addFreshEntity(ie);
    }

    // ------------------------------------------------------------------ 틱

    @Override
    public void tick() {
        oldStatus = status;
        status = getStatus();
        if (status != Status.UNDER_WATER && status != Status.UNDER_FLOWING_WATER) outOfControlTicks = 0.0F;
        else outOfControlTicks++;
        if (outOfControlTicks >= 60.0F) ejectPassengers();
        if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        if (getDamage() > 0.0F) setDamage(getDamage() - 1.0F);
        super.tick();
        // tickLerp: 서버는 lerpTo 를 받지 않는다(lerpSteps 0)
        if (isControlledByLocalInstance()) {
            if (!(firstPassenger() instanceof Player)) setPaddleState(false, false);
            floatBoat();
            move(MOVER_SELF, vx(), vy(), vz());
        } else {
            setDeltaMovement(0.0, 0.0, 0.0);
        }
        tickBubbleColumn();
        for (int i = 0; i <= 1; i++) {
            if (getPaddleState(i)) {
                if (!isSilent() && paddlePositions[i] % (float) (Math.PI * 2) <= (float) (Math.PI / 4)
                        && (paddlePositions[i] + (float) (Math.PI / 8)) % (float) (Math.PI * 2) >= (float) (Math.PI / 4)) {
                    String sound = getPaddleSound();
                    if (sound != null) {
                        Vec3 view = viewVector();
                        double dx = i == 1 ? -view.z() : view.z(), dz = i == 1 ? view.x() : -view.x();
                        level.playSound(null, x() + dx, y(), z() + dz, sound, soundSource(), 1.0F, 0.8F + 0.4F * random.nextFloat());
                    }
                }
                paddlePositions[i] += (float) (Math.PI / 8);
            } else {
                paddlePositions[i] = 0.0F;
            }
        }
        checkInsideBlocks();
        List<Entity> near = level.getEntities(this, boundingBox().inflate(0.2F, -0.01F, 0.2F), e -> !e.isSpectator() && e.isPushable());
        if (!near.isEmpty()) {
            boolean canBoard = !(getControllingPassenger() instanceof Player);
            for (Entity e : near) {
                if (e.hasPassenger(this)) continue;
                if (canBoard && passengers().size() < getMaxPassengers() && !e.isPassenger() && hasEnoughSpaceFor(e) && e instanceof LivingEntity
                        && !(e instanceof WaterAnimal)) {
                    e.startRiding(this);
                } else {
                    push(e);
                }
            }
        }
    }

    /** calculateViewVector(xRot, yRot). */
    private Vec3 viewVector() {
        float f = xRot() * (float) (Math.PI / 180.0), g = -yRot() * (float) (Math.PI / 180.0);
        float h = Mth.cos(g), i = Mth.sin(g), j = Mth.cos(f), k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    /** tickBubbleColumn(서버): 거품 기둥 위면 60 틱을 세어 끝에 아래로 끌어내리거나(승객도 내린다) 위로 띄운다. */
    private void tickBubbleColumn() {
        if (!isAboveBubbleColumn) setBubbleTime(0);
        int t = getBubbleTime();
        if (t > 0) {
            setBubbleTime(--t);
            int j = 60 - t - 1;
            if (j > 0 && t == 0) {
                setBubbleTime(0);
                if (bubbleColumnDirectionIsDown) {
                    setDeltaMovement(vx(), vy() - 0.7, vz());
                    ejectPassengers();
                } else {
                    boolean player = false;
                    for (Rider r : passengers()) if (r instanceof Player) player = true;
                    setDeltaMovement(vx(), player ? 2.7 : 0.6, vz());
                }
            }
            isAboveBubbleColumn = false;
        }
    }

    /** onAboveBubbleCol: 거품 시간을 걸고, 입자·소리 자리의 난수를 바닐라처럼 쓴다(서버에선 보이지 않는다). */
    @Override
    public void onAboveBubbleCol(boolean down) {
        isAboveBubbleColumn = true;
        bubbleColumnDirectionIsDown = down;
        if (getBubbleTime() == 0) setBubbleTime(60);
        random.nextFloat();   // 입자 x
        random.nextFloat();   // 입자 z
        if (random.nextInt(20) == 0) {
            random.nextFloat();   // playLocalSound 높낮이 — 서버에서는 소리가 나지 않는다
            // TODO(게임 이벤트): splash
        }
    }

    /** getPaddleSound: 물 쪽이면 paddle_water, 땅이면 paddle_land. */
    protected String getPaddleSound() {
        return switch (getStatus()) {
            case IN_WATER, UNDER_WATER, UNDER_FLOWING_WATER -> "minecraft:entity.boat.paddle_water";
            case ON_LAND -> "minecraft:entity.boat.paddle_land";
            default -> null;
        };
    }

    /** getStatus: 물속 → 물 위 → 땅(바닥 마찰) → 공중. */
    private Status getStatus() {
        Status under = isUnderwater();
        if (under != null) {
            waterLevel = boundingBox().maxY();
            return under;
        }
        if (checkInWater()) return Status.IN_WATER;
        float f = getGroundFriction();
        if (f > 0.0F) {
            landFriction = f;
            return Status.ON_LAND;
        }
        return Status.IN_AIR;
    }

    /** getWaterLevelAbove: 몸통 칸들 위로 물이 가득 찬 층을 지나 처음 덜 찬 층의 수면 높이. */
    public float getWaterLevelAbove() {
        AABB bb = boundingBox();
        int x0 = floor(bb.minX()), x1 = ceil(bb.maxX()), y0 = floor(bb.maxY()), y1 = ceil(bb.maxY() - lastYd), z0 = floor(bb.minZ()), z1 = ceil(bb.maxZ());
        int y = y0;
        layers:
        for (; y < y1; y++) {
            float f = 0.0F;
            for (int x = x0; x < x1; x++) {
                for (int z = z0; z < z1; z++) {
                    Pos p = new Pos(x, y, z);
                    int fluid = level.getFluidState(p);
                    if (Fluids.isWater(fluid)) f = Math.max(f, FluidTicker.height(fluid, level, p));
                    if (f >= 1.0F) continue layers;
                }
            }
            if (f < 1.0F) return y + f;
        }
        return y1 + 1;
    }

    /** getGroundFriction: 바닥 바로 아래(0.001) 얇은 판과 겹치는 블록(수련잎 제외)의 미끄러움 평균(모서리 칸 제외). */
    public float getGroundFriction() {
        AABB bb = boundingBox();
        AABB below = new AABB(bb.minX(), bb.minY() - 0.001, bb.minZ(), bb.maxX(), bb.minY(), bb.maxZ());
        int x0 = floor(below.minX()) - 1, x1 = ceil(below.maxX()) + 1, y0 = floor(below.minY()) - 1, y1 = ceil(below.maxY()) + 1;
        int z0 = floor(below.minZ()) - 1, z1 = ceil(below.maxZ()) + 1;
        float sum = 0.0F;
        int n = 0;
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                int edge = (x != x0 && x != x1 - 1 ? 0 : 1) + (z != z0 && z != z1 - 1 ? 0 : 1);
                if (edge == 2) continue;
                for (int y = y0; y < y1; y++) {
                    if (edge > 0 && (y == y0 || y == y1 - 1)) continue;
                    Pos p = new Pos(x, y, z);
                    int s = level.getBlockState(p);
                    if (B.is(s, "lily_pad")) continue;
                    if (BlockInfo.get().collisionShape(s).overlaps(below, x, y, z)) {
                        sum += BlockInfo.get().friction(B.block(s));
                        n++;
                    }
                }
            }
        }
        return sum / n;
    }

    /** checkInWater: 몸 밑면(0.001 위까지) 칸의 물 수면 중 가장 높은 것을 waterLevel 로, 밑면이 수면 아래면 물 위. */
    private boolean checkInWater() {
        AABB bb = boundingBox();
        int x0 = floor(bb.minX()), x1 = ceil(bb.maxX()), y0 = floor(bb.minY()), y1 = ceil(bb.minY() + 0.001), z0 = floor(bb.minZ()), z1 = ceil(bb.maxZ());
        boolean in = false;
        waterLevel = -Double.MAX_VALUE;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    Pos p = new Pos(x, y, z);
                    int fluid = level.getFluidState(p);
                    if (Fluids.isWater(fluid)) {
                        float top = y + FluidTicker.height(fluid, level, p);
                        waterLevel = Math.max(top, waterLevel);
                        in |= bb.minY() < top;
                    }
                }
            }
        }
        return in;
    }

    /** isUnderwater: 몸 꼭대기(0.001 위)가 물에 잠겼으면 — 원천이 아닌 물이 있으면 흐르는 물속. */
    private Status isUnderwater() {
        AABB bb = boundingBox();
        double top = bb.maxY() + 0.001;
        int x0 = floor(bb.minX()), x1 = ceil(bb.maxX()), y0 = floor(bb.maxY()), y1 = ceil(top), z0 = floor(bb.minZ()), z1 = ceil(bb.maxZ());
        boolean under = false;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    Pos p = new Pos(x, y, z);
                    int fluid = level.getFluidState(p);
                    if (Fluids.isWater(fluid) && top < y + FluidTicker.height(fluid, level, p)) {
                        if (!Fluids.isSource(fluid)) return Status.UNDER_FLOWING_WATER;
                        under = true;
                    }
                }
            }
        }
        return under ? Status.UNDER_WATER : null;
    }

    /** floatBoat: 공중에서 물로 막 떨어졌으면 수면 위로 올려놓고, 아니면 상태별 마찰(invFriction)과 부력. */
    private void floatBoat() {
        double gravity = -(isNoGravity() ? 0.0 : defaultGravity());
        double buoyancy = 0.0;
        invFriction = 0.05F;
        if (oldStatus == Status.IN_AIR && status != Status.IN_AIR && status != Status.ON_LAND) {
            waterLevel = y() + bbHeight() * 1.0;
            double ny = getWaterLevelAbove() - bbHeight() + 0.101;
            if (level.noCollision(boundingBox().move(0.0, ny - y(), 0.0))) {
                setPos(x(), ny, z());
                setDeltaMovement(vx(), 0.0, vz());
                lastYd = 0.0;
            }
            status = Status.IN_WATER;
        } else {
            if (status == Status.IN_WATER) {
                buoyancy = (waterLevel - y()) / bbHeight();
                invFriction = 0.9F;
            } else if (status == Status.UNDER_FLOWING_WATER) {
                gravity = -7.0E-4;
                invFriction = 0.9F;
            } else if (status == Status.UNDER_WATER) {
                buoyancy = 0.01F;
                invFriction = 0.45F;
            } else if (status == Status.IN_AIR) {
                invFriction = 0.9F;
            } else if (status == Status.ON_LAND) {
                invFriction = landFriction;
                if (getControllingPassenger() instanceof Player) landFriction /= 2.0F;
            }
            setDeltaMovement(vx() * invFriction, vy() + gravity, vz() * invFriction);
            deltaRotation *= invFriction;
            if (buoyancy > 0.0) setDeltaMovement(vx(), (vy() + buoyancy * (defaultGravity() / 0.65)) * 0.75, vz());
        }
    }

    // ------------------------------------------------------------------ 승객

    /** getPassengerAttachmentPoint: 둘이면 앞 0.2·뒤 -0.6(동물은 +0.2), 높이는 몸 높이의 1/3(뗏목은 8/9). */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Rider r, float scale) {
        float f = getSinglePassengerXOffset();
        if (passengers().size() > 1) {
            f = passengers().indexOf(r) == 0 ? 0.2F : -0.6F;
            if (r instanceof Animal) f += 0.2F;
        }
        double h = getVariant() == Type.BAMBOO ? bbHeight() * 0.8888889F : bbHeight() / 3.0F;
        return new Vec3(0.0, h, f).yRot(-yRot() * (float) (Math.PI / 180.0));
    }

    /** positionRider: 배와 함께 돌고(can_turn_in_boats 종류 제외) 시선을 ±105 도로 묶는다. 꽉 찬 배의 동물은 옆으로 앉는다. */
    @Override
    protected void placeRider(Rider r) {
        super.placeRider(r);
        if (!(r instanceof Entity e) || EntityTypeTags.ids("minecraft:can_turn_in_boats") != null && contains(EntityTypeTags.ids("minecraft:can_turn_in_boats"), e.type().id())) return;
        // TODO(플레이어 회전): 탄 플레이어의 서버 쪽 회전은 클라이언트가 보내 온 값을 쓴다
        e.setYRot(e.yRot() + deltaRotation);
        if (e instanceof LivingEntity le) le.setYHeadRot(le.yHeadRot() + deltaRotation);
        clampRotation(e);
        if (e instanceof Animal a && passengers().size() == getMaxPassengers()) {
            int j = e.id() % 2 == 0 ? 90 : 270;
            a.setYBodyRot(a.yBodyRot() + j);
            a.setYHeadRot(a.yHeadRot() + j);
        }
    }

    private static boolean contains(int[] ids, int id) {
        for (int i : ids) if (i == id) return true;
        return false;
    }

    /** clampRotation: 몸 방향을 배 방향으로, 시선은 배 기준 ±105 도. */
    protected void clampRotation(Entity e) {
        if (e instanceof LivingEntity le) le.setYBodyRot(yRot());
        float f = Mth.wrapDegrees(e.yRot() - yRot());
        float g = Math.clamp(f, -105.0F, 105.0F);
        e.yRotO += g - f;
        e.setYRot(e.yRot() + g - f);
        if (e instanceof LivingEntity le) le.setYHeadRot(e.yRot());
    }

    /** getDismountLocationForPassenger: 승객 시선 방향 옆(배 폭 × √2) 칸과 그 아래 칸의 바닥, 그 칸이 물이면 기본. */
    @Override
    public Vec3 getDismountLocationForPassenger(Rider r) {
        float pw = r instanceof Entity e ? e.bbWidth() : 0.6F;
        Vec3 v = collisionHorizontalEscapeVector(bbWidth() * (float) Math.sqrt(2.0), pw, r.riderYRot());
        double px = x() + v.x(), pz = z() + v.z();
        Pos at = new Pos(floor(px), floor(boundingBox().maxY()), floor(pz));
        Pos below = at.below();
        if (!level.isWaterAt(below)) {
            List<Vec3> spots = new java.util.ArrayList<>(2);
            double h = DismountHelper.blockFloorHeight(level, at);
            if (DismountHelper.isBlockFloorValid(h)) spots.add(new Vec3(px, at.y() + h, pz));
            double h2 = DismountHelper.blockFloorHeight(level, below);
            if (DismountHelper.isBlockFloorValid(h2)) spots.add(new Vec3(px, below.y() + h2, pz));
            float[][] poses = r instanceof Player ? new float[][] {{0.6F, 1.8F}, {0.6F, 1.5F}, {0.6F, 0.6F}} : new float[][] {{pw, r.bbHeight()}};
            for (float[] dim : poses) {
                for (Vec3 s : spots) {
                    double hw = dim[0] / 2.0F;
                    if (DismountHelper.canDismountTo(level, new AABB(s.x() - hw, s.y(), s.z() - hw, s.x() + hw, s.y() + dim[1], s.z() + hw))) return s;
                }
            }
        }
        return super.getDismountLocationForPassenger(r);
    }

    /** push(entity): 다른 배는 내 꼭대기보다 낮을 때, 나머지는 밑면이 내 밑면 이하일 때만 민다. */
    @Override
    public void push(Entity e) {
        if (e instanceof Boat) {
            if (e.boundingBox().minY() < boundingBox().maxY()) super.push(e);
        } else if (e.boundingBox().minY() <= boundingBox().minY()) {
            super.push(e);
        }
    }

    /** 플레이어가 미는 쪽: 플레이어 밑면이 내 밑면 이하일 때만. */
    @Override
    public void pushByPlayer(Player p) {
        if (p.boundingBox().minY() <= boundingBox().minY()) super.pushByPlayer(p);
    }

    /** interact: 웅크리면 넘기고, 물에 잠긴 지 60 틱 안이면 탄다. TODO(끈): Leashable 상호작용. */
    public boolean interact(Player p, int hand) {
        if (p.sneaking()) return false;
        if (outOfControlTicks < 60.0F) return p.startRiding(this, false);
        return false;
    }

    /** checkFallDamage: 땅에 3 넘게 떨어지면 부서져 판자 3·막대 2(땅이 아니면 낙하 거리만 지운다). 물 위가 아니면 떨어진 거리를 센다. */
    @Override
    protected void checkFallDamage(double dy, boolean ground, int state, Pos pos) {
        lastYd = vy();
        if (isPassenger()) return;
        if (ground) {
            if (fallDistance > 3.0F) {
                if (status != Status.ON_LAND) {
                    resetFallDistance();
                    return;
                }
                causeFallDamage(fallDistance, 1.0F);
                if (!isRemoved()) {
                    kill();
                    if (level.gameRule("doEntityDrops")) {
                        String planks = getVariant().planks().substring(10);
                        for (int i = 0; i < 3; i++) spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", planks), 1));
                        for (int i = 0; i < 2; i++) spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "stick"), 1));
                    }
                }
            }
            resetFallDistance();
        } else if (!Fluids.isWater(level.getFluidState(blockPosition().below())) && dy < 0.0) {
            fallDistance -= (float) dy;
        }
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putString("Type", getVariant().name);
        Object leash = leashHolder instanceof LeashFenceKnotEntity k ? k.pos() : leashHolder != null ? Mob.holderUuid(leashHolder) : leashPending;
        if (leash instanceof java.util.UUID u) {
            NbtCompound c = new NbtCompound();
            c.putIntArray("UUID", Entity.uuidToInts(u));
            tag.putCompound("leash", c);
        } else if (leash instanceof Pos pos) {
            tag.putIntArray("leash", new int[]{pos.x(), pos.y(), pos.z()});
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.tags().get("Type") instanceof String s) setVariant(Type.byName(s));
        Object leashNbt = tag.tags().get("leash");
        leashHolder = null;
        leashPending = null;
        if (leashNbt instanceof NbtCompound l && l.tags().get("UUID") instanceof int[] u && u.length == 4) {
            leashPending = new java.util.UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
        } else if (leashNbt instanceof int[] p && p.length == 3) {
            leashPending = new Pos(p[0], p[1], p[2]);
        }
    }

    // ------------------------------------------------------------------ 가죽끈(Leashable — 몹과 같은 규칙, 바닐라 cov 가 btk 를 구현)

    private Rider leashHolder;
    private Object leashPending;
    private boolean holderSeenHere;

    @Override public Rider getLeashHolder() { return leashHolder; }

    @Override public boolean isLeashed() { return leashHolder != null; }

    public boolean mayBeLeashed() { return leashHolder != null || leashPending != null; }

    @Override public boolean canHaveALeashAttachedToIt() { return !isLeashed(); }

    @Override
    public void setLeashedTo(Rider holder, boolean broadcast) {
        leashHolder = holder;
        holderSeenHere = holder instanceof Entity he && level.entities().contains(he);
        leashPending = null;
        if (broadcast && tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.SetEntityLink(id(), holder.id()));
        if (isPassenger()) stopRiding();
    }

    @Override
    public void dropLeash(boolean broadcast, boolean dropItem) {
        if (leashHolder != null) {
            leashHolder = null;
            if (dropItem) spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "lead"), 1));
            if (broadcast && tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.SetEntityLink(id(), 0));
        }
    }

    /** Boat.remove(cov.a): 파괴되는 제거면 끈을 푼다(끈 아이템 포함). */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && isLeashed()) dropLeash(true, true);
        super.remove(reason);
    }

    @Override protected void removeAfterChangingDimensions() { dropLeash(true, false); }

    /** Boat.elasticRangeLeashBehaviour(cov.b): 6칸 넘으면 붙잡은 쪽 방향으로 (거리 - 6) 만큼, 가는 방향이 같으면 0.15 아니면 0.2 배로 당긴다. */
    private void elasticRangeLeashBehaviour(Rider holder, float dist) {
        double dx = holder.x() - x(), dy = holder.y() - y(), dz = holder.z() - z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double s = dist - 6.0;
        double px = len == 0.0 ? 0.0 : dx / len * s, py = len == 0.0 ? 0.0 : dy / len * s, pz = len == 0.0 ? 0.0 : dz / len * s;
        boolean same = vx() * px + vy() * py + vz() * pz > 0.0;
        double k = same ? 0.15F : 0.2F;
        setDeltaMovement(vx() + px * k, vy() + py * k, vz() + pz * k);
    }

    /** Leashable.tickLeash(b_) + restoreLeashFromSave. */
    @Override
    protected void tickLeash() {
        if (leashPending != null) restoreLeashFromSave();
        if (leashHolder == null) return;
        if (leashHolder instanceof Entity he) {
            if (level.entities().contains(he)) {
                holderSeenHere = true;
            } else {
                if (holderSeenHere) dropLeash(true, true);
                return;
            }
        }
        if (!isAlive() || !Mob.holderAlive(leashHolder)) dropLeash(true, true);
        Rider holder = leashHolder;
        if (holder == null) return;
        if (holder instanceof Player p && !p.dimensionKey().equals(level.dimension())) return;
        float dist = Mob.distanceTo(this, holder);
        if (dist > 10.0) {
            dropLeash(true, true);
        } else if (dist > 6.0) {
            elasticRangeLeashBehaviour(holder, dist);
            if (vy() > -0.5 && fallDistance > 1.0F) fallDistance = 1.0F;   // checkSlowFallDistance
        }
    }

    private void restoreLeashFromSave() {
        if (leashPending instanceof java.util.UUID id) {
            Rider e = level.entities().get(id);
            if (e == null) e = level.playerByUuid(id);
            if (e != null) { setLeashedTo(e, true); return; }
        } else if (leashPending instanceof Pos pos) {
            setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, pos), true);
            return;
        }
        if (tickCount > 100) {
            spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "lead"), 1));
            leashPending = null;
        }
    }

    @Override
    public boolean interactLeash(Player p, int hand, ItemStack held) {
        if (!isAlive()) return false;
        if (leashHolder == p) {
            dropLeash(true, !p.hasInfiniteMaterials());
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_INTERACT, p);
            return true;
        }
        if (!held.isEmpty() && org.domi.furnace.server.item.ItemTools.itemName(held).equals("minecraft:lead") && canHaveALeashAttachedToIt()) {
            setLeashedTo(p, true);
            var inv = p.inventory();
            inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
            return true;
        }
        return false;
    }
}
