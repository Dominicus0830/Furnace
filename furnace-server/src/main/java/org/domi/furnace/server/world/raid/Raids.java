// 바닐라 Raids(습격 목록) + 나쁜 징조(BadOmenMobEffect)·습격 징조(RaidOmenMobEffect)의 서버 쪽.
// 차이: 바닐라는 차원 하나에 Raids 하나(raids.dat 저장). 여기서는 region 레벨마다 두고, region 이 합쳐지거나 나뉘면
//   습격 중심 청크를 따라 옮긴다. 번호·저장(raids.dat)은 월드 하나의 RaidStore 가 맡는다(claim·park·publishSnapshots).
package org.domi.furnace.server.world.raid;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiRecord;
import org.domi.furnace.server.world.poi.PoiTypes;

public final class Raids {
    private final ChunkLevel level;
    private final Map<Integer, Raid> raids = new LinkedHashMap<>();
    private RaidStore store;
    private int tick;

    public Raids(ChunkLevel level) { this.level = level; }

    private RaidStore store() {
        if (store == null) {
            store = level.raidStore();
            tick = store.tick();
        }
        return store;
    }

    public Map<Integer, Raid> all() { return raids; }

    /** Raids.get(id). */
    public Raid get(int id) { return raids.get(id); }

    /** Raids.canJoinRaid(raider, raid): 살아 있고 참여 가능하며 무행동 2400 이하, 같은 차원. */
    public static boolean canJoinRaid(org.domi.furnace.server.entity.Raider r, Raid raid) {
        return r != null && raid != null && raid.level() != null
                && r.isAlive() && r.canJoinRaid() && r.getNoActionTime() <= 2400 && r.level().dimension().equals(raid.level().dimension());
    }

    // ------------------------------------------------------------------ 저장(RaidStore)

    /** 그 청크가 중심인 보류 습격을 이 레벨로 올린다(청크가 이 region 에 올라올 때). */
    public void claim(int cx, int cz) {
        for (var tag : store().claim(cx, cz)) {
            Raid r = new Raid(level, tag);
            raids.put(r.id(), r);
        }
    }

    /** 중심 청크가 내려가면 습격을 보류로 돌리고, 소속 습격자는 번호만 기억하게 한다(다시 올라오면 다시 들어간다). */
    public void park(int cx, int cz) {
        Iterator<Raid> it = raids.values().iterator();
        while (it.hasNext()) {
            Raid r = it.next();
            if (r.center().x() >> 4 == cx && r.center().z() >> 4 == cz) {
                store().park(r);
                r.detachRaiders();
                it.remove();
            }
        }
    }

    /** 이 레벨의 습격 스냅샷을 저장소에 올린다(이 레벨 스레드에서). */
    public void publishSnapshots() {
        RaidStore s = store();
        s.reportTick(tick);
        for (Raid r : raids.values()) s.publish(r);
    }

    /** Raids.tick: disableRaids 면 멈추고, 멈춘 습격은 지운다. */
    public void tick() {
        store();
        tick++;
        Iterator<Raid> it = raids.values().iterator();
        while (it.hasNext()) {
            Raid r = it.next();
            if (level.gameRule("disableRaids")) r.stop();
            if (r.isStopped()) {
                it.remove();
                store().forget(r.id());
            } else {
                r.tick();
            }
        }
    }

    /** getNearbyRaid(pos, 거리²): 활성 습격 중 중심이 가장 가까운 것(같으면 먼저 것). */
    public Raid getNearbyRaid(Pos pos, int distSqr) {
        Raid best = null;
        double d = distSqr;
        for (Raid r : raids.values()) {
            Pos c = r.center();
            double dx = c.x() - pos.x(), dy = c.y() - pos.y(), dz = c.z() - pos.z();
            double e = dx * dx + dy * dy + dz * dz;
            if (r.isActive() && e < d) {
                best = r;
                d = e;
            }
        }
        return best;
    }

    /** ServerLevel.getRaidAt. */
    public Raid getRaidAt(Pos pos) { return getNearbyRaid(pos, Raid.VALID_RAID_RADIUS_SQR); }

