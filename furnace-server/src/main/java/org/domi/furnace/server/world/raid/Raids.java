// 바닐라 Raids(습격 목록) + 나쁜 징조(BadOmenMobEffect)·습격 징조(RaidOmenMobEffect)의 서버 쪽.
// 차이: 바닐라는 차원 하나에 Raids 하나(raids.dat 저장). 여기서는 region 레벨마다 두고, region 이 합쳐지거나 나뉘면
//   습격 중심 청크를 따라 옮긴다. id 는 서버 전체 카운터. 저장(raids.dat)은 아직 없다 — 서버를 끄면 진행 중 습격은 사라진다.
package org.domi.furnace.server.world.raid;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiRecord;
import org.domi.furnace.server.world.poi.PoiTypes;

public final class Raids {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final ChunkLevel level;
    private final Map<Integer, Raid> raids = new LinkedHashMap<>();
    private int tick;

    public Raids(ChunkLevel level) { this.level = level; }

    public Map<Integer, Raid> all() { return raids; }

    /** Raids.tick: disableRaids 면 멈추고, 멈춘 습격은 지운다. */
    public void tick() {
        tick++;
        Iterator<Raid> it = raids.values().iterator();
        while (it.hasNext()) {
            Raid r = it.next();
            if (level.gameRule("disableRaids")) r.stop();
            if (r.isStopped()) it.remove();
            else r.tick();
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
        return r != null ? r : new Raid(NEXT_ID.getAndIncrement(), level, pos);
    }

    /** DimensionType.has_raids: 오버월드·엔드 참, 네더 거짓. */
    private boolean dimensionHasRaids() { return !"minecraft:the_nether".equals(level.dimension()); }

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
        for (Raid r : other.raids.values()) {
            r.level(into);
            raids.put(r.id(), r);
        }
        other.raids.clear();
    }

    public void splitTo(Raids target, ChunkLevel targetLevel, ChunkLevel.ChunkFilter inTarget) {
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
