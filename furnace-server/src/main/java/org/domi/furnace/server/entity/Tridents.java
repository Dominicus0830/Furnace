// 바닐라 TridentItem: 오래 쓰기(72000틱) 시작, 놓을 때 10틱 이상 당겼으면 던진다(속도 2.5, 흩어짐 1, 내구도 1).
// 급류: 물·비 안에서만 쓰고, 놓으면 돌진(밀기는 클라이언트가 스스로 한다) + 20틱 회전 공격(피해 8)과 riptide 소리.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBreaking;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemTools;

public final class Tridents {
    private Tridents() {}

    public static final int USE_TICKS = 72000, THROW_THRESHOLD = 10;
    public static final float SHOOT_POWER = 2.5F;

    public static boolean isTrident(ItemStack s) { return ItemTools.itemName(s).equals("minecraft:trident"); }

    /** isTooDamagedToUse: 피해 ≥ 최대 − 1. */
    static boolean tooDamaged(ItemStack s) { return ItemTools.damage(s) >= ItemTools.maxDamage(s) - 1; }

    /** getTridentSpinAttackStrength: 급류 1.5 + 0.75 × (레벨 − 1). */
    static float spinStrength(ItemStack s) {
        int lvl = ItemTools.enchantmentLevel(s, "minecraft:riptide");
        return lvl > 0 ? 1.5F + 0.75F * (lvl - 1) : 0.0F;
    }

    /** startAutoSpinAttack(20, 8.0F, stack). */
    public static final int SPIN_TICKS = 20;
    public static final float SPIN_DAMAGE = 8.0F;

    /** Entity.isInWaterOrRain: 물에 닿았거나(지난 틱) 발 칸·머리 높이 칸에 비가 온다. */
    public static boolean isInWaterOrRain(ChunkLevel level, Player p) {
        if (p.wasTouchingWater) return true;
        if (level == null) return false;
        var pos = p.blockPosition();
        return level.isRainingAt(pos)
                || level.isRainingAt(new org.domi.furnace.server.block.Pos(pos.x(), (int) Math.floor(p.boundingBox().maxY()), pos.z()));
    }

    /** 급류 레벨별 소리(trident_sound 효과: riptide_1..3). 급류가 없으면 던지는 소리. */
    static String sound(ItemStack s) {
        int lvl = ItemTools.enchantmentLevel(s, "minecraft:riptide");
        if (lvl <= 0) return "item.trident.throw";
        return "item.trident.riptide_" + Math.min(lvl, 3);
    }

    /** use: 너무 닳았거나 급류인데 물·비 밖이면 실패, 아니면 쓰기 시작. */
    static boolean use(ChunkLevel level, BlockActor actor, Player p, int hand) {
        ItemStack s = actor.itemInHand(hand);
        if (tooDamaged(s)) return true;
        if (spinStrength(s) > 0.0F && !isInWaterOrRain(level, p)) return true;
        RangedWeapons.start(p, hand, USE_TICKS);
        return true;
    }

    /**
     * releaseUsing: 10틱 이상이고 (급류가 아니거나 물·비 안)이고 너무 닳지 않았으면 내구도 1. 급류가 아니면 ThrownTrident 를
     * shootFromRotation(0, 2.5, 1)·창조 전용 줍기·소리·손에서 뺀다. 통계. 급류면 회전 공격을 시작하고 그 레벨 소리.
     * 돌진 밀기(push)와 땅에서 1.2 띄우기는 클라이언트가 같은 releaseUsing 을 돌려 스스로 한다(서버 플레이어 이동은 클라이언트 것).
     */
    static void release(ChunkLevel level, BlockActor actor, Player p, int hand, int remaining) {
        int ticks = USE_TICKS - remaining;
        if (ticks < THROW_THRESHOLD) return;
        ItemStack s = actor.itemInHand(hand);
        float spin = spinStrength(s);
        if (spin > 0.0F && !isInWaterOrRain(level, p)) return;
        if (tooDamaged(s)) return;
        String sound = sound(s);
        ItemStack original = s;
        BlockBreaking.hurtAndBreak(level, actor, hand, s, 1);
        ItemStack cur = actor.itemInHand(hand);
        if (spin == 0.0F) {
            var t = new ThrownTrident(level, level.nextEntityId(), p, cur);
            t.shootFromRotation(p.pitch(), p.yaw(), 0.0F, SHOOT_POWER, 1.0F, p.knownMovement(), p.onGround());
            if (p.hasInfiniteMaterials()) t.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            if (!level.addFreshEntity(t)) {   // 플러그인이 던지기를 취소: 내구도도 쓰지 않는다
                actor.setItemInHand(hand, original);
                org.domi.furnace.server.plugin.ClientResync.inventory(p);
                return;
            }
            level.playSound(null, t, sound, "players", 1.0F, 1.0F);
            if (!p.hasInfiniteMaterials()) actor.setItemInHand(hand, ItemStack.EMPTY);
        }
        p.awardStat("minecraft:used:minecraft:trident", 1);
        if (spin > 0.0F) {
            p.startAutoSpinAttack(SPIN_TICKS, SPIN_DAMAGE, cur);
            level.playSound(null, p, sound, "players", 1.0F, 1.0F);
        }
    }

    /**
     * LivingEntity.aiStep 의 회전 공격: 남은 틱을 하나 줄이고 checkAutoSpinAttack(이동 전·후 상자를 합친 곳)에서 처음 만난 생물을
     * doAutoAttackOnTouch(= Player.attack, 피해는 autoSpinAttackDmg) 하고 끝낸다. 끝나면 회전 비트를 끈다.
     * 되튕김(속도 × -0.2)과 벽 충돌로 끝나는 것은 클라이언트 이동이라 서버는 보지 않는다.
     */
    public static void tickSpin(PlayerMelee.World w, Player p, java.util.function.Consumer<Targetable> attack) {
        if (p.autoSpinAttackTicks <= 0) return;
        p.autoSpinAttackTicks--;
        double[] mv = p.knownMovement();
        var now = p.boundingBox();
        var box = new org.domi.furnace.server.entity.AABB(Math.min(now.minX(), now.minX() - mv[0]), Math.min(now.minY(), now.minY() - mv[1]),
                Math.min(now.minZ(), now.minZ() - mv[2]), Math.max(now.maxX(), now.maxX() - mv[0]), Math.max(now.maxY(), now.maxY() - mv[1]),
                Math.max(now.maxZ(), now.maxZ() - mv[2]));
        Targetable hit = null;
        if (w.level() != null) {
            for (Entity e : w.level().getEntities(null, box, e -> true)) if (e instanceof LivingEntity le) { hit = le; break; }
        }
        if (hit == null) {
            for (Player o : w.players()) if (o != p && o.isAlive() && o.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && o.boundingBox().intersects(box)) { hit = o; break; }
        }
        if (hit != null) {
            attack.accept(hit);
            p.autoSpinAttackTicks = 0;
        }
        if (p.autoSpinAttackTicks <= 0) {
            p.autoSpinAttackDmg = 0.0F;
            p.autoSpinAttackItem = null;
        }
    }
}
