// 바닐라 LightningBolt: 번개. 처음 틱에 불을 붙이고(보통·어려움) 피뢰침을 켜고 구리를 벗기며, 살아 있는 동안(2틱+번쩍임 1~3번) 주변(3칸, 위 6칸) 엔티티를 thunderHit 한다.
// 소리는 클라이언트가 낸다(서버는 안 낸다). 번쩍임 사이 간격은 레벨 난수(0~9)로 정한다.
// 발전 과제: 원인 플레이어(channeling)에게 channeled_lightning, 끝날 때 근처 플레이어에게 lightning_strike.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.BaseFireBlock;
import org.domi.furnace.server.block.types.LightningRodBlock;
import org.domi.furnace.server.block.types.WeatheringCopper;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class LightningBolt extends Entity {
    private int life = 2;
    public long seed;
    private int flashes;
    private boolean visualOnly;
    private final Set<Object> hitEntities = new HashSet<>();
    private int blocksSetOnFire;

    public LightningBolt(ChunkLevel level, int id) {
        super(EntityType.LIGHTNING_BOLT, level, id);
        seed = random.nextLong();
        flashes = random.nextInt(3) + 1;
    }

    private Player cause;

    /** 삼지창 channeling 으로 불러낸 플레이어(LightningBolt.setCause). */
    public void setCause(Player p) { cause = p; }

    public void setVisualOnly(boolean v) { visualOnly = v; }

    public int blocksSetOnFire() { return blocksSetOnFire; }

    /** 바닐라 getHitEntities — 맞은 엔티티 중 아직 살아 있는 것(전리품 조건 lightning.entity_struck 용). */
    public List<Rider> hitEntities() {
        List<Rider> out = new ArrayList<>();
        for (Object o : hitEntities) {
            if (o instanceof Player p) { if (!p.vitals().isDeadOrDying()) out.add(p); }
            else if (o instanceof Entity e && e.isAlive()) out.add(e);
        }
        return out;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
    }

    private Pos strikePosition() { return new Pos((int) Math.floor(x()), (int) Math.floor(y() - 1.0E-6), (int) Math.floor(z())); }

    @Override
    public void tick() {
        super.tick();
        if (life == 2) {
            int diff = level.difficulty();
            if (diff == 2 || diff == 3) spawnFire(4);
            powerLightningRod();
            clearCopperOnLightningStrike(level, strikePosition());
        }
        life--;
        if (life < 0) {
            if (flashes == 0) {
                lightningStrikeTriggers();   // 바닐라(btm): visualOnly 를 보지 않고 flashes == 0 이면 늘 보낸다
                discard();
            } else if (life < -level.random().nextInt(10)) {
                flashes--;
                life = 1;
                seed = level.random().nextLong();
                spawnFire(0);
            }
        }
        if (life >= 0 && !visualOnly) {
            AABB box = new AABB(x() - 3.0, y() - 3.0, z() - 3.0, x() + 3.0, y() + 6.0 + 3.0, z() + 3.0);
            List<Entity> list = level.getEntities(this, box, Entity::isAlive);
            for (Entity e : list) e.thunderHit(level, this);
            hitEntities.addAll(list);
            List<Player> players = new ArrayList<>();
            level.forEachPlayerIn(box, players::add);
            List<Rider> victims = new ArrayList<>(list);
            for (Player p : players) {
                if (p.vitals().isSpectator()) continue;
                p.vitals().hurt(DamageType.byKey("lightning_bolt"), 5.0F, null);
                victims.add(p);
                hitEntities.add(p);
            }
            if (cause != null) {   // 바닐라: 원인이 있으면 맞은 것들(빈 목록 포함)로 channeled_lightning
                var snaps = AdvTriggers.lootOfAll(victims);
                cause.triggerAdvancement(a -> a.triggerVictims("minecraft:channeled_lightning", snaps));
            }
        }
    }

    /**
     * 끝날 때(flashes == 0): 15칸 안의 아직 안 맞은 살아 있는 것들을 구경꾼으로, 번개에서 256칸 안 모든 플레이어에게 lightning_strike.
     */
    private void lightningStrikeTriggers() {
        AABB big = new AABB(x() - 15.0, y() - 15.0, z() - 15.0, x() + 15.0, y() + 6.0 + 15.0, z() + 15.0);
        List<Rider> bystanders = new ArrayList<>(level.getEntities(this, big, e -> e.isAlive() && !hitEntities.contains(e)));
        level.forEachPlayerIn(big, p -> { if (!p.vitals().isDeadOrDying() && !hitEntities.contains(p)) bystanders.add(p); });
        var bolt = KillCredit.lootOf(this);
        var by = AdvTriggers.lootOfAll(bystanders);
        level.forEachPlayer(p -> {
            double dx = p.x() - x(), dy = p.y() - y(), dz = p.z() - z();
            if ((float) Math.sqrt(dx * dx + dy * dy + dz * dz) < 256.0F) p.triggerAdvancement(a -> a.triggerLightning(bolt, by));
        });
    }

    private void spawnFire(int extra) {
        if (visualOnly || !level.gameRule("doFireTick")) return;
        Pos pos = blockPosition();
        int fire = BaseFireBlock.getState(level, pos);
        if (B.isAir(level.getBlockState(pos)) && B.canSurvive(fire, level, pos)) {
            level.setBlockAndUpdate(pos, fire);
            blocksSetOnFire++;
        }
        for (int i = 0; i < extra; i++) {
            Pos p = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
            int f = BaseFireBlock.getState(level, p);
            if (B.isAir(level.getBlockState(p)) && B.canSurvive(f, level, p)) {
                level.setBlockAndUpdate(p, f);
                blocksSetOnFire++;
            }
        }
    }

    private void powerLightningRod() {
        Pos p = strikePosition();
        int s = level.getBlockState(p);
        if (B.behavior(s) instanceof LightningRodBlock rod) rod.onLightningStrike(s, level, p);
    }

    private static void clearCopperOnLightningStrike(ChunkLevel level, Pos pos) {
        int s = level.getBlockState(pos);
        Pos target;
        int state;
        if (B.behavior(s) instanceof LightningRodBlock rod) {
            target = pos.relative(rod.attachedDirection(s));
            state = level.getBlockState(target);
        } else {
            target = pos;
            state = s;
        }
        if (WeatheringCopper.age(B.block(state)) >= 0) {
            level.setBlockAndUpdate(target, WeatheringCopper.first(level.getBlockState(target)));
            int n = level.random().nextInt(3) + 3;
            for (int i = 0; i < n; i++) {
                int steps = level.random().nextInt(8) + 1;
                Pos cur = target;
                for (int k = 0; k < steps; k++) {
                    Pos next = randomWalkCleaningCopper(level, cur);
                    if (next == null) break;
                    cur = next;
                }
            }
        }
    }

    private static Pos randomWalkCleaningCopper(ChunkLevel level, Pos origin) {
        for (int i = 0; i < 10; i++) {
            int dx = level.random().nextInt(3) - 1, dy = level.random().nextInt(3) - 1, dz = level.random().nextInt(3) - 1;
            Pos p = origin.offset(dx, dy, dz);
            int s = level.getBlockState(p);
            if (WeatheringCopper.age(B.block(s)) >= 0) {
                int prev = WeatheringCopper.previous(s);
                if (prev >= 0) level.setBlockAndUpdate(p, prev);
                level.levelEvent(null, 3002, p, -1);
                return p;
            }
        }
        return null;
    }

    @Override public boolean shouldSave() { return false; }

    @Override protected void addAdditionalSaveData(NbtCompound tag) {}

    @Override protected void readAdditionalSaveData(NbtCompound tag) {}
}
