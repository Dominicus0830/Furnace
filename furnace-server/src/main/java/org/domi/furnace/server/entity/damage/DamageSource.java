// 바닐라 DamageSource — 피해 유형 + 원인 엔티티(causing)·직접 엔티티(direct)·명시 위치.
// 바닐라는 Entity 하나로 받지만 Furnace 플레이어는 Entity 가 아니라서 Rider(Entity 또는 Player)로 받는다.
package org.domi.furnace.server.entity.damage;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.entity.Targetable;

/**
 * @param causing  getEntity(): 피해의 책임자(화살이면 쏜 쪽). 없으면 null
 * @param direct   getDirectEntity(): 실제로 맞힌 것(화살 자체). 없으면 null
 * @param position 명시 위치(바닐라 DamageSource(type, Vec3)). 없으면 null
 */
public record DamageSource(DamageType type, Rider causing, Rider direct, double[] position) {

    public static DamageSource of(DamageType type) { return new DamageSource(type, null, null, null); }

    public static DamageSource at(DamageType type, double[] pos) { return new DamageSource(type, null, null, pos); }

    /** DamageSource(type, entity): 직접·원인이 같은 엔티티. */
    public static DamageSource by(DamageType type, Rider entity) { return new DamageSource(type, entity, entity, null); }

    /** DamageSource(type, direct, causing). */
    public static DamageSource by(DamageType type, Rider direct, Rider causing) { return new DamageSource(type, causing, direct, null); }

    /** 때린 쪽(플레이어·몹)으로 쓸 수 있는 원인 엔티티. 살아 있는 것이 아니면 null. */
    public Targetable attacker() { return causing instanceof Targetable t ? t : null; }

    /** 직접 엔티티가 일반 엔티티(투사체 등)면 그것. */
    public Entity directEntity() { return direct instanceof Entity e ? e : null; }

    /** isDirect: 직접 엔티티와 원인 엔티티가 같다. */
    public boolean isDirect() { return causing == direct; }

    /** scaling when_caused_by_living_non_player 의 조건: 원인 엔티티가 플레이어가 아닌 LivingEntity. */
    public boolean causedByLivingNonPlayer() { return causing instanceof LivingEntity; }

    /** getSourcePosition: 명시 위치, 없으면 직접 엔티티 위치, 둘 다 없으면 null. */
    public double[] sourcePosition() {
        if (position != null) return position;
        if (direct != null) return new double[]{direct.x(), direct.y(), direct.z()};
        return null;
    }

    public static int idOf(Rider r) { return r == null ? -1 : r.id(); }

    public static Text nameOf(Rider r) { return r == null ? null : r.displayName(); }
}
