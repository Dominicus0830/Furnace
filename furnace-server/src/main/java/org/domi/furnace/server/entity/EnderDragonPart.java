// 바닐라 EnderDragonPart: 드래곤 몸의 판정 상자 하나(머리·목·몸통·꼬리 셋·날개 둘). 레벨에 들어가지 않고 저장되지 않는다.
// 클라이언트는 본체 id 다음 번호(id+1..id+8)를 파트에 붙이므로 그 번호로 맞으면 본체가 파트 배율로 맞는다.
package org.domi.furnace.server.entity;

public final class EnderDragonPart {
    public final EnderDragon parent;
    public final String name;
    public final int index;
    public final float width, height;
    double x, y, z, xo, yo, zo;

    EnderDragonPart(EnderDragon parent, String name, int index, float width, float height) {
        this.parent = parent;
        this.name = name;
        this.index = index;
        this.width = width;
        this.height = height;
    }

    public int id() { return parent.id() + 1 + index; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    /** getY(scale): 아래에서 높이의 scale 만큼. */
    public double y(double scale) { return y + height * scale; }

    void setPos(double nx, double ny, double nz) {
        x = nx;
        y = ny;
        z = nz;
    }

    public AABB boundingBox() {
        double w = width / 2.0;
        return new AABB(x - w, y, z - w, x + w, y + height, z + w);
    }

    /** hurt: 본체에 이 파트로 맞았다고 넘긴다. */
    public boolean hurt(Entity.Damage source, float amount, Targetable attacker) {
        // 파트 자신의 isInvulnerableTo: 파트는 무적 표시가 없다 — 지워졌거나 불 피해(드래곤 종류는 불에 면역)만 막는다
        if (parent.isRemoved() || source.fire() && parent.fireImmune()) return false;
        return parent.hurt(this, source, amount, attacker);
    }
}
