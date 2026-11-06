// 플레이어의 활·쇠뇌(바닐라 BowItem·CrossbowItem·ProjectileWeaponItem·ArrowItem): 오래 쓰기 시작, 사용 중 틱(쇠뇌 장전 소리), 놓을 때 발사·장전.
// 마법은 데이터 주도 효과 대신 관련된 것만 직접: 힘·밀어내기·화염(화살 쪽), 무한(ammo_use), 다중 발사(개수·퍼짐), 빠른 장전, 관통.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBreaking;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.ItemTools;

public final class RangedWeapons {
    private RangedWeapons() {}

    public static final int BOW_USE_TICKS = 72000;
    private static final String CHARGED = "minecraft:charged_projectiles", INTANGIBLE = "minecraft:intangible_projectile";

    public static boolean isBow(ItemStack s) { return ItemTools.itemName(s).equals("minecraft:bow"); }

    public static boolean isCrossbow(ItemStack s) { return ItemTools.itemName(s).equals("minecraft:crossbow"); }

    public static boolean isRangedWeapon(ItemStack s) { return isBow(s) || isCrossbow(s); }

    /** 놓을 때 처리하는 오래 쓰기 아이템(활·쇠뇌·삼지창). */
    public static boolean usesRelease(ItemStack s) { return isRangedWeapon(s) || Tridents.isTrident(s); }

    private static boolean isArrow(ItemStack s) { return !s.isEmpty() && ItemContext.isInTag("minecraft:item", "minecraft:arrows", s.itemId()); }

    private static boolean isFirework(ItemStack s) { return ItemTools.itemName(s).equals("minecraft:firework_rocket"); }

    /** getSupportedHeldProjectiles: 활은 화살, 쇠뇌는 화살 + 폭죽 로켓. */
    private static boolean heldProjectile(ItemStack weapon, ItemStack s) { return isArrow(s) || (isCrossbow(weapon) && isFirework(s)); }

    // ------------------------------------------------------------------ 탄약(Player.getProjectile)

    /** 탄약 칸: 창 칸 번호(-1 = 창조 모드의 새 화살)와 스택. */
    record Ammo(int windowSlot, ItemStack stack) {
        static final Ammo NONE = new Ammo(-1, ItemStack.EMPTY);
    }

    /** Player.getProjectile: 손(왼손 먼저)에 든 지원 탄약 → 인벤토리 순서(0..40)의 화살 → 창조면 새 화살. */
    static Ammo projectile(Player p, BlockActor actor, ItemStack weapon) {
        if (!isRangedWeapon(weapon)) return Ammo.NONE;
        ItemStack off = actor.itemInHand(BlockActor.OFF_HAND), main = actor.itemInHand(BlockActor.MAIN_HAND);
        if (heldProjectile(weapon, off)) return new Ammo(45, off);
        if (heldProjectile(weapon, main)) return new Ammo(p.inventory().heldWindowSlot(), main);
        for (int i = 0; i <= 40; i++) {
            int ws = PlayerInventory.windowSlot(i);
            ItemStack s = slot(p, actor, ws);
            if (isArrow(s)) return new Ammo(ws, s);
        }
        return p.hasInfiniteMaterials() ? new Ammo(-1, stack("arrow")) : Ammo.NONE;
    }

    private static ItemStack slot(Player p, BlockActor actor, int ws) {
        if (ws == 45) return actor.itemInHand(BlockActor.OFF_HAND);
        if (ws == p.inventory().heldWindowSlot()) return actor.itemInHand(BlockActor.MAIN_HAND);
        return p.inventory().get(ws);
    }

    private static void setSlot(Player p, BlockActor actor, int ws, ItemStack s) {
        if (ws < 0) return;
        if (ws == 45) actor.setItemInHand(BlockActor.OFF_HAND, s);
        else if (ws == p.inventory().heldWindowSlot()) actor.setItemInHand(BlockActor.MAIN_HAND, s);
        else p.inventory().set(ws, s);
    }

