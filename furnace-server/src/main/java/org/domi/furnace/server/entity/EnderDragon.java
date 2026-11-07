// 바닐라 EnderDragon: 엔더 드래곤. 물리 없이 단계(DragonPhase)가 정한 목표로 날고, 몸은 파트 8개(머리·목·몸통·꼬리 셋·날개 둘)로 맞는다.
// 머리가 아닌 파트는 피해를 /4 + min(피해, 1) 로 받고, 플레이어(또는 폭발 등 always_hurts_ender_dragons)에게만 깎인다.
// 32칸 안 가장 가까운 엔드 수정이 체력을 채운다. 죽음은 DYING 단계 → 200틱 애니메이션 → 경험치(첫 처치 12000, 이후 500).
// 보스 바는 EndDragonFight 가 갖는다. 날개 밀치기·머리 물기는 엔티티 뒤에 플레이어 순서로 본다(바닐라는 섹션 순 한 목록).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.navigation.BinaryHeap;
import org.domi.furnace.server.entity.ai.navigation.Node;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.world.GameMode;

public class EnderDragon extends Mob implements Enemy {

    /** EnderDragon.checkDespawn: 비어 있다(평화로움·거리로 사라지지 않음). */
    @Override
    public void checkDespawn() {}

    public static final int DATA_PHASE = 16;
    private static final TargetingConditions CRYSTAL_DESTROY_TARGETING = TargetingConditions.forCombat().range(64.0);
    private static final int[] NODE_ADJACENCY = {6146, 8197, 8202, 16404, 32808, 32848, 65696, 131392, 131712, 263424, 526848, 525313,
            1581057, 3166214, 2138120, 6373424, 4358208, 12910976, 9044480, 9706496, 15216640, 13688832, 11763712, 8257536};

    public final double[][] positions = new double[64][3];
    public int posPointer = -1;
    public final EnderDragonPart[] subEntities;
    public final EnderDragonPart head, neck, body, tail1, tail2, tail3, wing1, wing2;
    public float oFlapTime, flapTime;
    public boolean inWall;
    public int dragonDeathTime;
    public float yRotA;
    public EndCrystal nearestCrystal;
    private EndDragonFight dragonFight;
    private Pos fightOrigin = new Pos(0, 0, 0);
    private final PhaseManager phaseManager;
    private float sittingDamageReceived;
    private final Node[] nodes = new Node[24];
    private final BinaryHeap openSet = new BinaryHeap();

