// 바닐라 MinecartTNT: 켜진 활성화 레일·불·폭발로 도화선(80 틱), 빠르게 부딪히거나 3 넘게 떨어지면 바로 터진다.
// 세기 4 + 난수 × 1.5 × min(√속도², 5). 도화선이 탄 동안 자기 폭발은 레일(과 위가 레일인 칸)을 부수지 않는다.
// TODO(불화살): 불붙은 화살에 맞으면 화살 속도로 터지기 — 화살 피해가 직접 원인 엔티티를 넘기지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class MinecartTNT extends AbstractMinecart {

    private int fuse = -1;

    public MinecartTNT(ChunkLevel level, int id) { super(EntityType.TNT_MINECART, level, id); }

    @Override public Type getMinecartType() { return Type.TNT; }

    @Override protected String dropItem() { return "minecraft:tnt_minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.defaultState("tnt"); }

    public int getFuse() { return fuse; }

    public boolean isPrimed() { return fuse > -1; }

    @Override
    public void tick() {
        super.tick();
        if (fuse > 0) {
            fuse--;
            // addParticle(SMOKE)
        } else if (fuse == 0) {
            explode(vx() * vx() + vz() * vz());
        }
        if (horizontalCollision) {
            double d = vx() * vx() + vz() * vz();
            if (d >= 0.01F) explode(d);
        }
    }

    /** destroy(source): 불·폭발이 아니고 느리면 수레 아이템, 아니면 (아직 안 탔으면) 도화선 + 난수 0~38 틱. */
    @Override
    protected void destroy(Damage source) {
        double d = vx() * vx() + vz() * vz();
        if (!ignitesTnt(source) && !(d >= 0.01F)) {
            destroyAsItem(dropItem());
        } else if (fuse < 0) {
            primeFuse();
            fuse = random.nextInt(20) + random.nextInt(20);
        }
    }

    @Override
    protected boolean shouldSourceDestroy(Damage source) { return ignitesTnt(source); }

    /** damageSourceIgnitesTnt: is_fire 또는 is_explosion. */
    private static boolean ignitesTnt(Damage source) { return source.fire() || source.explosion(); }

    /** explode(power): 세기 4 + 난수 × 1.5 × min(√power, 5) 의 TNT 폭발 뒤 지운다. */
    protected void explode(double power) {
        double d = Math.sqrt(power);
        if (d > 5.0) d = 5.0;
        level.explode(this, x(), y(), z(), (float) (4.0 + random.nextDouble() * 1.5 * d), false, level.destroyType("TNT"));
        discard();
    }

    /** causeFallDamage: 3 이상 떨어지면 (거리/10)² 세기로 터진다. */
    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        if (distance >= 3.0F) {
            float f = distance / 10.0F;
            explode(f * f);
        }
        return super.causeFallDamage(distance, multiplier);
    }

    @Override
    public void activateMinecart(int x, int y, int z, boolean powered) {
        if (powered && fuse < 0) primeFuse();
    }

    /** primeFuse: 80 틱, 엔티티 이벤트 10, 소리. */
    public void primeFuse() {
        fuse = 80;
        broadcastEntityEvent((byte) 10);
        if (!isSilent()) level.playSound(null, x(), y(), z(), "minecraft:entity.tnt.primed", "blocks", 1.0F, 1.0F);
    }

    @Override
    public float blockExplosionResistance(ChunkLevel level, Pos pos, int state, float resistance) {
        return !isPrimed() || !B.isTag(state, "minecraft:rails") && !B.isTag(level.getBlockState(pos.above()), "minecraft:rails") ? resistance : 0.0F;
    }

    @Override
    public boolean shouldBlockExplode(ChunkLevel level, Pos pos, int state, float power) {
        return !isPrimed() || !B.isTag(state, "minecraft:rails") && !B.isTag(level.getBlockState(pos.above()), "minecraft:rails");
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TNTFuse", fuse);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.containsNumber("TNTFuse")) fuse = tag.getIntOr0("TNTFuse");
    }
}
