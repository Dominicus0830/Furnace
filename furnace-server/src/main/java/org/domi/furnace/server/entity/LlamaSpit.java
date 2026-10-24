// 바닐라 LlamaSpit: 라마가 뱉는 침. 중력 0.06·공기 0.99, 맞으면 소유자 피해 1(mob_projectile)을 주고 사라진다. 공기가 없는 블록 속이거나 물에 닿아도 사라진다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class LlamaSpit extends Projectile {
    public LlamaSpit(ChunkLevel level, int id) { super(EntityType.LLAMA_SPIT, level, id); }

    /** LlamaSpit(level, owner): 라마의 몸 방향 앞쪽 눈높이 - 0.1 에서 시작. */
    public void startAt(Llama owner) {
        setOwner(owner);
        double r = owner.yBodyRot() * (float) (Math.PI / 180.0);
        setPos(owner.x() - (owner.bbWidth() + 1.0F) * 0.5 * Mth.sin((float) r), owner.eyeY() - 0.1F, owner.z() + (owner.bbWidth() + 1.0F) * 0.5 * Mth.cos((float) r));
    }

    @Override protected double defaultGravity() { return 0.06; }

    @Override
    public void tick() {
        super.tick();
        double dx = vx(), dy = vy(), dz = vz();
        Hit hit = findHit(x(), y(), z(), x() + dx, y() + dy, z() + dz);
        if (hit != null) onHit(hit);
        double nx = x() + dx, ny = y() + dy, nz = z() + dz;
        double h = Math.sqrt(dx * dx + dz * dz);
        setYRot(lerpRotation(yRotO, (float) (Mth.atan2(dx, dz) * 180.0F / (float) Math.PI)));
        setXRot(lerpRotation(xRotO, (float) (Mth.atan2(dy, h) * 180.0F / (float) Math.PI)));
        if (noAirInBox()) {
            discard();
        } else if (isInWater()) {
            discard();
        } else {
            setDeltaMovement(dx * 0.99F, dy * 0.99F, dz * 0.99F);
            applyGravity();
            setPos(nx, ny, nz);
        }
    }

    /** level.getBlockStates(box).noneMatch(isAir) */
    private boolean noAirInBox() {
        var box = boundingBox();
        int x0 = (int) Math.floor(box.minX()), y0 = (int) Math.floor(box.minY()), z0 = (int) Math.floor(box.minZ());
        int x1 = (int) Math.floor(box.maxX()), y1 = (int) Math.floor(box.maxY()), z1 = (int) Math.floor(box.maxZ());
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
            if (B.isAir(level.getBlockState(new Pos(bx, by, bz)))) return false;
        }
        return true;
    }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable owner = getOwner();
        if (owner != null) {
            Damage dmg = new Damage("mob_projectile", false, false, false);
            if (hit.target() instanceof LivingEntity le) le.hurtByVia(dmg, 1.0F, owner, this);
            else hit.target().hurtByMob(dmg, 1.0F, owner instanceof Mob m ? m : null);
        }
    }

    @Override
    protected void onHitBlock(Hit hit) { discard(); }
}
