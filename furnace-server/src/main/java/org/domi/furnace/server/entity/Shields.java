// 방패 막기(바닐라 ShieldItem·LivingEntity.isBlocking/isDamageSourceBlocked·Player.hurtCurrentlyUsedShield/blockUsingShield/disableShield).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

public final class Shields {
    private Shields() { }

    /** ShieldItem.getUseDuration. */
    public static final int USE_DURATION = 72000;
    /** isBlocking: 사용 시작 뒤 이만큼 지나야 막는다. */
    public static final int BLOCK_DELAY = 5;
    /** disableShield: 재사용 대기 100틱. */
    public static final int DISABLE_TICKS = 100;

    /**
     * 막을 수 있는지 볼 피해 원인. 위치가 없으면(hasPos=false) 막지 못한다(DamageSource.getSourcePosition == null).
     * directLiving: 직접 원인이 LivingEntity(투사체가 아님) — blockUsingShield 대상. piercing: 관통 화살.
     */
    public record Source(String typeKey, boolean hasPos, double x, double z, Targetable attacker, boolean directLiving, boolean piercing,
                         org.domi.furnace.server.block.BlockLevel level) {
        public static Source none(String typeKey) { return new Source(typeKey, false, 0, 0, null, false, false, null); }
    }

    public static boolean isShield(ItemStack s) {
        return !s.isEmpty() && "minecraft:shield".equals(ItemTools.itemName(s));
    }

    public static int shieldId() {
        Integer id = ItemContext.installed() ? ItemContext.id("minecraft:item", "minecraft:shield") : null;
        return id == null ? -1 : id;
    }

    /** AxeItem 인가(LivingEntity.canDisableShield: 주 손이 도끼). */
    public static boolean isAxe(ItemStack s) {
        return !s.isEmpty() && ItemTools.itemName(s).endsWith("_axe");
    }

    /** LivingEntity.canDisableShield. */
    public static boolean canDisableShield(Targetable t) {
        if (t instanceof LivingEntity le) return isAxe(le.getMainHandItem());
        if (t instanceof Player p) return isAxe(p.inventory().get(p.inventory().heldWindowSlot()));
        return false;
    }

    /**
     * LivingEntity.isDamageSourceBlocked: bypasses_shield 가 아니고, 막는 중이고, 관통 화살이 아니고, 원인 위치가 있으면
     * 원인→나 방향(수평, 정규화)과 calculateViewVector(0, yRot) 의 내적이 음수일 때.
     */
    public static boolean isDamageSourceBlocked(boolean blocking, float yRot, double x, double z, Source src) {
        if (src.piercing()) return false;
        if (DamageType.keyHasTag(src.typeKey(), "bypasses_shield") || !blocking || !src.hasPos()) return false;
        float g = -yRot * (float) (Math.PI / 180.0);
        double vx = org.domi.furnace.server.entity.ai.control.Mth.sin(g), vz = org.domi.furnace.server.entity.ai.control.Mth.cos(g);
        double dx = x - src.x(), dz = z - src.z();
        double len = Math.sqrt(dx * dx + dz * dz);   // Vec3.normalize: 1e-4 미만이면 0
        if (len < 1.0E-4) return false;
        dx /= len;
        dz /= len;
        return dx * vx + dz * vz < 0.0;
    }

    // ------------------------------------------------------------------ 플레이어

    /** 쓰고 있는 손의 창 칸(없으면 -1). */
    static int usingWindowSlot(Player p) {
        if (p.usingHand < 0) return -1;
        return p.usingHand == 0 ? p.inventory().heldWindowSlot() : 45;
    }

    /** Player 의 isBlocking: 쓰는 아이템이 방패이고 72000 - 남은 틱 ≥ 5. */
    public static boolean isBlocking(Player p) {
        int ws = usingWindowSlot(p);
        if (ws < 0 || !isShield(p.inventory().get(ws))) return false;
        return USE_DURATION - p.usingTicks >= BLOCK_DELAY;
    }

    /** ShieldItem.use: 재사용 대기가 아니면 사용 시작(72000). */
    public static boolean tryStart(Player p, int hand, ItemStack s) {
        if (!isShield(s)) return false;
        if (p.isOnCooldown(s.itemId())) return false;
        p.usingHand = hand;
        p.usingTicks = USE_DURATION;
        return true;
    }