    /**
     * createOrExtendRaid(player, pos): 관전자·disableRaids·습격 없는 차원이면 null. 반경 64 의 점유된 마을 POI 평균 위치(없으면 pos)에
     * 습격을 찾거나 만들고, 아직 시작 전이거나 단계가 최대 미만이면 습격 징조를 흡수한다.
     */
    public Raid createOrExtendRaid(Player player, Pos pos) {
        if (player.isSpectator()) return null;
        if (level.gameRule("disableRaids")) return null;
        if (!dimensionHasRaids()) return null;
        var list = level.poiManager().getInRange(PoiTypes.VILLAGE, pos, 64, PoiManager.Occupancy.IS_OCCUPIED);
        int i = 0;
        double x = 0, y = 0, z = 0;
        for (PoiRecord r : list) {
            x += r.pos().x();
            y += r.pos().y();
            z += r.pos().z();
            i++;
        }
        Pos center = i > 0 ? new Pos((int) Math.floor(x * (1.0 / i)), (int) Math.floor(y * (1.0 / i)), (int) Math.floor(z * (1.0 / i))) : pos;
        Raid raid = getOrCreateRaid(center);
        if (!raid.isStarted() && !raids.containsKey(raid.id())) raids.put(raid.id(), raid);
        if (!raid.isStarted() || raid.raidOmenLevel() < raid.maxRaidOmenLevel()) raid.absorbRaidOmen(player);
        return raid;
    }

    private Raid getOrCreateRaid(Pos pos) {
        Raid r = getRaidAt(pos);
        return r != null ? r : new Raid(store().nextId(), level, pos);
    }

    /** DimensionType.has_raids(바닐라 타입: 오버월드·엔드 참, 네더 거짓). */
    private boolean dimensionHasRaids() { return level.dimensionType().hasRaids(); }

    // ------------------------------------------------------------------ 징조 효과(플레이어 효과 틱 직전에 부른다)

    /**
     * BadOmenMobEffect.applyEffectTick(매 틱): 관전자가 아니고 평화로움이 아니며 마을 안이면, 그 자리 습격이 없거나 단계가 최대 미만일 때
     * 습격 징조(600틱, 같은 증폭)로 바꾸고 위치를 기억한다(나쁜 징조는 지운다).
     * RaidOmenMobEffect.applyEffectTick(남은 시간 1): 기억한 위치에서 습격을 만들거나 키운다.
     */
    public void tickOmens(Player p) {
        if (p.vitals().isDeadOrDying()) return;
        MobEffectInstance bad = p.vitals().getEffect(MobEffect.BAD_OMEN);
        if (bad != null && !p.isSpectator() && level.difficulty() != 0) {
            Pos pos = p.blockPosition();
            if (level.isVillage(pos)) {
                Raid raid = getRaidAt(pos);
                if (raid == null || raid.raidOmenLevel() < raid.maxRaidOmenLevel()) {
                    p.vitals().addEffect(new MobEffectInstance(MobEffect.RAID_OMEN, 600, bad.amplifier()));
                    p.setRaidOmenPosition(pos);
                    p.vitals().removeEffect(MobEffect.BAD_OMEN);
                }
            }
        }
        MobEffectInstance omen = p.vitals().getEffect(MobEffect.RAID_OMEN);
        if (omen != null && omen.duration() == 1 && p.raidOmenPosition() != null) {
            createOrExtendRaid(p, p.raidOmenPosition());
            p.setRaidOmenPosition(null);
        }
    }

    // ------------------------------------------------------------------ region 합치기·나누기

    public void absorb(Raids other, ChunkLevel into) {
        tick = Math.max(tick, other.tick);
        for (Raid r : other.raids.values()) {
            r.level(into);
            raids.put(r.id(), r);
        }
        other.raids.clear();
    }

    public void splitTo(Raids target, ChunkLevel targetLevel, ChunkLevel.ChunkFilter inTarget) {
        target.tick = Math.max(target.tick, tick);
        Iterator<Raid> it = raids.values().iterator();
        while (it.hasNext()) {
            Raid r = it.next();
            if (inTarget.test(r.center().x() >> 4, r.center().z() >> 4)) {
                r.level(targetLevel);
                target.raids.put(r.id(), r);
                it.remove();
            }
        }
    }
}
