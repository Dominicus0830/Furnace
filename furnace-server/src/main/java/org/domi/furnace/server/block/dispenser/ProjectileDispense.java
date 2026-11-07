// 바닐라 ProjectileDispenseBehavior + ProjectileItem.DispenseConfig: 위치 함수(기본 가운데 + 0.7 방향 + 위 0.1)에서 투사체를 만들어
// shoot(방향, 힘 1.1, 흩어짐 6.0)으로 쏜다. 소리는 설정의 이벤트(기본 1002).
package org.domi.furnace.server.block.dispenser;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.entity.AbstractArrow;
import org.domi.furnace.server.entity.Arrow;
import org.domi.furnace.server.entity.FireworkRocketEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Projectile;
import org.domi.furnace.server.entity.SmallFireball;
import org.domi.furnace.server.entity.Snowball;
import org.domi.furnace.server.entity.SpectralArrow;
import org.domi.furnace.server.entity.ThrowableProjectile;
import org.domi.furnace.server.entity.ThrownEgg;
import org.domi.furnace.server.entity.ThrownExperienceBottle;
import org.domi.furnace.server.entity.ThrownPotion;
import org.domi.furnace.server.entity.WindCharge;

final class ProjectileDispense extends DefaultDispenseItemBehavior {

    /** ProjectileItem.asProjectile(level, pos, stack, direction). */
    interface Factory { Projectile create(ChunkLevel level, double x, double y, double z, ItemStack stack, int dir); }

    /** DispenseConfig.positionFunction. */
    interface Position { double[] at(BlockSource s); }

    private static final Position DEFAULT_POS = s -> s.dispensePosition(0.7, 0.0, 0.1, 0.0);

    private final Factory factory;
    private final Position position;
    private final float uncertainty, power;
    private final int event;
    private final boolean shoots;

    ProjectileDispense(Factory f) { this(f, 6.0F, 1.1F); }

    ProjectileDispense(Factory f, float uncertainty, float power) { this(f, DEFAULT_POS, uncertainty, power, 1002, true); }

    private ProjectileDispense(Factory f, Position position, float uncertainty, float power, int event, boolean shoots) {
        this.factory = f;
        this.position = position;
        this.uncertainty = uncertainty;
        this.power = power;
        this.event = event;
        this.shoots = shoots;
    }

    @Override
    protected ItemStack execute(BlockSource s, ItemStack stack) {
        if (!(s.level() instanceof ChunkLevel cl)) return stack;
        int dir = s.facing();
        double[] p = position.at(s);
        Projectile proj = factory.create(cl, p[0], p[1], p[2], stack, dir);
        if (shoots) proj.shoot(Dir.DX[dir], Dir.DY[dir], Dir.DZ[dir], power, uncertainty);   // ProjectileItem.shoot(…, power, uncertainty)
        cl.addFreshEntity(proj);
        return stack.withCount(stack.count() - 1);
    }

    @Override
    protected void playSound(BlockSource s) { s.level().levelEvent(null, event, s.pos(), 0); }

    /**
     * OminousItemSpawner.spawnItem 의 ProjectileItem 경로: asProjectile(level, 위치, 스택, 아래) → shoot(아래, 힘, 흩어짐) → 설정이 정한 이벤트(기본 1002 는 정하지 않은 것).
     * 아직 월드에 넣지 않은 투사체를 돌려준다. (바닐라는 소유자를 생성기 엔티티로 두지만 생물이 아니라 여기서는 두지 않는다 — 생성기는 같은 틱에 사라진다.)
     */
    Projectile launchDown(ChunkLevel level, double x, double y, double z, org.domi.furnace.server.block.Pos eventPos, ItemStack stack) {
        Projectile proj = factory.create(level, x, y, z, stack, Dir.DOWN);
        if (shoots) proj.shoot(Dir.DX[Dir.DOWN], Dir.DY[Dir.DOWN], Dir.DZ[Dir.DOWN], power, uncertainty);
        if (event != 1002) level.levelEvent(null, event, eventPos, 0);
        return proj;
    }

    // ------------------------------------------------------------------ 아이템별 asProjectile

    /** ArrowItem/TippedArrowItem: 줍기 허용 화살(줍는 아이템은 1개, 물부리 화살이면 효과). */
    static Projectile arrow(ChunkLevel level, double x, double y, double z, ItemStack stack, int dir) {
        Arrow a = new Arrow(level, level.nextEntityId(), x, y, z);
        if (org.domi.furnace.server.item.ItemTools.itemName(stack).equals("minecraft:tipped_arrow")) a.setPotionFromItem(stack);
        a.initFromItem(null, stack.withCount(1), null);
        a.pickup = AbstractArrow.Pickup.ALLOWED;
        return a;
    }

