// 바닐라 FishingHook(낚시찌): 날다가 물에 닿으면 떠다니고(BOBBING), 엔티티에 걸리면 따라다닌다(HOOKED_IN_ENTITY).
// 물속에서 catchingFish 로 입질 타이머(timeUntilLured → timeUntilHooked → nibble)를 돌리고, retrieve 에서 보상·끌어당기기.
// 입자(sendParticles)는 아직 서버에 없어 보내지 않지만 그에 딸린 난수 호출은 그대로 한다.
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.FluidTicker;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class FishingHook extends Projectile {
    public static final int DATA_HOOKED_ENTITY = 8, DATA_BITING = 9;

    enum FishHookState { FLYING, HOOKED_IN_ENTITY, BOBBING }

    enum OpenWaterType { ABOVE_WATER, INSIDE_WATER, INVALID }

    private static final int WATER = B.id("water"), LILY_PAD = B.id("lily_pad");

    /** syncronizedRandom: 틱마다 UUID 하위 비트 ^ 게임 시간으로 다시 심는다. */
    private final LegacyRandomSource syncronizedRandom = new LegacyRandomSource(0L);
    private boolean biting;
    int outOfWaterTime;
    int life;
    int nibble;
    int timeUntilLured;
    int timeUntilHooked;
    private float fishAngle;
    boolean openWater = true;
    /** 걸린 엔티티(Entity 또는 Player). */
    private Object hookedIn;
    FishHookState currentState = FishHookState.FLYING;
    final int luck;
    final int lureSpeed;

    public FishingHook(ChunkLevel level, int id) { this(level, id, 0, 0); }

    private FishingHook(ChunkLevel level, int id, int luck, int lureSpeed) {
        super(EntityType.FISHING_BOBBER, level, id);
        this.luck = Math.max(0, luck);
        this.lureSpeed = Math.max(0, lureSpeed);
    }

    /** FishingHook(player, level, luck, lureSpeed): 눈 높이에서 시선 쪽으로 0.6 + 흔들림(엔티티 난수 triangle 3번). */
    public FishingHook(Player owner, ChunkLevel level, int id, int luck, int lureSpeed) {
        this(level, id, luck, lureSpeed);
        setOwner(owner);
        float xr = owner.pitch(), yr = owner.yaw();
        float f2 = Mth.cos(-yr * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f3 = Mth.sin(-yr * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f4 = -Mth.cos(-xr * (float) (Math.PI / 180.0));
        float f5 = Mth.sin(-xr * (float) (Math.PI / 180.0));
        moveTo(owner.x() - f3 * 0.3, owner.eyeY(), owner.z() - f2 * 0.3, yr, xr);
        double vx = -f3, vy = Math.clamp(-(f5 / f4), -5.0F, 5.0F), vz = -f2;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double mx = 0.6 / len + random.triangle(0.5, 0.0103365);
        double my = 0.6 / len + random.triangle(0.5, 0.0103365);
        double mz = 0.6 / len + random.triangle(0.5, 0.0103365);
        vx *= mx;
        vy *= my;
        vz *= mz;
        setDeltaMovement(vx, vy, vz);
        setYRot((float) (Mth.atan2(vx, vz) * 180.0F / (float) Math.PI));
        setXRot((float) (Mth.atan2(vy, Math.sqrt(vx * vx + vz * vz)) * 180.0F / (float) Math.PI));
        yRotO = yRot();
        xRotO = xRot();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_HOOKED_ENTITY, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_BITING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    /** DATA_BITING 설정 + onSyncedDataUpdated: 물면 아래로 잡아당긴다(동기 난수). */
    private void setBiting(boolean v) {
        boolean changed = !Boolean.valueOf(v).equals(entityData.get(DATA_BITING));
        entityData.set(DATA_BITING, v);
        if (!changed) return;
        biting = v;
        if (biting) setDeltaMovement(vx(), -0.4F * nextFloat(syncronizedRandom, 0.6F, 1.0F), vz());
    }

    public boolean isBiting() { return biting; }

    public Object hookedIn() { return hookedIn; }

    public boolean isOpenWaterFishing() { return openWater; }

    // ------------------------------------------------------------------ 소유자

    @Override
    public void setOwner(Targetable o) {
        super.setOwner(o);
        updateOwnerInfo(this);
    }

    public Player getPlayerOwner() { return getOwner() instanceof Player p ? p : null; }

    private void updateOwnerInfo(FishingHook hook) {
        Player p = getPlayerOwner();
        if (p != null) p.fishing = hook;
    }

    @Override
    public void remove(RemovalReason reason) {
        updateOwnerInfo(null);
        super.remove(reason);
    }

    @Override
    public int spawnData() {
        Targetable o = getOwner();
        return o == null ? id() : o.id();
    }

    /** 저장하지 않는다(EntityType noSave). */
    @Override public boolean shouldSave() { return false; }

    @Override protected void addAdditionalSaveData(NbtCompound tag) {}

    @Override protected void readAdditionalSaveData(NbtCompound tag) {}

    // ------------------------------------------------------------------ 틱

    @Override
    public void tick() {
        syncronizedRandom.setSeed(uuid().getLeastSignificantBits() ^ level.gameTime());
        super.tick();
        Player owner = getPlayerOwner();
        if (owner == null) {
            discard();
            return;
        }
        if (shouldStopFishing(owner)) return;
        if (onGround()) {
            life++;
            if (life >= 1200) {
                discard();
                return;
            }
        } else {
            life = 0;
        }
        float f = 0.0F;
        Pos pos = blockPosition();
        int fluid = level.getFluidState(pos);
        if (Fluids.isWater(fluid)) f = FluidTicker.height(fluid, level, pos);
        boolean inWater = f > 0.0F;
        if (currentState == FishHookState.FLYING) {
            if (hookedIn != null) {
                setDeltaMovement(0.0, 0.0, 0.0);
                currentState = FishHookState.HOOKED_IN_ENTITY;
                return;
            }
            if (inWater) {
                setDeltaMovement(vx() * 0.3, vy() * 0.2, vz() * 0.3);
                currentState = FishHookState.BOBBING;
                return;
            }
            checkCollision();
        } else {
            if (currentState == FishHookState.HOOKED_IN_ENTITY) {
                if (hookedIn != null) {
                    if (!hookedRemoved()) setPos(hx(), hy() + hHeight() * 0.8, hz());
                    else {
                        setHookedEntity(null);
                        currentState = FishHookState.FLYING;
                    }
                }
                return;
            }
            if (currentState == FishHookState.BOBBING) {
                double d = y() + vy() - pos.y() - f;
                if (Math.abs(d) < 0.01) d += Math.signum(d) * 0.1;
                setDeltaMovement(vx() * 0.9, vy() - d * random.nextFloat() * 0.2, vz() * 0.9);
                if (nibble <= 0 && timeUntilHooked <= 0) openWater = true;
                else openWater = openWater && outOfWaterTime < 10 && calculateOpenWater(pos);
                if (inWater) {
                    outOfWaterTime = Math.max(0, outOfWaterTime - 1);
                    if (biting) setDeltaMovement(vx(), vy() + -0.1 * syncronizedRandom.nextFloat() * syncronizedRandom.nextFloat(), vz());
                    catchingFish(pos);
                } else {
                    outOfWaterTime = Math.min(10, outOfWaterTime + 1);
                }
            }
        }
        if (!Fluids.isWater(fluid)) setDeltaMovement(vx(), vy() - 0.03, vz());
        move(MOVER_SELF, vx(), vy(), vz());
        updateRotation();
        if (currentState == FishHookState.FLYING && (onGround() || horizontalCollision)) setDeltaMovement(0.0, 0.0, 0.0);
        setDeltaMovement(vx() * 0.92, vy() * 0.92, vz() * 0.92);
        setPos(x(), y(), z());   // reapplyPosition
    }

    /** Projectile.updateRotation. */
    private void updateRotation() {
        double h = Math.sqrt(vx() * vx() + vz() * vz());
        setXRot(lerpRotation(xRotO, (float) (Mth.atan2(vy(), h) * 180.0F / (float) Math.PI)));
        setYRot(lerpRotation(yRotO, (float) (Mth.atan2(vx(), vz()) * 180.0F / (float) Math.PI)));
    }

    /** shouldStopFishing: 소유자가 살아 있고 어느 손에든 낚싯대가 있고 32 칸 안이면 계속, 아니면 없앤다. */
    boolean shouldStopFishing(Player p) {
        var inv = p.inventory();
        boolean main = isRod(inv.get(inv.heldWindowSlot())), off = isRod(inv.get(45));
        if (!p.isDisconnected() && p.isAlive() && (main || off) && distanceToSqr(p) <= 1024.0) return false;
        discard();
        return true;
    }

    static boolean isRod(ItemStack s) { return ItemTools.itemName(s).equals("minecraft:fishing_rod"); }

    private double distanceToSqr(Targetable t) {
        double dx = x() - t.x(), dy = y() - t.y(), dz = z() - t.z();
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------------ 맞음

    /** checkCollision: getHitResultOnMoveVector — 블록, 맞출 수 있는 엔티티, 살아 있는 아이템 엔티티 중 가장 가까운 것. */
    private void checkCollision() {
        double fx = x(), fy = y(), fz = z(), tx = fx + vx(), ty = fy + vy(), tz = fz + vz();
        Clip.Result r = Clip.clipCollider(level, fx, fy, fz, tx, ty, tz);
        Hit block = null;
        if (!r.miss()) {
            block = new Hit(null, r.pos(), r.face(), r.x(), r.y(), r.z());
            tx = r.x();
            ty = r.y();
            tz = r.z();
        }
        Hit ent = findHitEntity(fx, fy, fz, tx, ty, tz);
        double best = ent == null ? Double.MAX_VALUE : sq(ent.x() - fx, ent.y() - fy, ent.z() - fz);
        ItemEntity item = null;
        AABB box = boundingBox().expandTowards(vx(), vy(), vz()).inflate(1.0);
        for (Entity e : level.getEntities(this, box, x -> x instanceof ItemEntity && x.isAlive())) {
            AABB bb = e.boundingBox().inflate(0.3F);
            double[] p = bb.contains(fx, fy, fz) ? new double[] {fx, fy, fz} : bb.clip(fx, fy, fz, tx, ty, tz);
            if (p == null) continue;
            double d = sq(p[0] - fx, p[1] - fy, p[2] - fz);
            if (d < best) {
                best = d;
                item = (ItemEntity) e;
            }
        }
        if (item != null) setHookedEntity(item);
        else if (ent != null) onHit(ent);
        else if (block != null) onHit(block);
    }

    private static double sq(double a, double b, double c) { return a * a + b * b + c * c; }

    @Override
    protected void onHitEntity(Hit hit) { setHookedEntity(hit.target()); }

    /** onHitBlock: 맞은 점까지의 거리만큼으로 속도를 줄인다. */
    @Override
    protected void onHitBlock(Hit hit) {
        super.onHitBlock(hit);   // 바닐라는 먼저 맞은 블록의 onProjectileHit
        double len = Math.sqrt(sq(vx(), vy(), vz()));
        double dist = Math.sqrt(sq(hit.x() - x(), hit.y() - y(), hit.z() - z()));
        if (len < 1.0E-4) setDeltaMovement(0.0, 0.0, 0.0);
        else setDeltaMovement(vx() / len * dist, vy() / len * dist, vz() / len * dist);
    }

    private void setHookedEntity(Object e) {
        hookedIn = e;
        entityData.set(DATA_HOOKED_ENTITY, e == null ? 0 : hId() + 1);
    }

    private int hId() { return hookedIn instanceof Entity e ? e.id() : ((Targetable) hookedIn).id(); }

    private double hx() { return hookedIn instanceof Entity e ? e.x() : ((Targetable) hookedIn).x(); }

    private double hy() { return hookedIn instanceof Entity e ? e.y() : ((Targetable) hookedIn).y(); }

    private double hz() { return hookedIn instanceof Entity e ? e.z() : ((Targetable) hookedIn).z(); }

    private float hHeight() { return hookedIn instanceof Entity e ? e.bbHeight() : ((Targetable) hookedIn).bbHeight(); }

    private boolean hookedRemoved() {
        if (hookedIn instanceof Entity e) return e.isRemoved() || e.level() != level;
        return hookedIn instanceof Player p && p.isDisconnected();
    }

    // ------------------------------------------------------------------ 입질(catchingFish)

    /** catchingFish: 비 +1(25%), 하늘 막힘 −1(50%) 속도로 타이머를 줄인다. 난수 호출 순서는 바닐라와 같다. */
    void catchingFish(Pos pos) {
        int i = 1;
        Pos above = pos.above();
        if (random.nextFloat() < 0.25F && level.isRainingAt(above)) i++;
        if (random.nextFloat() < 0.5F && !level.canSeeSky(above)) i--;
        if (nibble > 0) {
            nibble--;
            if (nibble <= 0) {
                timeUntilLured = 0;
                timeUntilHooked = 0;
                setBiting(false);
            }
        } else if (timeUntilHooked > 0) {
            timeUntilHooked -= i;
            if (timeUntilHooked > 0) {
                fishAngle = fishAngle + (float) random.triangle(0.0, 9.188);
                float a = fishAngle * (float) (Math.PI / 180.0);
                float s = Mth.sin(a), c = Mth.cos(a);
                double px = x() + s * timeUntilHooked * 0.1F;
                double py = Entity.floor(y()) + 1.0F;
                double pz = z() + c * timeUntilHooked * 0.1F;
                int st = level.getBlockState(new Pos(Entity.floor(px), Entity.floor(py - 1.0), Entity.floor(pz)));
                if (B.is(st, WATER)) {
                    if (random.nextFloat() < 0.15F) { /* BUBBLE 입자 */ }
                    // FISHING 입자 2개
                }
            } else {
                playSound("entity.fishing_bobber.splash", 0.25F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
                // BUBBLE·FISHING 입자
                nibble = nextInt(random, 20, 40);
                setBiting(true);
            }
        } else if (timeUntilLured > 0) {
            timeUntilLured -= i;
            float chance = 0.15F;
            if (timeUntilLured < 20) chance += (20 - timeUntilLured) * 0.05F;
            else if (timeUntilLured < 40) chance += (40 - timeUntilLured) * 0.02F;
            else if (timeUntilLured < 60) chance += (60 - timeUntilLured) * 0.01F;
            if (random.nextFloat() < chance) {
                float a = nextFloat(random, 0.0F, 360.0F) * (float) (Math.PI / 180.0);
                float r = nextFloat(random, 25.0F, 60.0F);
                double px = x() + Mth.sin(a) * r * 0.1;
                double py = Entity.floor(y()) + 1.0F;
                double pz = z() + Mth.cos(a) * r * 0.1;
                int st = level.getBlockState(new Pos(Entity.floor(px), Entity.floor(py - 1.0), Entity.floor(pz)));
                if (B.is(st, WATER)) random.nextInt(2);   // SPLASH 입자 개수 2 + nextInt(2)
            }
            if (timeUntilLured <= 0) {
                fishAngle = nextFloat(random, 0.0F, 360.0F);
                timeUntilHooked = nextInt(random, 20, 80);
            }
        } else {
            timeUntilLured = nextInt(random, 100, 600);
            timeUntilLured -= lureSpeed;
        }
    }

    /** Mth.nextInt(random, min, max). */
    static int nextInt(org.domi.furnace.server.block.RandomSource r, int min, int max) { return min >= max ? min : r.nextInt(max - min + 1) + min; }

    /** Mth.nextFloat(random, min, max). */
    static float nextFloat(org.domi.furnace.server.block.RandomSource r, float min, float max) { return min >= max ? min : r.nextFloat() * (max - min) + min; }

    // ------------------------------------------------------------------ 열린 물(calculateOpenWater)

    /** calculateOpenWater: 아래 1칸부터 위 2칸까지 5×5 층이 물속 → 물 위 순으로만 이어져야 한다. */
    boolean calculateOpenWater(Pos pos) {
        OpenWaterType prev = OpenWaterType.INVALID;
        for (int dy = -1; dy <= 2; dy++) {
            OpenWaterType t = openWaterTypeForArea(pos.x() - 2, pos.y() + dy, pos.z() - 2, pos.x() + 2, pos.z() + 2);
            switch (t) {
                case ABOVE_WATER -> { if (prev == OpenWaterType.INVALID) return false; }
                case INSIDE_WATER -> { if (prev == OpenWaterType.ABOVE_WATER) return false; }
                case INVALID -> { return false; }
            }
            prev = t;
        }
        return true;
    }

    private OpenWaterType openWaterTypeForArea(int x0, int y, int z0, int x1, int z1) {
        OpenWaterType acc = null;
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                OpenWaterType t = openWaterTypeForBlock(new Pos(x, y, z));
                acc = acc == null ? t : acc == t ? acc : OpenWaterType.INVALID;
            }
        }
        return acc == null ? OpenWaterType.INVALID : acc;
    }

    private OpenWaterType openWaterTypeForBlock(Pos pos) {
        int state = level.getBlockState(pos);
        if (!B.isAir(state) && !B.is(state, LILY_PAD)) {
            int fluid = level.getFluidState(pos);
            return Fluids.isWater(fluid) && Fluids.isSource(fluid) && BlockBehaviors.of(state).getCollisionShape(state, level, pos, null).isEmpty()
                    ? OpenWaterType.INSIDE_WATER : OpenWaterType.INVALID;
        }
        return OpenWaterType.ABOVE_WATER;
    }

    // ------------------------------------------------------------------ 거두기(retrieve)

    /** 전리품 조건이 보는 낚시찌(type_specific.in_open_water). */
    public org.domi.furnace.server.loot.LootEntity lootEntity() {
        double[] at = {x(), y(), z()};
        boolean ow = openWater;
        return new org.domi.furnace.server.loot.LootEntity() {
            public String type() { return "minecraft:fishing_bobber"; }
            public double[] position() { return at; }
            public Object typeSpecific(String key) { return key.equals("in_open_water") ? ow : null; }
        };
    }

    /**
     * retrieve(rod): 걸린 엔티티는 끌어당기고(3/5), 입질 중이면 낚시 전리품 표를 평가해 아이템을 플레이어 쪽으로 던지고 경험치(1~6)를 준다(1).
     * 땅에 있으면 2. 낚시찌는 없앤다. 반환값은 낚싯대 내구도 소모량.
     */
    public int retrieve(ItemStack rod) {
        Player p = getPlayerOwner();
        if (p == null || shouldStopFishing(p)) return 0;
        int i = 0;
        if (hookedIn != null) {
            pullEntity(hookedIn);
            fishingTrigger(p, rod, List.of());
            broadcastEntityEvent((byte) 31);
            i = hookedIn instanceof ItemEntity ? 3 : 5;
        } else if (nibble > 0) {
            // TODO(행운 속성): player.getLuck() — 플레이어 속성이 아직 없어 0
            List<ItemStack> list = level.fishingLoot(lootEntity(), x(), y(), z(), rod, luck + 0.0F);
            fishingTrigger(p, rod, list);
            for (ItemStack s : list) {
                var ie = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), s);
                double dx = p.x() - x(), dy = p.y() - y(), dz = p.z() - z();
                ie.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
                level.addFreshEntity(ie);
                level.addFreshEntity(new ExperienceOrb(level, level.nextEntityId(), p.x(), p.y() + 0.5, p.z() + 0.5, random.nextInt(6) + 1));
                if (ItemContext.isInTag("minecraft:item", "minecraft:fishes", s.itemId())) p.awardStat("minecraft:fish_caught", 1);
            }
            i = 1;
        }
        if (onGround()) i = 2;
        discard();
        return i;
    }

    /** FISHING_ROD_HOOKED.trigger(player, rod, hook, loot): 문맥 엔티티는 걸린 엔티티(없으면 낚시찌 자신). */
    private void fishingTrigger(Player p, ItemStack rod, List<ItemStack> loot) {
        Object h = hookedIn;
        var ent = h instanceof Rider r ? KillCredit.lootOf(r) : KillCredit.snapshot(lootEntity());
        var hookedItem = h instanceof ItemEntity ie ? org.domi.furnace.server.ServerCore.lootStackOf(ie.getItem()) : null;
        var rodSnap = org.domi.furnace.server.ServerCore.lootStackOf(rod);
        var lootSnap = new java.util.ArrayList<org.domi.furnace.server.loot.LootStack>();
        for (ItemStack s : loot) lootSnap.add(org.domi.furnace.server.ServerCore.lootStackOf(s));
        p.triggerAdvancement(a -> a.triggerFishing(rodSnap, ent, hookedItem, lootSnap));
    }

    /** pullEntity: 소유자 쪽으로 차이 × 0.1 만큼 속도를 더한다. */
    private void pullEntity(Object e) {
        Targetable o = getOwner();
        if (o == null) return;
        double dx = (o.x() - x()) * 0.1, dy = (o.y() - y()) * 0.1, dz = (o.z() - z()) * 0.1;
        if (e instanceof Entity en) en.addDeltaMovement(dx, dy, dz);
        else if (e instanceof Targetable t) t.addDeltaMovement(dx, dy, dz);
    }
}
