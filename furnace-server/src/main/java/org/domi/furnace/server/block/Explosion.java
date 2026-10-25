// 바닐라 Explosion: 16×16×16 껍질의 광선으로 부서질 칸을 정하고(광선마다 세기 = 반경 × (0.7 + 레벨 난수 × 0.6), 0.3 칸씩 0.225 감쇠),
// 반경 2배 안 엔티티에 거리·시야 비율로 피해·밀림을 주고, 칸을 부수며(전리품 폭발 감쇠) 소리·입자·패킷을 보낸다.
// TODO: 불(fire) 폭발의 불 붙이기는 구현, 블록 엔티티 내용물 전리품 문맥은 문맥 파라미터에 폭발 반경만 넘긴다.
package org.domi.furnace.server.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.net.protocol.play.ExplodePacket;

public final class Explosion {
    public enum BlockInteraction { KEEP, DESTROY, DESTROY_WITH_DECAY, TRIGGER_BLOCK }

    /** 바닐라 ExplosionDamageCalculator: 폭발 저항 덮어쓰기·피해 여부·밀림 배수. */
    public interface Calculator {
        /** null 이면 기본 저항(감쇠 없음 = Optional.empty 와 같은 뜻은 아님: 기본 계산기는 호출하지 않는다). */
        default Float blockResistance(int state) { return null; }
        default boolean shouldDamageEntity(Entity e) { return true; }
        default float knockbackMultiplier(Object e) { return 1.0F; }
    }

    /** 윈드 차지 계산기: 피해 없음, 막는 블록만 저항을 가지고 나머지는 광선이 감쇠하지 않는다. */
    public static final class WindCalculator implements Calculator {
        private final Float knockback;
        public WindCalculator(Float knockback) { this.knockback = knockback; }
        @Override public Float blockResistance(int state) { return B.isTag(state, "minecraft:blocks_wind_charge_explosions") ? 3600000.0F : -2.0F; }
        @Override public boolean shouldDamageEntity(Entity e) { return false; }
        @Override public float knockbackMultiplier(Object e) { return knockback == null ? 1.0F : knockback; }
    }

    private Calculator calculator;
    private int smallParticle = 22, largeParticle = 21;
    private String soundName = "minecraft:entity.generic.explode";

    public Explosion withCalculator(Calculator c, int small, int large, String sound) {
        calculator = c;
        smallParticle = small;
        largeParticle = large;
        soundName = sound;
        return this;
    }

    private final ChunkLevel level;
    private final Entity source;
    private final double x, y, z;
    private final float radius;
    private final boolean fire;
    private final BlockInteraction interaction;
    private final List<Pos> toBlow = new ArrayList<>();
    private final Map<Player, double[]> hitPlayers = new LinkedHashMap<>();
    private final boolean playerSource;

    public Explosion(ChunkLevel level, Entity source, double x, double y, double z, float radius, boolean fire, BlockInteraction interaction) {
        this.level = level;
        this.source = source;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.fire = fire;
        this.interaction = interaction;
        this.playerSource = false;
    }

    public float radius() { return radius; }

    public boolean canTriggerBlocks() { return interaction == BlockInteraction.TRIGGER_BLOCK; }

    public List<Pos> toBlow() { return toBlow; }

    /** Explosion.getSeenPercent(center, entity): 상자 안 격자점에서 폭발 중심이 보이는 비율. */
    public static float getSeenPercent(ChunkLevel level, double cx, double cy, double cz, AABB bb) {
        double d0 = 1.0 / ((bb.maxX() - bb.minX()) * 2.0 + 1.0);
        double d1 = 1.0 / ((bb.maxY() - bb.minY()) * 2.0 + 1.0);
        double d2 = 1.0 / ((bb.maxZ() - bb.minZ()) * 2.0 + 1.0);
        double d3 = (1.0 - Math.floor(1.0 / d0) * d0) / 2.0;
        double d4 = (1.0 - Math.floor(1.0 / d2) * d2) / 2.0;
        if (d0 < 0.0 || d1 < 0.0 || d2 < 0.0) return 0.0F;
        int i = 0, j = 0;
        for (double f = 0.0; f <= 1.0; f += d0) {
            for (double f1 = 0.0; f1 <= 1.0; f1 += d1) {
                for (double f2 = 0.0; f2 <= 1.0; f2 += d2) {
                    double px = lerp(f, bb.minX(), bb.maxX()), py = lerp(f1, bb.minY(), bb.maxY()), pz = lerp(f2, bb.minZ(), bb.maxZ());
                    if (Clip.clipCollider(level, px + d3, py, pz + d4, cx, cy, cz).miss()) i++;
                    j++;
                }
            }
        }
        return (float) i / j;
    }

    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    private float blockResistance(Pos pos, int state, int fluid) {
        if (BlockInfo.get().isAir(state) && Fluids.isEmpty(fluid)) return -1.0F;
        if (calculator != null) {
            Float r = calculator.blockResistance(state);
            if (r != null) return r < -1.0F ? -1.0F : r;   // 윈드: 저항 없는 블록은 감쇠하지 않는다
        }
        float block = BlockInfo.get().explosionResistance(Blocks.get().blockOf(state));
        float f = Fluids.isEmpty(fluid) ? 0.0F : 100.0F;   // 물·용암 폭발 저항 100
        return Math.max(block, f);
    }