    static Projectile spectralArrow(ChunkLevel level, double x, double y, double z, ItemStack stack, int dir) {
        SpectralArrow a = new SpectralArrow(level, level.nextEntityId());
        a.setPos(x, y, z);
        a.initFromItem(null, stack.withCount(1), null);
        a.pickup = AbstractArrow.Pickup.ALLOWED;
        return a;
    }

    private static Projectile throwable(ThrowableProjectile t, double x, double y, double z, ItemStack stack) {
        t.setPos(x, y, z);
        t.setItem(stack);
        return t;
    }

    static Projectile egg(ChunkLevel l, double x, double y, double z, ItemStack s, int d) { return throwable(new ThrownEgg(l, l.nextEntityId()), x, y, z, s); }

    static Projectile snowball(ChunkLevel l, double x, double y, double z, ItemStack s, int d) { return throwable(new Snowball(l, l.nextEntityId()), x, y, z, s); }

    static Projectile experienceBottle(ChunkLevel l, double x, double y, double z, ItemStack s, int d) {
        return throwable(new ThrownExperienceBottle(l, l.nextEntityId()), x, y, z, s);
    }

    static Projectile potion(ChunkLevel l, double x, double y, double z, ItemStack s, int d) { return throwable(new ThrownPotion(l, l.nextEntityId()), x, y, z, s); }

    /** FireworkRocketItem: 비스듬히 쏜 로켓. 위치는 가운데 + 방향 × (0.50001 − 너비/2), y 는 높이/2 를 더 뺀다. 힘 0.5·흩어짐 1.0·이벤트 1004. */
    static final ProjectileDispense FIREWORK = new ProjectileDispense((l, x, y, z, s, d) -> {
        FireworkRocketEntity r = new FireworkRocketEntity(l, l.nextEntityId(), x, y, z, s);
        r.setShotAtAngle(true);
        return r;
    }, src -> {
        int d = src.facing();
        double h = 0.5000099999997474 - 0.25 / 2.0;
        return new double[] {src.centerX() + Dir.DX[d] * h, src.centerY() + Dir.DY[d] * h - 0.25 / 2.0, src.centerZ() + Dir.DZ[d] * h};
    }, 1.0F, 0.5F, 1004, true);

    private static final Position ONE_BLOCK = s -> s.dispensePosition(1.0, 0.0, 0.0, 0.0);

    /** FireChargeItem: 레벨 난수 triangle 3번으로 흩은 방향(정규화)의 작은 화염구. shoot 하지 않는다. 이벤트 1018. */
    static final ProjectileDispense FIRE_CHARGE = new ProjectileDispense((l, x, y, z, s, d) -> {
        var r = l.random();
        double mx = r.triangle(Dir.DX[d], 0.11485000000000001), my = r.triangle(Dir.DY[d], 0.11485000000000001), mz = r.triangle(Dir.DZ[d], 0.11485000000000001);
        double len = Math.sqrt(mx * mx + my * my + mz * mz);   // Vec3.normalize
        if (len < 1.0E-5F) {
            mx = my = mz = 0.0;
        } else {
            mx /= len;
            my /= len;
            mz /= len;
        }
        SmallFireball f = new SmallFireball(l, l.nextEntityId());
        f.launch(x, y, z, mx, my, mz);
        f.setItem(s);
        return f;
    }, ONE_BLOCK, 6.6666665F, 1.0F, 1018, false);

    /** WindChargeItem: 같은 흩음(정규화 없이)을 속도로 바로 준다. 이벤트 1051. */
    static final ProjectileDispense WIND_CHARGE = new ProjectileDispense((l, x, y, z, s, d) -> {
        var r = l.random();
        double mx = r.triangle(Dir.DX[d], 0.11485000000000001), my = r.triangle(Dir.DY[d], 0.11485000000000001), mz = r.triangle(Dir.DZ[d], 0.11485000000000001);
        WindCharge w = new WindCharge(l, l.nextEntityId());
        w.launch(x, y, z, mx, my, mz);
        w.setDeltaMovement(mx, my, mz);
        return w;
    }, ONE_BLOCK, 6.6666665F, 1.0F, 1051, false);
}
