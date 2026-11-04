// 바닐라 1.21.1 AbstractMinecart(+ VehicleEntity 의 피해 부분). 레거시 레일 이동: moveAlongTrack(경사 가속·방향 맞춤·전동 레일 가속/제동·
// 레일 중심선에 붙이기·높이 보정)과 레일 밖 comeOffTrack. 서버가 전부 움직인다(플레이어가 타도 조종 승객이 없어 move_vehicle 을 받지 않는다).
// 바닐라처럼 Entity.baseTick 을 부르지 않는다(불 피해·탑승 기다림 감소가 없다).
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.block.types.BaseRailBlock;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class AbstractMinecart extends Entity {

    /** VehicleEntity: 8 맞은 시간, 9 맞은 방향, 10 피해. AbstractMinecart: 11 표시 블록(상태 번호), 12 표시 높이, 13 사용자 표시 블록 여부. */
    public static final int DATA_ID_HURT = 8, DATA_ID_HURTDIR = 9, DATA_ID_DAMAGE = 10, DATA_ID_DISPLAY_BLOCK = 11, DATA_ID_DISPLAY_OFFSET = 12,
            DATA_ID_CUSTOM_DISPLAY = 13;

    /** AbstractMinecart.Type: 순번 그대로. */
    public enum Type { RIDEABLE, CHEST, FURNACE, TNT, SPAWNER, HOPPER, COMMAND_BLOCK }

    private static final Prop SHAPE = B.prop("shape"), POWERED = B.prop("powered");
    private static final int POWERED_RAIL = B.id("powered_rail"), ACTIVATOR_RAIL = B.id("activator_rail");

    /** EXITS: 레일 모양별 두 출구(Vec3i). 바닐라 표 그대로. */
    private static int[][] exits(String shape) {
        return switch (shape) {
            case "north_south" -> new int[][] {{0, 0, -1}, {0, 0, 1}};
            case "east_west" -> new int[][] {{-1, 0, 0}, {1, 0, 0}};
            case "ascending_east" -> new int[][] {{-1, -1, 0}, {1, 0, 0}};
            case "ascending_west" -> new int[][] {{-1, 0, 0}, {1, -1, 0}};
            case "ascending_north" -> new int[][] {{0, 0, -1}, {0, -1, 1}};
            case "ascending_south" -> new int[][] {{0, -1, -1}, {0, 0, 1}};
            case "south_east" -> new int[][] {{0, 0, 1}, {1, 0, 0}};
            case "south_west" -> new int[][] {{0, 0, 1}, {-1, 0, 0}};
            case "north_west" -> new int[][] {{0, 0, -1}, {-1, 0, 0}};
            case "north_east" -> new int[][] {{0, 0, -1}, {1, 0, 0}};
            default -> throw new IllegalArgumentException(shape);
        };
    }

    private boolean flipped;
    private boolean onRails;

    protected AbstractMinecart(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        // blocksBuilding = true
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ID_HURT, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_ID_HURTDIR, SetEntityData.SERIALIZER_INT, 1);
        entityData.define(DATA_ID_DAMAGE, SetEntityData.SERIALIZER_FLOAT, 0.0F);
        entityData.define(DATA_ID_DISPLAY_BLOCK, SetEntityData.SERIALIZER_INT, 0);   // Block.getId(AIR)
        entityData.define(DATA_ID_DISPLAY_OFFSET, SetEntityData.SERIALIZER_INT, 6);
        entityData.define(DATA_ID_CUSTOM_DISPLAY, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    /** createMinecart(level, x, y, z, type, stack, player): 종류별 수레를 그 자리에(xo 까지) 만든다. TODO(아이템 컴포넌트): 이름·엔티티 데이터. */
    public static AbstractMinecart create(ChunkLevel level, double x, double y, double z, Type type) {
        AbstractMinecart m = switch (type) {
            case CHEST -> new MinecartChest(level, level.nextEntityId());
            case FURNACE -> new MinecartFurnace(level, level.nextEntityId());
            case TNT -> new MinecartTNT(level, level.nextEntityId());
            case SPAWNER -> new MinecartSpawner(level, level.nextEntityId());
            case HOPPER -> new MinecartHopper(level, level.nextEntityId());
            case COMMAND_BLOCK -> new MinecartCommandBlock(level, level.nextEntityId());
            default -> new Minecart(level, level.nextEntityId());
        };
        m.setPos(x, y, z);
        m.xo = x;
        m.yo = y;
        m.zo = z;
        return m;
    }

    public abstract Type getMinecartType();

    // ------------------------------------------------------------------ 데이터

    public int getHurtTime() { return (Integer) entityData.get(DATA_ID_HURT); }

    public void setHurtTime(int v) { entityData.set(DATA_ID_HURT, v); }

    public int getHurtDir() { return (Integer) entityData.get(DATA_ID_HURTDIR); }

    public void setHurtDir(int v) { entityData.set(DATA_ID_HURTDIR, v); }

    public float getDamage() { return (Float) entityData.get(DATA_ID_DAMAGE); }

    public void setDamage(float v) { entityData.set(DATA_ID_DAMAGE, v); }

    public boolean hasCustomDisplay() { return (Boolean) entityData.get(DATA_ID_CUSTOM_DISPLAY); }

    public void setCustomDisplay(boolean v) { entityData.set(DATA_ID_CUSTOM_DISPLAY, v); }

    /** getDisplayBlockState: 사용자 지정이 없으면 종류의 기본 블록. */
    public int getDisplayBlockState() { return !hasCustomDisplay() ? getDefaultDisplayBlockState() : (Integer) entityData.get(DATA_ID_DISPLAY_BLOCK); }

    public int getDefaultDisplayBlockState() { return B.defaultState("air"); }

    public int getDisplayOffset() { return !hasCustomDisplay() ? getDefaultDisplayOffset() : (Integer) entityData.get(DATA_ID_DISPLAY_OFFSET); }

    public int getDefaultDisplayOffset() { return 6; }

    public void setDisplayBlockState(int state) {
        entityData.set(DATA_ID_DISPLAY_BLOCK, state);
        setCustomDisplay(true);
    }

    public void setDisplayOffset(int v) {
        entityData.set(DATA_ID_DISPLAY_OFFSET, v);
        setCustomDisplay(true);
    }

    public boolean isOnRails() { return onRails; }

    public boolean isFlipped() { return flipped; }

    // ------------------------------------------------------------------ 성질

    @Override public boolean isPushable() { return true; }

    @Override public boolean isPickable() { return !isRemoved(); }

    /** canCollideWith = Boat.canVehicleCollide. */
    public boolean canCollideWith(Entity e) { return (e.canBeCollidedWith() || e.isPushable()) && !isPassengerOfSameVehicle(e); }

    @Override protected double defaultGravity() { return isInWater() ? 0.005 : 0.04; }

    /** getMaxSpeed: 물속 4, 아니면 8 블록/초. */
    protected double getMaxSpeed() { return (isInWater() ? 4.0 : 8.0) / 20.0; }

    /** getBlockSpeedFactor: 레일 위면 1. */
    @Override
    protected float getBlockSpeedFactor() {
        return B.isTag(level.getBlockState(blockPosition()), "minecraft:rails") ? 1.0F : super.getBlockSpeedFactor();
    }

    /** getDropItem: 부서질 때 떨어뜨릴 아이템 이름. */
    protected abstract String dropItem();

    /** getMotionDirection: 뒤집혔으면 반대 방향의 시계 방향, 아니면 보는 방향의 시계 방향. */
    public int getMotionDirection() {
        int d = Dir.fromYRot(yRot());
        return flipped ? Dir.clockWise(Dir.opposite(d)) : Dir.clockWise(d);
    }

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
        // TODO(게임 이벤트): entity_damage
        boolean creative = attacker instanceof Player p && p.vitals().isCreative();
        if ((creative || !(getDamage() > 40.0F)) && !shouldSourceDestroy(source)) {
            if (creative) discard();
        } else {
            destroy(source, attacker);
        }
        return true;
    }

    /** shouldSourceDestroy: 기본 아니오(TNT 수레는 불·폭발). */
    protected boolean shouldSourceDestroy(Damage source) { return false; }

    /** destroy(source) = destroy(getDropItem()). attacker 는 부순 쪽(없으면 null). */
    protected void destroy(Damage source, Targetable attacker) { destroyAsItem(dropItem()); }

    /** destroy(item): 지우고 엔티티 드롭 규칙이면 그 아이템(TODO(이름표): 사용자 지정 이름을 아이템 이름으로). */
    protected final void destroyAsItem(String item) {
        kill();
        if (level.gameRule("doEntityDrops")) spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", item.substring(10)), 1));
    }

    /** Entity.spawnAtLocation(stack): 발 위치에 아이템 엔티티(줍기 지연 10). */
    protected void spawnAtLocation(ItemStack stack) {
        if (stack.isEmpty()) return;
        var ie = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), stack);
        ie.setDefaultPickUpDelay();
        level.addFreshEntity(ie);
    }

    /** animateHurt 의 서버 쪽 몫은 없다(클라이언트 전용). */

    // ------------------------------------------------------------------ 틱

    @Override
    public void tick() {
        if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        if (getDamage() > 0.0F) setDamage(getDamage() - 1.0F);
        checkBelowWorld();
        // TODO(차원 이동): handlePortal
        applyGravity();
        int i = floor(x()), j = floor(y()), k = floor(z());
        if (B.isTag(level.getBlockState(new Pos(i, j - 1, k)), "minecraft:rails")) j--;
        Pos pos = new Pos(i, j, k);
        int state = level.getBlockState(pos);
        onRails = BaseRailBlock.isRail(state);
        if (onRails) {
            moveAlongTrack(pos, state);
            if (B.is(state, ACTIVATOR_RAIL)) activateMinecart(i, j, k, POWERED.is(state));
        } else {
            comeOffTrack();
        }
        checkInsideBlocks();
        setXRot(0.0F);
        double d = xo - x(), e = zo - z();
        if (d * d + e * e > 0.001) {
            setYRot((float) (Mth.atan2(e, d) * 180.0 / Math.PI));
            if (flipped) setYRot(yRot() + 180.0F);
        }
        double f = Mth.wrapDegrees(yRot() - yRotO);
        if (f < -170.0 || f >= 170.0) {
            setYRot(yRot() + 180.0F);
            flipped = !flipped;
        }
        setYRot(yRot() % 360.0F);   // setRot
        setXRot(xRot() % 360.0F);
        AABB box = boundingBox().inflate(0.2F, 0.0, 0.2F);
        if (getMinecartType() == Type.RIDEABLE && vx() * vx() + vz() * vz() > 0.01) {
            List<Entity> list = level.getEntities(this, box, en -> !en.isSpectator() && en.isPushable());
            for (Entity en : list) {
                if (!(en instanceof IronGolem) && !(en instanceof AbstractMinecart) && !isVehicle() && !en.isPassenger()) en.startRiding(this);
                else en.push(this);
            }
            // 플레이어는 태우지 않고 Player.push(this)(일반 Entity.push): 수레만 밀린다. 바닐라 목록 안 순서와 달리 엔티티 뒤에 둔다.
            List<Player> players = new java.util.ArrayList<>();
            level.forEachPlayerIn(box, players::add);
            for (Player p : players) if (p.isAlive() && p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR) pushedByPlayer(p);
        } else {
            for (Entity en : level.getEntities(this, box, en -> !en.isSpectator())) {
                if (!hasPassenger(en) && en.isPushable() && en instanceof AbstractMinecart) en.push(this);
            }
        }
        updateInWaterStateAndDoFluidPushing();
        if (isInLava()) {
            lavaHurt();
            fallDistance *= 0.5F;
        }
        firstTick = false;
    }

    /** Player 가 나를 미는 일반 Entity.push(this) 의 수레 쪽 몫(플레이어 쪽 속도는 클라이언트 권위라 버린다). */
    private void pushedByPlayer(Player p) {
        if (p.vehicle() != null && Entity.rootVehicleOf(p) == rootVehicle() || noPhysics) return;
        double dx = x() - p.x(), dz = z() - p.z();
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

    /** activateMinecart(x, y, z, powered): 활성화 레일 위(종류별). */
    public void activateMinecart(int x, int y, int z, boolean powered) {}

    /** comeOffTrack: 최고 속도로 묶고, 땅이면 절반, 움직인 뒤 공중이면 0.95. */
    protected void comeOffTrack() {
        double d = getMaxSpeed();
        setDeltaMovement(Math.clamp(vx(), -d, d), vy(), Math.clamp(vz(), -d, d));
        if (onGround()) setDeltaMovement(vx() * 0.5, vy() * 0.5, vz() * 0.5);
        move(MOVER_SELF, vx(), vy(), vz());
        if (!onGround()) setDeltaMovement(vx() * 0.95, vy() * 0.95, vz() * 0.95);
    }

    /** moveAlongTrack(pos, state): 바닐라 1.21.1 그대로. */
    protected void moveAlongTrack(Pos pos, int state) {
        resetFallDistance();
        double d = x(), e = y(), f = z();
        Vec3 before = getPos(d, e, f);
        e = pos.y();
        boolean powered = false, braking = false;
        if (B.is(state, POWERED_RAIL)) {
            powered = POWERED.is(state);
            braking = !powered;
        }
        double g = 0.0078125;
        if (isInWater()) g *= 0.2;
        double mx = vx(), my = vy(), mz = vz();
        String shape = SHAPE.valueName(state);
        switch (shape) {
            case "ascending_east" -> { setDeltaMovement(mx - g, my, mz); e++; }
            case "ascending_west" -> { setDeltaMovement(mx + g, my, mz); e++; }
            case "ascending_north" -> { setDeltaMovement(mx, my, mz + g); e++; }
            case "ascending_south" -> { setDeltaMovement(mx, my, mz - g); e++; }
            default -> {}
        }
        mx = vx();
        my = vy();
        mz = vz();
        int[][] ex = exits(shape);
        int[] a = ex[0], b = ex[1];
        double h = b[0] - a[0], i = b[2] - a[2];
        double j = Math.sqrt(h * h + i * i);
        double k = mx * h + mz * i;
        if (k < 0.0) {
            h = -h;
            i = -i;
        }
        double l = Math.min(2.0, Math.sqrt(mx * mx + mz * mz));
        setDeltaMovement(l * h / j, my, l * i / j);
        if (firstPassenger() instanceof Player p) {
            Vec3 pm = riderDeltaMovement(p);
            double m = pm.x() * pm.x() + pm.z() * pm.z();
            double n = vx() * vx() + vz() * vz();
            if (m > 1.0E-4 && n < 0.01) {
                setDeltaMovement(vx() + pm.x() * 0.1, vy(), vz() + pm.z() * 0.1);
                braking = false;
            }
        }
        if (braking) {
            double o = Math.sqrt(vx() * vx() + vz() * vz());
            if (o < 0.03) setDeltaMovement(0.0, 0.0, 0.0);
            else setDeltaMovement(vx() * 0.5, vy() * 0.0, vz() * 0.5);
        }
        double p = pos.x() + 0.5 + a[0] * 0.5, q = pos.z() + 0.5 + a[2] * 0.5;
        double r = pos.x() + 0.5 + b[0] * 0.5, s = pos.z() + 0.5 + b[2] * 0.5;
        h = r - p;
        i = s - q;
        double t;
        if (h == 0.0) {
            t = f - pos.z();
        } else if (i == 0.0) {
            t = d - pos.x();
        } else {
            double u = d - p, v = f - q;
            t = (u * h + v * i) * 2.0;
        }
        d = p + h * t;
        f = q + i * t;
        setPos(d, e, f);
        double w = isVehicle() ? 0.75 : 1.0;
        double max = getMaxSpeed();
        move(MOVER_SELF, Math.clamp(w * vx(), -max, max), 0.0, Math.clamp(w * vz(), -max, max));
        if (a[1] != 0 && floor(x()) - pos.x() == a[0] && floor(z()) - pos.z() == a[2]) {
            setPos(x(), y() + a[1], z());
        } else if (b[1] != 0 && floor(x()) - pos.x() == b[0] && floor(z()) - pos.z() == b[2]) {
            setPos(x(), y() + b[1], z());
        }
        applyNaturalSlowdown();
        Vec3 after = getPos(x(), y(), z());
        if (after != null && before != null) {
            double y = (before.y() - after.y()) * 0.05;
            double z = Math.sqrt(vx() * vx() + vz() * vz());
            if (z > 0.0) setDeltaMovement(vx() * ((z + y) / z), vy(), vz() * ((z + y) / z));
            setPos(x(), after.y(), z());
        }
        int aa = floor(x()), ab = floor(z());
        if (aa != pos.x() || ab != pos.z()) {
            double ac = Math.sqrt(vx() * vx() + vz() * vz());
            setDeltaMovement(ac * (aa - pos.x()), vy(), ac * (ab - pos.z()));
        }
        if (powered) {
            double ad = Math.sqrt(vx() * vx() + vz() * vz());
            if (ad > 0.01) {
                setDeltaMovement(vx() + vx() / ad * 0.06, vy(), vz() + vz() / ad * 0.06);
            } else {
                double ae = vx(), af = vz();
                if ("east_west".equals(shape)) {
                    if (isRedstoneConductor(pos.west())) ae = 0.02;
                    else if (isRedstoneConductor(pos.east())) ae = -0.02;
                } else {
                    if (!"north_south".equals(shape)) return;
                    if (isRedstoneConductor(pos.north())) af = 0.02;
                    else if (isRedstoneConductor(pos.south())) af = -0.02;
                }
                setDeltaMovement(ae, vy(), af);
            }
        }
    }

    /**
     * 탄 플레이어의 서버 쪽 deltaMovement(가정): 바닐라 ServerPlayer.doTick 의 travel 이 입력(xxa·zza × 0.98)을 공중 속도(0.02, 달리기 0.026)로
     * moveRelative 한 뒤 공중 마찰 0.91 을 곱한 값. 입력 패킷이 매 틱 온다고 본다.
     */
    static Vec3 riderDeltaMovement(Player p) {
        double ix = p.inputXxa() * 0.98F, iz = p.inputZza() * 0.98F;
        double len = ix * ix + iz * iz;
        if (len < 1.0E-7) return Vec3.ZERO;
        float speed = p.sprinting() ? 0.025999999F : 0.02F;
        if (len > 1.0) {
            double n = Math.sqrt(len);
            ix /= n;
            iz /= n;
        }
        ix *= speed;
        iz *= speed;
        float sin = Mth.sin(p.riderYRot() * (float) (Math.PI / 180.0)), cos = Mth.cos(p.riderYRot() * (float) (Math.PI / 180.0));
        double x = ix * cos - iz * sin, z = iz * cos + ix * sin;
        return new Vec3(x * 0.91F, 0.0, z * 0.91F);
    }

    private boolean isRedstoneConductor(Pos p) {
        int s = level.getBlockState(p);
        return B.behavior(s).isRedstoneConductor(s, level, p);
    }

    /** applyNaturalSlowdown: 탔으면 0.997, 아니면 0.96(세로는 0), 물속이면 0.95 배 더. */
    protected void applyNaturalSlowdown() {
        double d = isVehicle() ? 0.997 : 0.96;
        double x = vx() * d, y = vy() * 0.0, z = vz() * d;
        if (isInWater()) {
            x *= 0.95F;
            y *= 0.95F;
            z *= 0.95F;
        }
        setDeltaMovement(x, y, z);
    }

    /** getPos(x, y, z): 그 칸이 레일이면 레일 선 위의 점(경사는 높이까지), 아니면 null. */
    public Vec3 getPos(double x, double y, double z) {
        int i = floor(x), j = floor(y), k = floor(z);
        if (B.isTag(level.getBlockState(new Pos(i, j - 1, k)), "minecraft:rails")) j--;
        int state = level.getBlockState(new Pos(i, j, k));
        if (!BaseRailBlock.isRail(state)) return null;
        int[][] ex = exits(SHAPE.valueName(state));
        int[] a = ex[0], b = ex[1];
        double d = i + 0.5 + a[0] * 0.5, e = j + 0.0625 + a[1] * 0.5, f = k + 0.5 + a[2] * 0.5;
        double g = i + 0.5 + b[0] * 0.5, h = j + 0.0625 + b[1] * 0.5, l = k + 0.5 + b[2] * 0.5;
        double m = g - d, n = (h - e) * 2.0, o = l - f;
        double t;
        if (m == 0.0) {
            t = z - k;
        } else if (o == 0.0) {
            t = x - i;
        } else {
            double p = x - d, q = z - f;
            t = (p * m + q * o) * 2.0;
        }
        x = d + m * t;
        y = e + n * t;
        z = f + o * t;
        if (n < 0.0) y++;
        else if (n > 0.0) y += 0.5;
        return new Vec3(x, y, z);
    }

    // ------------------------------------------------------------------ 밀기

    /** push(entity): 수레끼리는 진행 방향과 거의 나란할 때만(화로 수레가 끄는 쪽), 나머지는 나는 반대로·상대는 1/4. */
    @Override
    public void push(Entity en) {
        if (en.noPhysics || noPhysics) return;
        if (hasPassenger(en)) return;
        double dx = en.x() - x(), dz = en.z() - z();
        double dd = dx * dx + dz * dz;
        if (!(dd >= 1.0E-4F)) return;
        dd = Math.sqrt(dd);
        dx /= dd;
        dz /= dd;
        double f = 1.0 / dd;
        if (f > 1.0) f = 1.0;
        dx *= f;
        dz *= f;
        dx *= 0.1F;
        dz *= 0.1F;
        dx *= 0.5;
        dz *= 0.5;
        if (en instanceof AbstractMinecart other) {
            double ox = en.x() - x(), oz = en.z() - z();
            double ol = Math.sqrt(ox * ox + oz * oz);
            double nx = ol < 1.0E-5F ? 0.0 : ox / ol, nz = ol < 1.0E-5F ? 0.0 : oz / ol;
            double vx0 = Mth.cos(yRot() * (float) (Math.PI / 180.0)), vz0 = Mth.sin(yRot() * (float) (Math.PI / 180.0));
            double vl = Math.sqrt(vx0 * vx0 + vz0 * vz0);
            double fx = vl < 1.0E-5F ? 0.0 : vx0 / vl, fz = vl < 1.0E-5F ? 0.0 : vz0 / vl;
            double dot = Math.abs(nx * fx + nz * fz);
            if (dot < 0.8F) return;
            double ax = vx(), ay = vy(), az = vz();
            double bx = en.vx(), by = en.vy(), bz = en.vz();
            if (other.getMinecartType() == Type.FURNACE && getMinecartType() != Type.FURNACE) {
                setDeltaMovement(ax * 0.2, ay, az * 0.2);
                push(bx - dx, 0.0, bz - dz);
                en.setDeltaMovement(bx * 0.95, by, bz * 0.95);
            } else if (other.getMinecartType() != Type.FURNACE && getMinecartType() == Type.FURNACE) {
                en.setDeltaMovement(bx * 0.2, by, bz * 0.2);
                en.push(ax + dx, 0.0, az + dz);
                setDeltaMovement(ax * 0.95, ay, az * 0.95);
            } else {
                double mx = (bx + ax) / 2.0, mz = (bz + az) / 2.0;
                setDeltaMovement(ax * 0.2, ay, az * 0.2);
                push(mx - dx, 0.0, mz - dz);
                en.setDeltaMovement(bx * 0.2, by, bz * 0.2);
                en.push(mx + dx, 0.0, mz + dz);
            }
        } else {
            push(-dx, 0.0, -dz);
            en.push(dx / 4.0, 0.0, dz / 4.0);
        }
    }

    /** push(player): 플레이어가 걸어 들어와 미는 쪽(Entity 판 push 와 같은 식, 플레이어 쪽 1/4 은 클라이언트 권위라 버린다). */
    @Override
    public void pushByPlayer(Player p) { pushedBy(p); }

    public void pushedBy(Player p) {
        if (noPhysics || hasPassenger(p)) return;
        double dx = p.x() - x(), dz = p.z() - z();
        double dd = dx * dx + dz * dz;
        if (!(dd >= 1.0E-4F)) return;
        dd = Math.sqrt(dd);
        dx /= dd;
        dz /= dd;
        double f = 1.0 / dd;
        if (f > 1.0) f = 1.0;
        dx *= f;
        dz *= f;
        dx *= 0.1F;
        dz *= 0.1F;
        dx *= 0.5;
        dz *= 0.5;
        push(-dx, 0.0, -dz);
    }

    // ------------------------------------------------------------------ 승객

    /** getPassengerAttachmentPoint: 주민·떠돌이 상인은 (0, 0, 0), 나머지는 기본 부착점. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Rider r, float scale) {
        if (r instanceof Villager || r instanceof WanderingTrader) return Vec3.ZERO;
        return super.getPassengerAttachmentPoint(r, scale);
    }

    /** 자세별 내릴 높이 후보(POSE_DISMOUNT_HEIGHTS): 서기·웅크리기 0, 1, -1, 헤엄 0, 1. */
    private static final int[][] DISMOUNT_HEIGHTS = {{0, 1, -1}, {0, 1, -1}, {0, 1}};

    /** getDismountLocationForPassenger: 진행 방향 둘레 8 칸 × 높이 후보에서 바닥(오를 수 있는 블록 제외)이 맞고 몸이 끼지 않는 곳. */
    @Override
    public Vec3 getDismountLocationForPassenger(Rider r) {
        int dir = getMotionDirection();
        int[][] offsets = offsetsForDirection(dir);
        Pos pos = blockPosition();
        float[][] poses = r instanceof Player ? new float[][] {{0.6F, 1.8F}, {0.6F, 1.5F}, {0.6F, 0.6F}} : new float[][] {{((Entity) r).bbWidth(), r.bbHeight()}};
        for (int pi = 0; pi < poses.length; pi++) {
            float hw = Math.min(poses[pi][0], 1.0F) / 2.0F;
            for (int dy : DISMOUNT_HEIGHTS[pi]) {
                for (int[] off : offsets) {
                    Pos m = new Pos(pos.x() + off[0], pos.y() + dy, pos.z() + off[1]);
                    double h = floorHeight(m);
                    if (DismountHelper.isBlockFloorValid(h)) {
                        double vx = m.x() + 0.5, vy = m.y() + h, vz = m.z() + 0.5;
                        AABB box = new AABB(vx - hw, vy, vz - hw, vx + hw, vy + poses[pi][1], vz + hw);
                        if (DismountHelper.canDismountTo(level, box)) return new Vec3(vx, vy, vz);   // TODO(자세): 고른 자세로 승객 자세 바꾸기
                    }
                }
            }
        }
        // 바닐라는 여기서 머리 위 천장을 보아 자세만 고른다(TODO(자세)). 위치는 기본.
        return super.getDismountLocationForPassenger(r);
    }

    /** DismountHelper.offsetsForDirection(dir): 옆·옆·뒤옆·뒤옆·앞옆·앞옆·뒤·앞 순. */
    static int[][] offsetsForDirection(int dir) {
        int a = Dir.clockWise(dir), b = Dir.opposite(a), c = Dir.opposite(dir);
        return new int[][] {
            {Dir.DX[a], Dir.DZ[a]}, {Dir.DX[b], Dir.DZ[b]},
            {Dir.DX[c] + Dir.DX[a], Dir.DZ[c] + Dir.DZ[a]}, {Dir.DX[c] + Dir.DX[b], Dir.DZ[c] + Dir.DZ[b]},
            {Dir.DX[dir] + Dir.DX[a], Dir.DZ[dir] + Dir.DZ[a]}, {Dir.DX[dir] + Dir.DX[b], Dir.DZ[dir] + Dir.DZ[b]},
            {Dir.DX[c], Dir.DZ[c]}, {Dir.DX[dir], Dir.DZ[dir]}
        };
    }

    /** getBlockFloorHeight(nonClimbableShape(m), nonClimbableShape(m.below())). */
    private double floorHeight(Pos m) {
        Shape s = nonClimbableShape(m);
        if (!s.isEmpty()) return s.bounds()[4];
        Shape below = nonClimbableShape(m.below());
        double d = below.isEmpty() ? Double.NEGATIVE_INFINITY : below.bounds()[4];
        return d >= 1.0 ? d - 1.0 : Double.NEGATIVE_INFINITY;
    }

    /** DismountHelper.nonClimbableShape: 오를 수 있는 블록·열린 다락문은 빈 모양. */
    private Shape nonClimbableShape(Pos p) {
        int s = level.getBlockState(p);
        if (B.isTag(s, "minecraft:climbable")) return Shape.EMPTY;
        Prop open = B.prop("open");
        if (B.isTag(s, "minecraft:trapdoors") && open.has(s) && open.is(s)) return Shape.EMPTY;
        return BlockInfo.get().collisionShape(s);
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        if (hasCustomDisplay()) {
            tag.putBoolean("CustomDisplayTile", true);
            tag.putCompound("DisplayState", org.domi.furnace.server.block.entity.PistonMovingBlockEntity.writeBlockState(getDisplayBlockState()));
            tag.putInt("DisplayOffset", getDisplayOffset());
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.getBoolean("CustomDisplayTile")) {
            setDisplayBlockState(tag.tags().get("DisplayState") instanceof NbtCompound c
                    ? org.domi.furnace.server.block.entity.PistonMovingBlockEntity.readBlockState(c) : B.defaultState("air"));
            setDisplayOffset(tag.getIntOr0("DisplayOffset"));
        }
    }
}