    /** id 는 본체 + 파트 8 개를 연달아 잡은 첫 번호여야 한다(level.nextEntityIds(9)). */
    public EnderDragon(ChunkLevel level, int id) {
        super(EntityType.ENDER_DRAGON, level, id);
        head = new EnderDragonPart(this, "head", 0, 1.0F, 1.0F);
        neck = new EnderDragonPart(this, "neck", 1, 3.0F, 3.0F);
        body = new EnderDragonPart(this, "body", 2, 5.0F, 3.0F);
        tail1 = new EnderDragonPart(this, "tail", 3, 2.0F, 2.0F);
        tail2 = new EnderDragonPart(this, "tail", 4, 2.0F, 2.0F);
        tail3 = new EnderDragonPart(this, "tail", 5, 2.0F, 2.0F);
        wing1 = new EnderDragonPart(this, "wing", 6, 4.0F, 2.0F);
        wing2 = new EnderDragonPart(this, "wing", 7, 4.0F, 2.0F);
        subEntities = new EnderDragonPart[] {head, neck, body, tail1, tail2, tail3, wing1, wing2};
        setHealth(maxHealth());
        noPhysics = true;
        phaseManager = new PhaseManager();
        phaseManager.setPhase(DragonPhase.HOVERING);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 200.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PHASE, SetEntityData.SERIALIZER_INT, DragonPhase.HOVERING);
    }

    public PhaseManager getPhaseManager() { return phaseManager; }

    public EndDragonFight getDragonFight() { return dragonFight; }

    public void setDragonFight(EndDragonFight f) { dragonFight = f; }

    public Pos getFightOrigin() { return fightOrigin; }

    public void setFightOrigin(Pos p) { fightOrigin = p; }

    public float sittingDamageReceived() { return sittingDamageReceived; }

    /** 파트 번호(클라이언트 id)로 파트. */
    public EnderDragonPart partById(int entityId) {
        int i = entityId - id() - 1;
        return i >= 0 && i < subEntities.length ? subEntities[i] : null;
    }

    @Override public String getAmbientSound() { return "entity.ender_dragon.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.ender_dragon.hurt"; }

    @Override public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override public boolean isPickable() { return false; }

    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }

    // ------------------------------------------------------------------ 위치 기록

    /** getLatencyPos(delay, 1.0F): delay 틱 전의 (yRot, y). */
    public double[] getLatencyPos(int delay) {
        int i = posPointer - delay & 63;
        return new double[] {positions[i][0], positions[i][1], positions[i][2]};
    }

    private float getHeadYOffset() {
        if (phaseManager.getCurrentPhase().isSitting()) return -1.0F;
        double[] a = getLatencyPos(5), b = getLatencyPos(0);
        return (float) (a[1] - b[1]);
    }

    static double wrapDegrees(double d) {
        double r = d % 360.0;
        if (r >= 180.0) r -= 360.0;
        if (r < -180.0) r += 360.0;
        return r;
    }

    private float rotWrap(double d) { return (float) wrapDegrees(d); }

    // ------------------------------------------------------------------ 틱

    @Override
    protected void aiStep() {
        if (dragonFight == null) {
            EndDragonFight f = level.dragonFight();
            if (f != null && uuid().equals(f.getDragonUUID())) dragonFight = f;
        }
        oFlapTime = flapTime;
        if (isDeadOrDying()) {
            // 폭발 입자 위치 — 서버에선 보이지 않지만 난수는 쓴다
            random.nextFloat();
            random.nextFloat();
            random.nextFloat();
            return;
        }
        checkCrystals();
        float f = 0.2F / ((float) Math.sqrt(vx() * vx() + vz() * vz()) * 10.0F + 1.0F);
        f *= (float) Math.pow(2.0, vy());
        if (phaseManager.getCurrentPhase().isSitting()) flapTime += 0.1F;
        else if (inWall) flapTime += f * 0.5F;
        else flapTime += f;
        setYRot(Mth.wrapDegrees(yRot()));
        if (isNoAi()) {
            flapTime = 0.5F;
            return;
        }
        if (posPointer < 0) {
            for (double[] p : positions) {
                p[0] = yRot();
                p[1] = y();
            }
        }
        if (++posPointer == positions.length) posPointer = 0;
        positions[posPointer][0] = yRot();
        positions[posPointer][1] = y();

        DragonPhase phase = phaseManager.getCurrentPhase();
        phase.doServerTick();
        if (phaseManager.getCurrentPhase() != phase) {
            phase = phaseManager.getCurrentPhase();
            phase.doServerTick();
        }
        Vec3 target = phase.getFlyTargetLocation();
        if (target != null) {
            double d0 = target.x() - x(), d1 = target.y() - y(), d2 = target.z() - z();
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;
            float f5 = phase.getFlySpeed();
            double d4 = Math.sqrt(d0 * d0 + d2 * d2);
            if (d4 > 0.0) d1 = Math.clamp(d1 / d4, -f5, f5);
            setDeltaMovement(vx(), vy() + d1 * 0.01, vz());
            setYRot(Mth.wrapDegrees(yRot()));
            Vec3 toTarget = target.subtract(x(), y(), z()).normalize();
            Vec3 facing = new Vec3(Mth.sin(yRot() * (float) (Math.PI / 180.0)), vy(), -Mth.cos(yRot() * (float) (Math.PI / 180.0))).normalize();
            float f6 = Math.max(((float) facing.dot(toTarget) + 0.5F) / 1.5F, 0.0F);
            if (Math.abs(d0) > 1.0E-5F || Math.abs(d2) > 1.0E-5F) {
                float f7 = Math.clamp(Mth.wrapDegrees(180.0F - (float) Mth.atan2(d0, d2) * (180.0F / (float) Math.PI) - yRot()), -50.0F, 50.0F);
                yRotA *= 0.8F;
                yRotA = yRotA + f7 * phase.getTurnSpeed();
                setYRot(yRot() + yRotA * 0.1F);
            }
            float f19 = (float) (2.0 / (d3 + 1.0));
            moveRelative(0.06F * (f6 * f19 + (1.0F - f19)));
            if (inWall) move(MOVER_SELF, vx() * 0.8F, vy() * 0.8F, vz() * 0.8F);
            else move(MOVER_SELF, vx(), vy(), vz());
            Vec3 dir = new Vec3(vx(), vy(), vz()).normalize();
            double d5 = 0.8 + 0.15 * (dir.dot(facing) + 1.0) / 2.0;
            setDeltaMovement(vx() * d5, vy() * 0.91F, vz() * d5);
        }
        yBodyRot = yRot();
        double[][] old = new double[subEntities.length][];
        for (int j = 0; j < subEntities.length; j++) old[j] = new double[] {subEntities[j].x, subEntities[j].y, subEntities[j].z};
        float f12 = (float) (getLatencyPos(5)[1] - getLatencyPos(10)[1]) * 10.0F * (float) (Math.PI / 180.0);
        float f13 = Mth.cos(f12);
        float f1 = Mth.sin(f12);
        float f14 = yRot() * (float) (Math.PI / 180.0);
        float f2 = Mth.sin(f14);
        float f15 = Mth.cos(f14);
        tickPart(body, f2 * 0.5F, 0.0, -f15 * 0.5F);
        tickPart(wing1, f15 * 4.5F, 2.0, f2 * 4.5F);
        tickPart(wing2, f15 * -4.5F, 2.0, f2 * -4.5F);
        if (hurtTime == 0) {
            knockBack(wing1.boundingBox().inflate(4.0, 2.0, 4.0).move(0.0, -2.0, 0.0));
            knockBack(wing2.boundingBox().inflate(4.0, 2.0, 4.0).move(0.0, -2.0, 0.0));
            hurtAll(head.boundingBox().inflate(1.0));
            hurtAll(neck.boundingBox().inflate(1.0));
        }
        float f3 = Mth.sin(yRot() * (float) (Math.PI / 180.0) - yRotA * 0.01F);
        float f16 = Mth.cos(yRot() * (float) (Math.PI / 180.0) - yRotA * 0.01F);
        float f4 = getHeadYOffset();
        tickPart(head, f3 * 6.5F * f13, f4 + f1 * 6.5F, -f16 * 6.5F * f13);
        tickPart(neck, f3 * 5.5F * f13, f4 + f1 * 5.5F, -f16 * 5.5F * f13);
        double[] ad = getLatencyPos(5);
        for (int k = 0; k < 3; k++) {
            EnderDragonPart part = k == 0 ? tail1 : k == 1 ? tail2 : tail3;
            double[] ad1 = getLatencyPos(12 + k * 2);
            float f17 = yRot() * (float) (Math.PI / 180.0) + rotWrap(ad1[0] - ad[0]) * (float) (Math.PI / 180.0);
            float f18 = Mth.sin(f17);
            float f21 = Mth.cos(f17);
            float f23 = (k + 1) * 2.0F;
            tickPart(part, -(f2 * 1.5F + f18 * f23) * f13, ad1[1] - ad[1] - (f23 + 1.5F) * f1 + 1.5, (f15 * 1.5F + f21 * f23) * f13);
        }
        inWall = checkWalls(head.boundingBox()) | checkWalls(neck.boundingBox()) | checkWalls(body.boundingBox());
        if (dragonFight != null) dragonFight.updateDragon(this);
        for (int l = 0; l < subEntities.length; l++) {
            subEntities[l].xo = old[l][0];
            subEntities[l].yo = old[l][1];
            subEntities[l].zo = old[l][2];
        }
    }

    /** moveRelative(speed, (0, 0, -1)): 바라보는 앞쪽으로 가속. */
    private void moveRelative(float speed) {
        float s = Mth.sin(yRot() * (float) (Math.PI / 180.0)), c = Mth.cos(yRot() * (float) (Math.PI / 180.0));
        double ix = 0.0, iz = -speed;
        setDeltaMovement(vx() + (ix * c - iz * s), vy(), vz() + (iz * c + ix * s));
    }

    private void tickPart(EnderDragonPart part, double dx, double dy, double dz) { part.setPos(x() + dx, y() + dy, z() + dz); }

    /** 수정: 연결된 것이 사라졌으면 놓고, 10틱마다 1 회복. 1/10 확률로 32칸 안 가장 가까운 것을 다시 찾는다. */
    private void checkCrystals() {
        if (nearestCrystal != null) {
            if (nearestCrystal.isRemoved()) nearestCrystal = null;
            else if (tickCount % 10 == 0 && health() < maxHealth()) setHealth(health() + 1.0F);
        }
        if (random.nextInt(10) == 0) {
            EndCrystal best = null;
            double bestD = Double.MAX_VALUE;
            for (Entity e : level.getEntities(this, boundingBox().inflate(32.0), en -> en instanceof EndCrystal)) {
                double dx = e.x() - x(), dy = e.y() - y(), dz = e.z() - z();
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = (EndCrystal) e;
                }
            }
            nearestCrystal = best;
        }
    }

    private static boolean hittablePlayer(Player p) {
        return p.isAlive() && p.gameMode() != GameMode.CREATIVE && p.gameMode() != GameMode.SPECTATOR;
    }

    /** knockBack: 날개 근처를 몸통 중심에서 밀어내고, 앉아 있지 않으면 5 피해. */
    private void knockBack(AABB box) {
        AABB bb = body.boundingBox();
        double cx = (bb.minX() + bb.maxX()) / 2.0, cz = (bb.minZ() + bb.maxZ()) / 2.0;
        for (Entity e : level.getEntities(this, box, en -> en instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            double dx = e.x() - cx, dz = e.z() - cz;
            double d = Math.max(dx * dx + dz * dz, 0.1);
            e.push(dx / d * 4.0, 0.2F, dz / d * 4.0);
            if (!phaseManager.getCurrentPhase().isSitting() && le.getLastHurtByMobTimestamp() < e.tickCount - 2) {
                le.hurtByMob(new Damage("mob_attack", false, false, false), 5.0F, this);
                EnchantEffects.thorns(level, le, this);   // doPostAttackEffects
            }
        }
        List<Player> players = new ArrayList<>();
        level.forEachPlayerIn(box, p -> { if (hittablePlayer(p)) players.add(p); });
        for (Player p : players) {
            double dx = p.x() - cx, dz = p.z() - cz;
            double d = Math.max(dx * dx + dz * dz, 0.1);
            p.addDeltaMovement(dx / d * 4.0, 0.2F, dz / d * 4.0);
            // 바닐라는 플레이어도 같은 목록이라 마지막으로 맞은 틱 검사를 똑같이 받는다
            if (!phaseManager.getCurrentPhase().isSitting() && p.getLastHurtByMobTimestamp() < p.combatTicks() - 2) {
                p.hurtByMob(new Damage("mob_attack", false, false, false), 5.0F, this);
                EnchantEffects.thorns(level, p, this);
            }
        }
    }

    /** 머리·목에 닿은 것에 10 피해. */
    private void hurtAll(AABB box) {
        for (Entity e : level.getEntities(this, box, en -> en instanceof LivingEntity)) {
            ((LivingEntity) e).hurtByMob(new Damage("mob_attack", false, false, false), 10.0F, this);
            EnchantEffects.thorns(level, (LivingEntity) e, this);
        }
        List<Player> players = new ArrayList<>();
        level.forEachPlayerIn(box, p -> { if (hittablePlayer(p)) players.add(p); });
        for (Player p : players) {
            p.hurtByMob(new Damage("mob_attack", false, false, false), 10.0F, this);
            EnchantEffects.thorns(level, p, this);
        }
    }

    /** checkWalls: 상자 안 블록 — 몹 그리핑이면 dragon_immune 아닌 것을 지우고(지웠으면 무작위 칸에 2008), 못 지운 것이 있으면 벽 안. */
    private boolean checkWalls(AABB box) {
        int i = (int) Math.floor(box.minX()), j = (int) Math.floor(box.minY()), k = (int) Math.floor(box.minZ());
        int l = (int) Math.floor(box.maxX()), i1 = (int) Math.floor(box.maxY()), j1 = (int) Math.floor(box.maxZ());
        boolean blocked = false, removed = false;
        boolean griefing = level.gameRule("mobGriefing");
        for (int x = i; x <= l; x++) {
            for (int y = j; y <= i1; y++) {
                for (int z = k; z <= j1; z++) {
                    Pos p = new Pos(x, y, z);
                    int s = level.getBlockState(p);
                    if (!B.isAir(s) && !B.isTag(s, "minecraft:dragon_transparent")) {
                        if (griefing && !B.isTag(s, "minecraft:dragon_immune")) removed = level.removeBlock(p, false) || removed;
                        else blocked = true;
                    }
                }
            }
        }
        if (removed) {
            Pos p = new Pos(i + random.nextInt(l - i + 1), j + random.nextInt(i1 - j + 1), k + random.nextInt(j1 - k + 1));
            level.levelEvent(null, 2008, p, 0);
        }
        return blocked;
    }

    // ------------------------------------------------------------------ 시선

    /** Entity.calculateViewVector(xRot, yRot). */
    public Vec3 viewVector(float xRot, float yRot) {
        float f = xRot * (float) (Math.PI / 180.0), f1 = -yRot * (float) (Math.PI / 180.0);
        float f2 = Mth.cos(f1), f3 = Mth.sin(f1), f4 = Mth.cos(f), f5 = Mth.sin(f);
        return new Vec3(f3 * f4, -f5, f2 * f4);
    }

    /** getHeadLookVector(1.0F): 착륙·이륙 중이면 받침대 거리에 따라 숙이고, 앉아 있으면 -45도. */
    public Vec3 getHeadLookVector() {
        DragonPhase p = phaseManager.getCurrentPhase();
        int t = p.id();
        if (t == DragonPhase.LANDING || t == DragonPhase.TAKEOFF) {
            Pos top = DragonPhase.podiumTop(this);
            float f = Math.max((float) Math.sqrt(DragonPhase.distToCenterSqr(top, x(), y(), z())) / 4.0F, 1.0F);
            float f1 = 6.0F / f;
            return viewVector(-f1 * 1.5F * 5.0F, yRot());
        } else if (p.isSitting()) {
            return viewVector(-45.0F, yRot());
        }
        return viewVector(xRot(), yRot());
    }

    // ------------------------------------------------------------------ 경로

    /** findClosestNode(): 처음이면 노드 24개(바깥 12·가운데 8·안쪽 4)를 높이 지도 위에 만든다. */
    public int findClosestNode() {
        if (nodes[0] == null) {
            int seaLevel = seaLevel();
            for (int i = 0; i < 24; i++) {
                int j = 5;
                int l, i1;
                if (i < 12) {
                    l = Mth.floor(60.0F * Mth.cos(2.0F * (-(float) Math.PI + (float) (Math.PI / 12) * i)));
                    i1 = Mth.floor(60.0F * Mth.sin(2.0F * (-(float) Math.PI + (float) (Math.PI / 12) * i)));
                } else if (i < 20) {
                    int k = i - 12;
                    l = Mth.floor(40.0F * Mth.cos(2.0F * (-(float) Math.PI + (float) (Math.PI / 8) * k)));
                    i1 = Mth.floor(40.0F * Mth.sin(2.0F * (-(float) Math.PI + (float) (Math.PI / 8) * k)));
                    j += 10;
                } else {
                    int k1 = i - 20;
                    l = Mth.floor(20.0F * Mth.cos(2.0F * (-(float) Math.PI + (float) (Math.PI / 4) * k1)));
                    i1 = Mth.floor(20.0F * Mth.sin(2.0F * (-(float) Math.PI + (float) (Math.PI / 4) * k1)));
                }
                int j1 = Math.max(seaLevel + 10, level.getMotionBlockingNoLeavesHeight(l, i1) + j);
                nodes[i] = new Node(l, j1, i1);
            }
        }
        return findClosestNode(x(), y(), z());
    }

    /** 생성기 해수면: 엔드 0, 네더 32, 그 밖 63. */
    private int seaLevel() {
        return switch (level.dimension()) {
            case "minecraft:the_end" -> 0;
            case "minecraft:the_nether" -> 32;
            default -> 63;
        };
    }

    public int findClosestNode(double x, double y, double z) {
        float best = 10000.0F;
        int idx = 0;
        Node node = new Node(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int j = dragonFight == null || dragonFight.getCrystalsAlive() == 0 ? 12 : 0;
        for (int k = j; k < 24; k++) {
            if (nodes[k] != null) {
                float d = nodes[k].distanceToSqr(node);
                if (d < best) {
                    best = d;
                    idx = k;
                }
            }
        }
        return idx;
    }

    /** findPath(start, end, finalNode): 노드 그래프 A*. 못 닿으면 가장 가까이 간 데까지(그것도 시작점이면 null). */
    public Path findPath(int start, int end, Node finalNode) {
        for (int i = 0; i < 24; i++) {
            Node n = nodes[i];
            n.closed = false;
            n.f = 0.0F;
            n.g = 0.0F;
            n.h = 0.0F;
            n.cameFrom = null;
            n.heapIdx = -1;
        }
        Node from = nodes[start], to = nodes[end];
        from.g = 0.0F;
        from.h = from.distanceTo(to);
        from.f = from.h;
        openSet.clear();
        openSet.insert(from);
        Node closest = from;
        int j = dragonFight == null || dragonFight.getCrystalsAlive() == 0 ? 12 : 0;
        while (!openSet.isEmpty()) {
            Node cur = openSet.pop();
            if (cur.equals(to)) {
                if (finalNode != null) {
                    finalNode.cameFrom = to;
                    to = finalNode;
                }
                return reconstructPath(to);
            }
            if (cur.distanceTo(to) < closest.distanceTo(to)) closest = cur;
            cur.closed = true;
            int k = 0;
            for (int l = 0; l < 24; l++) {
                if (nodes[l] == cur) {
                    k = l;
                    break;
                }
            }
            for (int i1 = j; i1 < 24; i1++) {
                if ((NODE_ADJACENCY[k] & 1 << i1) > 0) {
                    Node next = nodes[i1];
                    if (!next.closed) {
                        float g = cur.g + cur.distanceTo(next);
                        if (!next.inOpenSet() || g < next.g) {
                            next.cameFrom = cur;
                            next.g = g;
                            next.h = next.distanceTo(to);
                            if (next.inOpenSet()) {
                                openSet.changeCost(next, next.g + next.h);
                            } else {
                                next.f = next.g + next.h;
                                openSet.insert(next);
                            }
                        }
                    }
                }
            }
        }
        if (closest == from) return null;
        if (finalNode != null) {
            finalNode.cameFrom = closest;
            closest = finalNode;
        }
        return reconstructPath(closest);
    }

    private static Path reconstructPath(Node finish) {
        List<Node> list = new ArrayList<>();
        Node node = finish;
        list.add(0, finish);
        while (node.cameFrom != null) {
            node = node.cameFrom;
            list.add(0, node);
        }
        return new Path(list, new Pos(finish.x, finish.y, finish.z), true);
    }

    // ------------------------------------------------------------------ 피해·죽음

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        // 바닐라 EnderDragon.hurt: 무적 검사 없이 몸통으로 넘긴다(무적은 reallyHurt 의 LivingEntity.hurt 가 본다).
        // 파트에서 온 피해(hurtPart)면 그 파트로
        return hurt(pendingPart != null ? pendingPart : body, source, amount, attacker);
    }

    private EnderDragonPart pendingPart;

    /** 파트가 맞았다: 직접 원인을 실어(hurtByVia) 그 파트 배율로. */
    boolean hurtPart(EnderDragonPart part, Damage source, float amount, Targetable attacker, Entity direct) {
        pendingPart = part;
        try {
            return hurtByVia(source, amount, attacker, direct);
        } finally {
            pendingPart = null;
        }
    }

    /** hurt(part, source, damage): 단계 보정 → 머리 아니면 /4 + min(d, 1) → 플레이어·특수 피해만 깎는다. 체력이 다하면 1 로 두고 DYING. */
    public boolean hurt(EnderDragonPart part, Damage source, float damage, Targetable attacker) {
        if (phaseManager.getCurrentPhase().id() == DragonPhase.DYING) return false;
        damage = phaseManager.getCurrentPhase().onHurt(source, directCause(), damage);
        if (part != head) damage = damage / 4.0F + Math.min(damage, 1.0F);
        if (damage < 0.01F) return false;
        if (attacker instanceof Player || alwaysHurts(source)) {
            float before = health();
            reallyHurt(source, damage, attacker);
            if (isDeadOrDying() && !phaseManager.getCurrentPhase().isSitting()) {
                setHealth(1.0F);
                phaseManager.setPhase(DragonPhase.DYING);
            }
            if (phaseManager.getCurrentPhase().isSitting()) {
                sittingDamageReceived = sittingDamageReceived + before - health();
                if (sittingDamageReceived > 0.25F * maxHealth()) {
                    sittingDamageReceived = 0.0F;
                    phaseManager.setPhase(DragonPhase.TAKEOFF);
                }
            }
        }
        return true;
    }

    /** #always_hurts_ender_dragons(폭발 계열). 태그를 못 읽는 환경에선 폭발 표시로. */
    private static boolean alwaysHurts(Damage source) {
        return source.explosion() || org.domi.furnace.server.entity.damage.DamageType.keyHasTag(source.type(), "always_hurts_ender_dragons");
    }

    protected boolean reallyHurt(Damage source, float amount, Targetable attacker) { return super.hurtBy(source, amount, attacker); }

    /** 엔드 수정이 부서졌다: 연결된 것이면 머리에 10 폭발 피해, 그리고 단계에 알린다. */
    public void onCrystalDestroyed(EndCrystal crystal, Pos pos, Damage source, Targetable destroyer) {
        Player player = destroyer instanceof Player p ? p : level.getNearestPlayer(CRYSTAL_DESTROY_TARGETING, null, pos.x(), pos.y(), pos.z());   // 바닐라: 출처 없음 → 거리 제한 없음
        if (crystal == nearestCrystal) hurt(head, new Damage("explosion", false, true, false), 10.0F, player);
        phaseManager.getCurrentPhase().onCrystalDestroyed(crystal, pos, source, player);
    }

    @Override
    public void kill() {
        remove(RemovalReason.KILLED);
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DIE);
        if (dragonFight != null) {
            dragonFight.updateDragon(this);
            dragonFight.setDragonKilled(this);
        }
    }

    /** 죽음 애니메이션: 200틱 동안 위로 0.1 씩, 150틱 넘어 5틱마다 8%, 마지막에 20% 경험치. 첫 처치면 12000, 아니면 500. */
    @Override
    protected void tickDeath() {
        if (dragonFight != null) dragonFight.updateDragon(this);
        dragonDeathTime++;
        if (dragonDeathTime >= 180 && dragonDeathTime <= 200) {
            random.nextFloat();
            random.nextFloat();
            random.nextFloat();
        }
        boolean loot = level.gameRule("doMobLoot");
        int xp = experienceTotal();
        if (dragonDeathTime > 150 && dragonDeathTime % 5 == 0 && loot) ExperienceOrb.award(level, x(), y(), z(), Mth.floor(xp * 0.08F));
        if (dragonDeathTime == 1 && !isSilent()) level.globalLevelEvent(1028, blockPosition(), 0);
        move(MOVER_SELF, 0.0, 0.1F, 0.0);
        if (dragonDeathTime == 200) {
            if (loot) ExperienceOrb.award(level, x(), y(), z(), Mth.floor(xp * 0.2F));
            if (dragonFight != null) dragonFight.setDragonKilled(this);
            remove(RemovalReason.KILLED);
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DIE);
        }
    }

    /** 죽음 애니메이션이 내는 경험치 총량(처음 잡는 드래곤 12000, 그 뒤 500). */
    public int experienceTotal() { return dragonFight != null && !dragonFight.hasPreviouslyKilledDragon() ? 12000 : 500; }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DragonPhase", phaseManager.getCurrentPhase().id());
        tag.putInt("DragonDeathTime", dragonDeathTime);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DragonPhase")) {
            // EnderDragonPhase.getById: 범위 밖은 HOLDING_PATTERN
            int id = tag.getIntOr0("DragonPhase");
            phaseManager.setPhase(id >= 0 && id < DragonPhase.COUNT ? id : DragonPhase.HOLDING_PATTERN);
        }
        if (tag.contains("DragonDeathTime")) dragonDeathTime = tag.getIntOr0("DragonDeathTime");
    }

    // ------------------------------------------------------------------ 단계 관리

    /** EnderDragonPhaseManager: 단계마다 인스턴스 하나를 두고 바꿀 때 end → DATA_PHASE → begin. */
    public final class PhaseManager {
        private final DragonPhase[] phases = new DragonPhase[DragonPhase.COUNT];
        private DragonPhase current;

        public void setPhase(int id) {
            if (current != null && id == current.id()) return;
            if (current != null) current.end();
            current = getPhase(id);
            entityData.set(DATA_PHASE, id);
            current.begin();
        }

        public DragonPhase getCurrentPhase() { return current; }

        public DragonPhase getPhase(int id) {
            if (phases[id] == null) phases[id] = DragonPhase.create(EnderDragon.this, id);
            return phases[id];
        }
    }
}
