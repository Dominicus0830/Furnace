// 바닐라 EnderDragonPart: 드래곤 몸의 판정 상자 하나(머리·목·몸통·꼬리 셋·날개 둘). 레벨·저장·추적·틱 목록에 들어가지 않는다.
// 클라이언트는 본체 스폰 패킷의 id 다음 번호(id+1..id+8)로 파트를 직접 만든다 — 그래서 본체는 nextEntityIds(9)로 연속 번호를 잡는다.
// 투사체·폭발 조회(getEntitiesAndParts 꼴)에서만 보이는 가벼운 Targetable 이다: 맞으면 본체가 파트 배율로 받는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.Pos;

public final class EnderDragonPart implements Targetable {
    public final EnderDragon parent;
    public final String name;
    public final int index;
    public final float width, height;
    double x, y, z, xo, yo, zo;

    // 지금 이 파트를 맞힌 투사체의 주인·투사체(Projectile.onHit 동안만)
    private Targetable hitOwner;
    private Entity hitDirect;

    EnderDragonPart(EnderDragon parent, String name, int index, float width, float height) {
        this.parent = parent;
        this.name = name;
        this.index = index;
        this.width = width;
        this.height = height;
    }

    @Override public int id() { return parent.id() + 1 + index; }

    @Override public double x() { return x; }

    @Override public double y() { return y; }

    @Override public double z() { return z; }

    /** 눈높이: EntityDimensions.scalable 기본 0.85 × 높이. */
    @Override public double eyeY() { return y + height * 0.85F; }

    @Override public float bbWidth() { return width; }

    @Override public float bbHeight() { return height; }

    /** getY(scale): 아래에서 높이의 scale 만큼. */
    public double y(double scale) { return y + height * scale; }

    void setPos(double nx, double ny, double nz) {
        x = nx;
        y = ny;
        z = nz;
    }

    @Override
    public AABB boundingBox() {
        double w = width / 2.0;
        return new AABB(x - w, y, z - w, x + w, y + height, z + w);
    }

    @Override public Pos blockPosition() { return new Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }

    @Override public boolean isAlive() { return parent.isAlive(); }

    /** 몹 AI 가 파트를 겨냥하지 않는다(파트는 몹 조회에 나오지 않는다). */
    @Override public boolean canBeSeenAsEnemy() { return false; }

    @Override public double visibilityPercent(Entity looker) { return 1.0; }

    @Override public boolean isPlayer() { return false; }

    /** 바닐라 EnderDragonPart.isPickable = true(본체는 false). */
    @Override public boolean isPickable() { return true; }

    @Override public void knockback(double strength, double dx, double dz) {}

    @Override public org.domi.furnace.api.text.Text displayName() { return parent.displayName(); }

    /** is(entity): 자신이거나 부모. */
    public boolean is(Object o) { return o == this || o == parent; }

    /** 투사체 맞힘 처리 동안 주인(플레이어 포함)·투사체를 기억해 hurtByMob 이 그대로 넘기게 한다. */
    void beginProjectileHit(Targetable owner, Entity direct) {
        hitOwner = owner;
        hitDirect = direct;
    }

    void endProjectileHit() {
        hitOwner = null;
        hitDirect = null;
    }

    @Override
    public boolean hurtByMob(Entity.Damage source, float amount, Mob attacker) {
        if (hitDirect != null) return hurtVia(source, amount, hitOwner, hitDirect);
        return hurtVia(source, amount, attacker, null);
    }

    /** hurt: 본체에 이 파트로 맞았다고 넘긴다. */
    public boolean hurt(Entity.Damage source, float amount, Targetable attacker) { return hurtVia(source, amount, attacker, null); }

    /** 직접 원인(화살 등)까지 넘긴다 — 앉은 단계의 화살·돌풍구 처리가 본다. */
    public boolean hurtVia(Entity.Damage source, float amount, Targetable attacker, Entity direct) {
        // 파트 자신의 isInvulnerableTo 근사: 파트는 무적 표시가 없고 불 피해는 드래곤 종류라 면역.
        // 바닐라는 파트 자신의 isRemoved(파트는 지워지지 않아 늘 false)를 보지만 여기선 본체가 지워졌으면 막는다(의도한 차이)
        if (parent.isRemoved() || source.fire() && parent.fireImmune()) return false;
        return parent.hurtPart(this, source, amount, attacker, direct);
    }
}