    /**
     * Player.hurtCurrentlyUsedShield: 사용 통계, 피해 3 이상이면 1 + floor(피해) 만큼 hurtAndBreak(크리에이티브는 그대로, 내구성 확률 감소).
     * 부서지면 손 비우기·부서짐 이벤트(47/48)·broken 통계, 사용 끝, 다른 사람에게 item.shield.break.
     */
    public static void hurtCurrentlyUsedShield(Player p, float amount, org.domi.furnace.server.block.BlockLevel level) {
        int ws = usingWindowSlot(p);
        if (ws < 0) return;
        ItemStack stack = p.inventory().get(ws);
        if (!isShield(stack)) return;
        p.awardStat("minecraft:used:minecraft:shield", 1);
        if (amount < 3.0F) return;
        int dmg = 1 + (int) Math.floor(amount);
        int hand = p.usingHand;
        if (!hurtAndBreak(p, ws, stack, dmg, level == null ? null : level.random())) return;
        p.awardStat("minecraft:broken:minecraft:shield", 1);
        var ev = new org.domi.furnace.server.net.protocol.play.EntityEvent(p.entityId(), (byte) (47 + hand));
        p.send(ev);
        p.vitals().broadcastToTrackers(ev);
        p.usingHand = -1;
        if (level != null) {
            float pitch = 0.8F + level.random().nextFloat() * 0.4F;
            playSoundExceptSelf(p, level, "minecraft:item.shield.break", 0.8F, pitch);
        }
    }

    /** ItemStack.hurtAndBreak(플레이어): 부서졌으면 true(칸은 하나 줄인다). */
    static boolean hurtAndBreak(Player p, int ws, ItemStack stack, int amount, RandomSource random) {
        if (!ItemTools.isDamageable(stack) || p.hasInfiniteMaterials()) return false;
        int unbreaking = ItemTools.enchantmentLevel(stack, "minecraft:unbreaking");
        if (unbreaking > 0 && random != null) {
            float chance = (float) unbreaking / (2.0F + (unbreaking - 1));
            int removed = 0;
            for (int j = 0; j < amount; j++) if (random.nextFloat() < chance) removed++;
            amount -= removed;
        }
        if (amount <= 0) return false;
        int nd = ItemTools.damage(stack) + amount;
        ItemStack damaged = ItemTools.withDamage(stack, nd);
        if (nd >= ItemTools.maxDamage(stack)) {
            p.inventory().set(ws, damaged.withCount(damaged.count() - 1));
            return true;
        }
        p.inventory().set(ws, damaged);
        return false;
    }

    /** Player.blockUsingShield(attacker): 막은 쪽이 0.5 밀리고(blockedByShield), 공격자가 도끼면 disableShield. */
    public static void blockUsingShield(Player p, Targetable attacker) {
        p.knockback(0.5, p.x() - attacker.x(), p.z() - attacker.z());
        if (canDisableShield(attacker)) disableShield(p);
    }

    /** Player.disableShield: 방패 100틱 대기, 사용 끝, 엔티티 이벤트 30(본인·보는 사람). */
    public static void disableShield(Player p) {
        int id = shieldId();
        if (id >= 0) p.addCooldown(id, DISABLE_TICKS);
        p.usingHand = -1;
        var ev = new org.domi.furnace.server.net.protocol.play.EntityEvent(p.entityId(), (byte) 30);
        p.send(ev);
        p.vitals().broadcastToTrackers(ev);
    }

    /** Player.playSound: 그 플레이어 자신은 빼고(클라이언트가 직접 낸다). */
    private static void playSoundExceptSelf(Player p, org.domi.furnace.server.block.BlockLevel level, String sound, float vol, float pitch) {
        org.domi.furnace.server.block.BlockActor self = new org.domi.furnace.server.block.BlockActor() {
            public float yRot() { return p.yaw(); }
            public float xRot() { return p.pitch(); }
            public double x() { return p.x(); }
            public double y() { return p.y(); }
            public double z() { return p.z(); }
            public double eyeHeight() { return 1.62; }
            public boolean isSecondaryUseActive() { return false; }
            public boolean mayBuild() { return true; }
            public boolean instabuild() { return p.hasInfiniteMaterials(); }
            public boolean isSpectator() { return false; }
            public boolean isCreative() { return p.hasInfiniteMaterials(); }
            public int permissionLevel() { return 0; }
            public ItemStack itemInHand(int hand) { return ItemStack.EMPTY; }
            public void setItemInHand(int hand, ItemStack stack) { }
            public Object handle() { return p; }
        };
        level.playSound(self, p.x(), p.y(), p.z(), sound, "players", vol, pitch);
    }
}