    /** Explosion.explode(): 부서질 칸과 엔티티 피해·밀림. */
    public void explode() {
        Set<Pos> set = new HashSet<>();
        List<Pos> ordered = new ArrayList<>();
        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < 16; l++) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d0 = j / 15.0F * 2.0F - 1.0F;
                        double d1 = k / 15.0F * 2.0F - 1.0F;
                        double d2 = l / 15.0F * 2.0F - 1.0F;
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = radius * (0.7F + level.random().nextFloat() * 0.6F);
                        double px = x, py = y, pz = z;
                        for (; f > 0.0F; f -= 0.22500001F) {
                            Pos pos = new Pos((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
                            if (!level.isInWorldBounds(pos)) break;
                            int state = level.getBlockState(pos);
                            int fluid = level.getFluidState(pos);
                            float res = blockResistance(pos, state, fluid);
                            if (res >= 0.0F) f -= (res + 0.3F) * 0.3F;
                            if (f > 0.0F && shouldBlockExplode(pos, state, f)) {
                                if (set.add(pos)) ordered.add(pos);
                            }
                            px += d0 * 0.3F;
                            py += d1 * 0.3F;
                            pz += d2 * 0.3F;
                        }
                    }
                }
            }
        }
        toBlow.addAll(ordered);
        float f2 = radius * 2.0F;
        int k1 = (int) Math.floor(x - f2 - 1.0), l1 = (int) Math.floor(x + f2 + 1.0);
        int i2 = (int) Math.floor(y - f2 - 1.0), i1 = (int) Math.floor(y + f2 + 1.0);
        int j2 = (int) Math.floor(z - f2 - 1.0), j1 = (int) Math.floor(z + f2 + 1.0);
        AABB box = new AABB(k1, i2, j2, l1, i1, j1);
        for (Entity e : level.getEntities(source, box, en -> true)) {
            if (e.ignoreExplosion()) continue;
            double[] kb = affect(e.x(), e.y(), e.eyeYForExplosion(), e.z(), e.boundingBox(), f2, e);
            if (kb == null) continue;
            double m = kb[3];
            if (e instanceof LivingEntity le) m *= 1.0 - le.attributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
            if (calculator != null) m *= calculator.knockbackMultiplier(e);
            e.setDeltaMovement(e.vx() + kb[0] * m, e.vy() + kb[1] * m, e.vz() + kb[2] * m);
        }
        List<Player> players = new ArrayList<>();
        level.forEachPlayerIn(box, players::add);
        for (Player p : players) {
            double[] kb = affect(p.x(), p.y(), p.eyeY(), p.z(), p.boundingBox(), f2, p);
            if (kb == null) continue;
            if (!p.vitals().isSpectator() && (!p.vitals().isCreative() || !p.vitals().isFlying())) {
                double pm = kb[3] * (calculator != null ? calculator.knockbackMultiplier(p) : 1.0F);
                hitPlayers.put(p, new double[] {kb[0] * pm, kb[1] * pm, kb[2] * pm});
            }
        }
    }

    /** 대상 하나에 피해를 주고 [방향 x, y, z, 밀림 세기]를 돌려준다(범위 밖이면 null). */
    private double[] affect(double tx, double ty, double teye, double tz, AABB bb, float f2, Object target) {
        double ddx = tx - x, ddy = ty - y, ddz = tz - z;
        double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / f2;
        if (dist > 1.0) return null;
        double dx = tx - x, dy = teye - y, dz = tz - z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0.0) return null;
        dx /= len;
        dy /= len;
        dz /= len;
        float seen = getSeenPercent(level, x, y, z, bb);
        double d10 = (1.0 - dist) * seen;
        float damage = (float) ((d10 * d10 + d10) / 2.0 * 7.0 * f2 + 1.0);
        if (calculator == null || !(target instanceof Entity te) || calculator.shouldDamageEntity(te)) {
            if (!(calculator != null && target instanceof Player)) hurtTarget(target, damage);
        }
        return new double[] {dx, dy, dz, d10};
    }

    protected boolean shouldBlockExplode(Pos pos, int state, float power) { return true; }

    private void hurtTarget(Object t, float damage) {
        Mob attacker = source instanceof Mob m ? m : (source instanceof org.domi.furnace.server.entity.PrimedTnt tnt && tnt.getOwner() instanceof Mob m2 ? m2 : null);
        Entity.Damage dmg = Entity.Damage.EXPLOSION;
        if (t instanceof LivingEntity le) {
            le.hurtByVia(dmg, damage, attacker, source);
        } else if (t instanceof Player p) {
            p.vitals().hurt(DamageType.EXPLOSION, damage, attacker);
        } else if (t instanceof Entity e) {
            e.hurt(dmg, damage);
        }
    }

    /** Explosion.finalizeExplosion(particles): 소리·패킷, 블록 부수기·불. */
    public void finalizeExplosion(boolean particles) {
        level.playSound(null, x, y, z, soundName, "blocks", 4.0F, (1.0F + (level.random().nextFloat() - level.random().nextFloat()) * 0.2F) * 0.7F);
        boolean destroy = interaction != BlockInteraction.KEEP;
        // 패킷: 4096(64칸) 안 플레이어들
        int sound = org.domi.furnace.server.item.ItemContext.id("minecraft:sound_event", soundName);
        level.forEachPlayer(p -> {
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            if (dx * dx + dy * dy + dz * dz < 4096.0) {
                double[] kb = hitPlayers.get(p);
                p.send(new ExplodePacket(x, y, z, radius, new ArrayList<>(toBlow), kb == null ? 0 : (float) kb[0], kb == null ? 0 : (float) kb[1],
                        kb == null ? 0 : (float) kb[2], interaction.ordinal(), smallParticle, radius >= 2.0F && destroy ? largeParticle : smallParticle, sound));
            }
        });
        if (interaction == BlockInteraction.TRIGGER_BLOCK) {
            for (Pos pos : toBlow) {
                int st = level.getBlockState(pos);
                BlockBehaviors.of(st).onExplosionHit(st, level, pos, this);
            }
        }
        if (destroy && interaction != BlockInteraction.TRIGGER_BLOCK) {
            List<Object[]> drops = new ArrayList<>();   // {ItemStack, Pos}
            org.domi.furnace.server.block.RandomSource rnd = level.random();
            shuffle(toBlow, rnd);
            for (Pos pos : toBlow) {
                int state = level.getBlockState(pos);
                if (BlockInfo.get().isAir(state)) continue;
                BlockBehavior b = BlockBehaviors.of(state);
                if (b.dropFromExplosion() && !interactionTriggerOnly()) {
                    Object be = level.getBlockEntity(pos);
                    Float rad = interaction == BlockInteraction.DESTROY_WITH_DECAY ? radius : null;
                    for (ItemStack s : b.getDrops(state, level, pos, be, null, ItemStack.EMPTY, rad)) addOrAppendStack(drops, s, pos);
                }
                level.setBlock(pos, 0, 3, 512);
                b.wasExploded(level, pos, this);
            }
            for (Object[] d : drops) level.popResource((Pos) d[1], (ItemStack) d[0]);
        }
        if (fire) {
            for (Pos pos : toBlow) {
                if (level.random().nextInt(3) == 0 && BlockInfo.get().isAir(level.getBlockState(pos)) && isSolidRender(level.getBlockState(pos.below()))) {
                    level.setBlock(pos, org.domi.furnace.server.block.types.BaseFireBlock.getState(level, pos), 3, 512);
                }
            }
        }
    }

    /** BlockState.isSolidRender: 가리는 블록이고 가림 모양이 꽉 찬 상자. */
    private static boolean isSolidRender(int state) {
        if (!BlockInfo.get().canOcclude(Blocks.get().blockOf(state))) return false;
        double[] b = BlockInfo.get().occlusionShape(state).bounds();
        return b != null && b[0] <= 0.0 && b[1] <= 0.0 && b[2] <= 0.0 && b[3] >= 1.0 && b[4] >= 1.0 && b[5] >= 1.0 && BlockInfo.get().occlusionShape(state).boxCount() == 1;
    }

    private boolean interactionTriggerOnly() { return interaction == BlockInteraction.TRIGGER_BLOCK; }

    private static void shuffle(List<Pos> list, org.domi.furnace.server.block.RandomSource r) {
        for (int i = list.size(); i > 1; i--) {   // Util.shuffle: Collections.shuffle 과 같은 방식(뒤에서부터 교환)
            int j = r.nextInt(i);
            Pos tmp = list.get(i - 1);
            list.set(i - 1, list.get(j));
            list.set(j, tmp);
        }
    }

    /** Explosion.addOrAppendStack: 같은 종류면 한 스택으로 합친다(최대 16). */
    private static void addOrAppendStack(List<Object[]> drops, ItemStack stack, Pos pos) {
        for (Object[] d : drops) {
            ItemStack s = (ItemStack) d[0];
            if (s.sameItemSameComponents(stack)) {
                int max = org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(s);
                int can = Math.min(16, max) - s.count();
                int add = Math.min(stack.count(), Math.max(can, 0));
                if (add > 0) {
                    d[0] = s.withCount(s.count() + add);
                    stack = stack.withCount(stack.count() - add);
                    if (stack.isEmpty()) return;
                }
            }
        }
        drops.add(new Object[] {stack, pos});
    }
}