    private static ItemStack stack(String item) { return new ItemStack(ItemContext.id("minecraft:item", item), 1); }

    // ------------------------------------------------------------------ 마법 값

    /** processProjectileCount: 다중 발사 +2 × 레벨. */
    static int projectileCount(ItemStack weapon) {
        int m = ItemTools.enchantmentLevel(weapon, "minecraft:multishot");
        return Math.max(0, 1 + (m > 0 ? 2 * m : 0));
    }

    /** processProjectileSpread: 다중 발사 +10° × 레벨. */
    static float projectileSpread(ItemStack weapon) {
        int m = ItemTools.enchantmentLevel(weapon, "minecraft:multishot");
        return Math.max(0.0F, m > 0 ? 10.0F * m : 0.0F);
    }

    /** processAmmoUse: 무한은 탄약이 minecraft:arrow 일 때 0. */
    static int ammoUse(ItemStack weapon, ItemStack ammo) {
        int n = 1;
        if (ItemTools.enchantmentLevel(weapon, "minecraft:infinity") > 0 && ItemTools.itemName(ammo).equals("minecraft:arrow")) n = 0;
        return Math.max(0, n);
    }

    // ------------------------------------------------------------------ ProjectileWeaponItem.draw / useAmmo

    /** draw(weapon, ammo, shooter): 개수만큼 — 첫 발은 진짜 탄약에서, 나머지는 사본(줍기 불가 표시). 진짜 탄약은 그 칸에서 줄인다. */
    static List<ItemStack> draw(Player p, BlockActor actor, ItemStack weapon, Ammo ammo) {
        if (ammo.stack().isEmpty()) return List.of();
        int n = projectileCount(weapon);
        List<ItemStack> list = new ArrayList<>(n);
        ItemStack copy = ammo.stack();
        for (int i = 0; i < n; i++) {
            ItemStack s = useAmmo(p, actor, weapon, i == 0 ? ammo : new Ammo(-1, copy), i > 0);
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    private static ItemStack useAmmo(Player p, BlockActor actor, ItemStack weapon, Ammo ammo, boolean isCopy) {
        ItemStack a = ammo.stack();
        int use = !isCopy && !p.hasInfiniteMaterials() ? ammoUse(weapon, a) : 0;
        if (use > a.count()) return ItemStack.EMPTY;
        if (use == 0) return ItemTools.with(a.withCount(1), INTANGIBLE, Map.of());
        setSlot(p, actor, ammo.windowSlot(), a.withCount(a.count() - use));
        return a.withCount(use);
    }

    // ------------------------------------------------------------------ 사용 시작(Item.use)

    /** 활·쇠뇌(그리고 낚싯대·삼지창) use: 처리했으면 true(시작·발사·실패 모두). 해당 아이템이 아니면 false. */
    public static boolean use(ChunkLevel level, BlockActor actor, Player p, int hand) {
        ItemStack weapon = actor.itemInHand(hand);
        if (FishingHook.isRod(weapon)) return FishingRods.use(level, actor, p, hand);
        if (Tridents.isTrident(weapon)) return Tridents.use(level, actor, p, hand);
        if (isBow(weapon)) {
            boolean has = !projectile(p, actor, weapon).stack().isEmpty();
            if (p.hasInfiniteMaterials() || has) start(p, hand, BOW_USE_TICKS);
            return true;
        }
        if (!isCrossbow(weapon)) return false;
        List<?> charged = chargedRaw(weapon);
        if (!charged.isEmpty()) {
            performShooting(level, actor, p, hand, containsFirework(charged) ? 1.6F : 3.15F, 1.0F);
            return true;
        }
        if (!projectile(p, actor, weapon).stack().isEmpty()) {
            p.crossbowStartSound = false;
            p.crossbowMidSound = false;
            start(p, hand, crossbowUseDuration(weapon));
        }
        return true;
    }

    static void start(Player p, int hand, int ticks) {
        p.startUsingItem(hand, ticks);
    }

    // ------------------------------------------------------------------ 사용 중(LivingEntity.updateUsingItem)

    /** 틱마다: onUseTick(남은 틱) 후 하나 줄인다. 활은 72000틱이 다 되면 그냥 끝(쇠뇌는 놓을 때까지 계속). */
    public static void tick(Player p, ChunkLevel level, ItemStack stack) {
        onUseTick(p, level, stack, p.usingTicks);
        if (--p.usingTicks == 0 && (isBow(stack) || Tridents.isTrident(stack))) p.stopUsingItem();
    }

    /** CrossbowItem.onUseTick: 장전 진행 0.2·0.5 에서 시작·중간 소리(한 번씩). */
    static void onUseTick(Player p, ChunkLevel level, ItemStack stack, int remaining) {
        if (!isCrossbow(stack) || level == null) return;
        String[] sounds = chargingSounds(stack);
        float progress = (float) (crossbowUseDuration(stack) - remaining) / chargeDuration(stack);
        if (progress < 0.2F) {
            p.crossbowStartSound = false;
            p.crossbowMidSound = false;
        }
        if (progress >= 0.2F && !p.crossbowStartSound) {
            p.crossbowStartSound = true;
            if (sounds[0] != null) level.playSound(null, p.x(), p.y(), p.z(), sounds[0], "players", 0.5F, 1.0F);
        }
        if (progress >= 0.5F && !p.crossbowMidSound) {
            p.crossbowMidSound = true;
            if (sounds[1] != null) level.playSound(null, p.x(), p.y(), p.z(), sounds[1], "players", 0.5F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ 놓기(releaseUsing)

    /** ServerPlayer.releaseUsingItem: 쓰던 손의 활·쇠뇌에 releaseUsing(남은 틱), 쇠뇌는 한 번 더 사용 틱, 그리고 사용 끝. */
    public static void release(ChunkLevel level, BlockActor actor, Player p) {
        int hand = p.usingHand;
        if (hand < 0) return;
        ItemStack weapon = actor.itemInHand(hand);
        int remaining = p.usingTicks;
        if (isBow(weapon)) bowRelease(level, actor, p, hand, remaining);
        else if (Tridents.isTrident(weapon)) Tridents.release(level, actor, p, hand, remaining);
        else if (isCrossbow(weapon)) {
            crossbowRelease(level, actor, p, hand, remaining);
            tick(p, level, actor.itemInHand(hand));
        }
        p.stopUsingItem();
    }

    /** BowItem.getPowerForTime: (t² + 2t)/3, t = 틱/20, 최대 1. */
    public static float bowPower(int ticks) {
        float f = ticks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        return f;
    }

    /** BowItem.releaseUsing: 탄약 → 충전 → 0.1 미만이면 아무 일 없음 → draw → 발사(속도 힘×3, 흩어짐 1, 힘 1 이면 치명타) → 소리 → 통계. */
    static void bowRelease(ChunkLevel level, BlockActor actor, Player p, int hand, int remaining) {
        ItemStack weapon = actor.itemInHand(hand);
        Ammo ammo = projectile(p, actor, weapon);
        if (ammo.stack().isEmpty()) return;
        int ticks = BOW_USE_TICKS - remaining;
        float power = bowPower(ticks);
        if (power < 0.1) return;
        List<ItemStack> list = draw(p, actor, weapon, ammo);
        if (!list.isEmpty() && shoot(level, actor, p, hand, weapon, list, power * 3.0F, 1.0F, power == 1.0F) == 0) {
            // 플러그인이 발사를 모두 취소: 뽑은 탄약을 돌려놓고, 클라이언트가 먼저 줄인 칸을 되돌린다(소리·통계 없음)
            setSlot(p, actor, ammo.windowSlot(), ammo.stack());
            org.domi.furnace.server.plugin.ClientResync.inventory(p);
            return;
        }
        level.playSound(null, p.x(), p.y(), p.z(), "entity.arrow.shoot", "players", 1.0F,
                1.0F / (level.random().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        p.awardStat("minecraft:used:minecraft:bow", 1);
    }

    // ------------------------------------------------------------------ 쇠뇌

    /** CrossbowItem.getChargeDuration: floor((1.25 − 0.25 × 빠른 장전) × 20), 0 아래로는 안 내려감. */
    public static int chargeDuration(ItemStack crossbow) {
        int q = ItemTools.enchantmentLevel(crossbow, "minecraft:quick_charge");
        float seconds = Math.max(0.0F, 1.25F + (q > 0 ? -0.25F * q : 0.0F));
        float t = seconds * 20.0F;
        int i = (int) t;
        return t < i ? i - 1 : i;
    }

    /** CrossbowItem.getUseDuration: 장전 시간 + 3. */
    public static int crossbowUseDuration(ItemStack crossbow) { return chargeDuration(crossbow) + 3; }

    /** crossbow_charging_sounds: 빠른 장전이면 레벨별 시작 소리(중간 없음), 아니면 기본 시작·중간·끝. */
    private static String[] chargingSounds(ItemStack crossbow) {
        int q = ItemTools.enchantmentLevel(crossbow, "minecraft:quick_charge");
        if (q > 0) return new String[] {"item.crossbow.quick_charge_" + Math.min(q, 3), null, "item.crossbow.loading_end"};
        return new String[] {"item.crossbow.loading_start", "item.crossbow.loading_middle", "item.crossbow.loading_end"};
    }

    static List<?> chargedRaw(ItemStack crossbow) {
        return ItemTools.component(crossbow, CHARGED) instanceof List<?> l ? l : List.of();
    }

    public static boolean isCharged(ItemStack crossbow) { return !chargedRaw(crossbow).isEmpty(); }

    @SuppressWarnings("unchecked")
    static List<ItemStack> chargedProjectiles(ItemStack crossbow) {
        List<ItemStack> out = new ArrayList<>();
        for (Object o : chargedRaw(crossbow)) if (o instanceof Map<?, ?> m) out.add(ItemStackCodecs.fromCanonical((Map<String, Object>) m));
        return out;
    }

    private static boolean containsFirework(List<?> raw) {
        for (Object o : raw) if (o instanceof Map<?, ?> m && "minecraft:firework_rocket".equals(m.get("id"))) return true;
        return false;
    }

    /** CrossbowItem.releaseUsing: 다 당겼고 비었으면 장전하고 끝 소리. */
    static void crossbowRelease(ChunkLevel level, BlockActor actor, Player p, int hand, int remaining) {
        ItemStack weapon = actor.itemInHand(hand);
        int ticks = crossbowUseDuration(weapon) - remaining;
        float power = Math.min(1.0F, (float) ticks / chargeDuration(weapon));
        if (power >= 1.0F && !isCharged(weapon) && tryLoad(actor, p, hand)) {
            String end = chargingSounds(weapon)[2];
            level.playSound(null, p.x(), p.y(), p.z(), end, "players", 1.0F, 1.0F / (level.random().nextFloat() * 0.5F + 1.0F) + 0.2F);
        }
    }

    /** CrossbowItem.tryLoadProjectiles: draw 한 것을 charged_projectiles 로. */
    private static boolean tryLoad(BlockActor actor, Player p, int hand) {
        ItemStack weapon = actor.itemInHand(hand);
        List<ItemStack> list = draw(p, actor, weapon, projectile(p, actor, weapon));
        if (list.isEmpty()) return false;
        List<Object> raw = new ArrayList<>(list.size());
        for (ItemStack s : list) raw.add(ItemStackCodecs.toCanonical(s));
        actor.setItemInHand(hand, ItemTools.with(actor.itemInHand(hand), CHARGED, raw));
        return true;
    }

    /** CrossbowItem.performShooting: 장전을 비우고, 장전돼 있던 것을 쏜 뒤 사용 통계. */
    static void performShooting(ChunkLevel level, BlockActor actor, Player p, int hand, float speed, float inaccuracy) {
        ItemStack weapon = actor.itemInHand(hand);
        List<ItemStack> list = chargedProjectiles(weapon);
        if (list.isEmpty()) return;
        ItemStack loaded = weapon;
        weapon = ItemTools.with(weapon, CHARGED, List.of());
        actor.setItemInHand(hand, weapon);
        if (shoot(level, actor, p, hand, weapon, list, speed, inaccuracy, true) == 0) {   // 플러그인이 발사를 모두 취소: 장전된 채로
            actor.setItemInHand(hand, loaded);
            org.domi.furnace.server.plugin.ClientResync.inventory(p);
            return;
        }
        ItemStack after = actor.itemInHand(hand);   // 바닐라는 같은 스택 객체(쏘면서 깎인 뒤) — 부서졌으면 쏘기 전 스택
        var shotItem = org.domi.furnace.server.ServerCore.lootStackOf(after.isEmpty() ? weapon : after);
        p.triggerAdvancement(a -> a.triggerItem("minecraft:shot_crossbow", shotItem));
        p.awardStat("minecraft:used:minecraft:crossbow", 1);
    }

    // ------------------------------------------------------------------ ProjectileWeaponItem.shoot

    /**
     * shoot: 퍼짐 각을 개수로 나눠 가운데·왼쪽·오른쪽 순으로(0, −d, +d, …) 발사체를 만들고 쏜 뒤 월드에 넣고, 발마다 무기 내구도
     * (폭죽 3, 화살 1). 무기가 부서지면 멈춘다.
     */
    static int shoot(ChunkLevel level, BlockActor actor, Player p, int hand, ItemStack weapon, List<ItemStack> list, float speed,
            float inaccuracy, boolean crit) {
        int launched = 0;
        float spread = projectileSpread(weapon);
        float step = list.size() == 1 ? 0.0F : 2.0F * spread / (list.size() - 1);
        float offset = (list.size() - 1) % 2 * step / 2.0F;
        float sign = 1.0F;
        boolean crossbow = isCrossbow(weapon);
        for (int i = 0; i < list.size(); i++) {
            ItemStack ammo = list.get(i);
            if (ammo.isEmpty()) continue;
            float angle = offset + sign * ((i + 1) / 2) * step;
            sign = -sign;
            Projectile proj = createProjectile(level, p, weapon, ammo, crit, crossbow);
            if (crossbow) crossbowShootProjectile(level, p, proj, i, speed, inaccuracy, angle);
            else proj.shootFromRotation(p.pitch(), p.yaw() + angle, 0.0F, speed, inaccuracy, p.knownMovement(), p.onGround());
            if (!level.addFreshEntity(proj)) continue;   // 플러그인이 발사를 취소: 이 발은 내구도도 쓰지 않는다
            launched++;
            BlockBreaking.hurtAndBreak(level, actor, hand, actor.itemInHand(hand), crossbow && isFirework(ammo) ? 3 : 1);
            if (actor.itemInHand(hand).isEmpty()) break;
        }
        return launched;
    }

    /** createProjectile: 쇠뇌의 폭죽은 로켓(눈 − 0.15, 비스듬히), 나머지는 ArrowItem.createArrow(+ 치명타, 쇠뇌면 맞는 소리 바꿈). */
    private static Projectile createProjectile(ChunkLevel level, Player p, ItemStack weapon, ItemStack ammo, boolean crit, boolean crossbow) {
        if (crossbow && isFirework(ammo)) {
            var rocket = new FireworkRocketEntity(level, level.nextEntityId(), p.x(), p.eyeY() - 0.15F, p.z(), ammo);
            rocket.setOwner(p);
            rocket.setShotAtAngle(true);
            return rocket;
        }
        AbstractArrow arrow = createArrow(level, p, ammo, weapon);
        if (crit) arrow.setCritArrow(true);
        if (crossbow) arrow.setSoundEvent("item.crossbow.hit");
        return arrow;
    }

    /** ArrowItem/TippedArrowItem/SpectralArrowItem.createArrow: 눈 − 0.1 에서, 줍는 아이템은 탄약 1개. 화살이 아닌 것은 일반 화살로. */
    static AbstractArrow createArrow(ChunkLevel level, Player p, ItemStack ammo, ItemStack weapon) {
        String n = ItemTools.itemName(ammo);
        AbstractArrow arrow;
        if (n.equals("minecraft:spectral_arrow")) {
            arrow = new SpectralArrow(level, level.nextEntityId());
        } else {
            Arrow a = new Arrow(level, level.nextEntityId());
            if (n.equals("minecraft:tipped_arrow")) a.setPotionFromItem(ammo);
            arrow = a;
        }
        arrow.setPos(p.x(), p.eyeY() - 0.1F, p.z());
        arrow.initFromItem(p, ammo.withCount(1), weapon);
        return arrow;
    }

    /** CrossbowItem.shootProjectile(대상 없음): 시선 벡터를 위쪽 벡터 축으로 각도만큼 돌려 shoot, 쏘는 소리(발 순서별 음높이). */
    private static void crossbowShootProjectile(ChunkLevel level, Player p, Projectile proj, int index, float speed, float inaccuracy, float angle) {
        float[] up = viewVector(p.pitch() - 90.0F, p.yaw());
        float[] view = viewVector(p.pitch(), p.yaw());
        float[] d = rotate(view, angle * (float) (Math.PI / 180.0), up);
        proj.shoot(d[0], d[1], d[2], speed, inaccuracy);
        float pitch = shotPitch(p.random(), index);
        level.playSound(null, p.x(), p.y(), p.z(), "item.crossbow.shoot", "players", 1.0F, pitch);
    }

    /** CrossbowItem.getShotPitch: 첫 발 1.0, 홀수 번째 1/(r×0.5+1.8)+0.63, 짝수 번째 +0.43. */
    static float shotPitch(org.domi.furnace.server.block.LegacyRandomSource r, int index) {
        if (index == 0) return 1.0F;
        float base = (index & 1) == 1 ? 0.63F : 0.43F;
        return 1.0F / (r.nextFloat() * 0.5F + 1.8F) + base;
    }

    /** Entity.calculateViewVector(xRot, yRot) — float 성분. */
    static float[] viewVector(float xRot, float yRot) {
        float f = xRot * (float) (Math.PI / 180.0), g = -yRot * (float) (Math.PI / 180.0);
        float h = Mth.cos(g), i = Mth.sin(g), j = Mth.cos(f), k = Mth.sin(f);
        return new float[] {i * j, -k, h * j};
    }

    /** JOML: new Quaternionf().setAngleAxis(angle, ax, ay, az)(double 판) 후 Vector3f.rotate(q). */
    static float[] rotate(float[] v, float angle, float[] axis) {
        double half = angle * 0.5;
        double s = Math.sin(half);
        float qx = (float) (axis[0] * s), qy = (float) (axis[1] * s), qz = (float) (axis[2] * s);
        float qw = (float) cosFromSin(s, half);
        float xx = qx * qx, yy = qy * qy, zz = qz * qz, ww = qw * qw;
        float xy = qx * qy, xz = qx * qz, yz = qy * qz, xw = qx * qw, zw = qz * qw, yw = qy * qw;
        float k = 1 / (xx + yy + zz + ww);
        float x = v[0], y = v[1], z = v[2];
        return new float[] {
            Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy - zw) * k, y, (2 * (xz + yw) * k) * z)),
            Math.fma(2 * (xy + zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz - xw) * k) * z)),
            Math.fma(2 * (xz - yw) * k, x, Math.fma(2 * (yz + xw) * k, y, ((zz - xx - yy + ww) * k) * z))};
    }

    /** JOML Math.cosFromSin(double, double)(빠른 수학 꺼짐). */
    private static double cosFromSin(double sin, double angle) {
        double cos = Math.sqrt(1.0 - sin * sin);
        double a = angle + Math.PI * 0.5;
        double b = a - (int) (a / (Math.PI * 2)) * (Math.PI * 2);
        if (b < 0.0) b = Math.PI * 2 + b;
        if (b >= Math.PI) return -cos;
        return cos;
    }
}
