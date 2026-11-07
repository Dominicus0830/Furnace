// 위치가 바뀔 때 도는 인챈트 효과(EnchantmentHelper.runLocationChangedEffects → Enchantment.runLocationChangedEffects): 영혼 가속·서리 걸음.
// 데이터: data/minecraft/enchantment/{soul_speed,frost_walker}.json 의 effects.minecraft:location_changed (슬롯 feet).
//  - 영혼 가속: ① movement_speed +0.0405F+0.0105F×(L-1) (add_value, 영혼 흙 위이거나 활성 중 공중, 탈것·비행 아님) ② movement_efficiency +1.0
//    (영혼 블록 위) ③ 확률 0.04F 로 지면+영혼 블록일 때 장비 내구 1. 속성 수정자는 활성화될 때 달리고 비활성화되면 빠진다.
//  - 서리 걸음: 지면에 있으면 발 아래(offset -1) 반경 clamped(linear(3,+1),0,16) 원판의 물 원천 중 위가 공기이고 엔티티가 막지 않는 칸을 frosted_ice(age 0)로.
// 호출 시점: 블록 칸이 바뀌었을 때(LivingEntity.baseTick), 그리고 발 장비 아이템이 바뀌었을 때(detectEquipmentUpdates — 먼저 비활성화).
// 소유권: 플레이어를 가진 region 스레드에서만 부른다.
// 영혼 가속의 tick 효과(effects.minecraft:tick, LivingEntity.baseTick 머리의 EnchantmentHelper.tickEffects)는 tickEffects 가 한다.
// 차이(미구현): 서리 걸음의 burn_from_stepping 피해 면역(플레이어의 마그마 블록 피해가
//   아직 없다). 영혼 블록 판정은 호출자가 넘긴다(Entity.getBlockPosBelowThatAffectsMyMovement = getOnPos(0.500001) ∈ #soul_speed_blocks).
package org.domi.furnace.server.entity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.item.ItemTools;

public final class PlayerLocationEffects {
    private PlayerLocationEffects() {}

    private static final String SOUL_SPEED_ID = "minecraft:enchantment.soul_speed/feet";

    /** 플레이어별 상태(바닐라 lastPos, activeLocationDependentEnchantments, 이 효과들이 단 수정자). */
    public static final class State {
        private Pos lastPos;
        private ItemStack lastBoots = ItemStack.EMPTY;
        private boolean speedActive, efficiencyActive;
        private int version;
        private final Map<Attribute, AttributeModifier> located = new LinkedHashMap<>();

        /** 수정자 집합이 바뀔 때마다 오른다(Player.syncAttributes 가 맵을 다시 맞출 신호). */
        public int version() { return version; }

        public Collection<AttributeModifier> modifiers(Attribute attr) {
            AttributeModifier m = located.get(attr);
            return m == null ? List.of() : List.of(m);
        }

        public boolean soulSpeedActive() { return speedActive; }

        public boolean soulEfficiencyActive() { return efficiencyActive; }

        private void put(Attribute a, AttributeModifier m) { located.put(a, m); version++; }

        private void remove(Attribute a) { if (located.remove(a) != null) version++; }
    }

    /** 새 플레이어(리스폰)처럼 처음부터 — 마지막 위치를 잊고 활성 효과를 모두 끈다. */
    public static void reset(Player p) {
        State st = p.locationEffects;
        st.lastPos = null;
        st.lastBoots = ItemStack.EMPTY;
        st.speedActive = false;
        st.efficiencyActive = false;
        if (!st.located.isEmpty()) { st.located.clear(); st.version++; }
    }

    /**
     * 한 틱: 블록 칸이 바뀌었으면 효과를 돌리고(baseTick), 발 장비가 바뀌었으면 비활성화한 뒤 다시 돌린다(detectEquipmentUpdates).
     * onSoulBlock 은 필요할 때만 평가한다.
     */
    public static void tick(Player p, ChunkLevel level, BooleanSupplier onSoulBlock) {
        State st = p.locationEffects;
        if (level == null || !p.isAlive()) return;
        Pos cur = new Pos((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
        if (!cur.equals(st.lastPos)) {
            st.lastPos = cur;
            run(p, level, onSoulBlock);
        }
        ItemStack boots = p.equipment(EquipmentSlot.FEET);
        if (!boots.equals(st.lastBoots) && !(boots.isEmpty() && st.lastBoots.isEmpty())) {
            st.lastBoots = boots;
            deactivate(st);
            run(p, level, onSoulBlock);
        }
    }

    /** (double) 1.0E-5F 의 제곱 — movement.horizontal_speed 의 min(항상 제곱으로 비교한다). */
    private static final double MIN_HORIZONTAL_SPEED_SQR = (double) 1.0E-5F * (double) 1.0E-5F;

    /**
     * 영혼 가속 tick 효과(data/minecraft/enchantment/soul_speed.json effects.minecraft:tick 두 개). 발 장비에 영혼 가속이 있으면 매 틱 평가한다.
     * 공통 조건: 비행 아님·지면·known movement 의 수평 속도(×20) ≥ 1.0E-5·발밑이 #soul_speed_blocks·tickCount % 5 == 0.
     *  ① spawn_particles minecraft:soul — 위치 x,z = 엔티티 + (nextDouble-0.5)×너비(엔티티 난수, x 먼저 z 나중), y = 엔티티 + 0.1,
     *     count 0(속도 입자): 속도 x,z = known movement × -0.2, y = 0.1, 속도 상수 1.0. 보낸 사람은 레벨 전체(32 칸 안).
     *  ② play_sound particle.soul_escape — 먼저 레벨 난수 nextFloat < 0.35(조건이 모든 틱 굴려진다), 소리 크기 0.6, 높이 = 엔티티 난수 nextFloat×0.4+0.6.
     *     조용한 엔티티(isSilent)는 소리 없음. 분류 player, 위치 엔티티 발.
     */
    public static void tickEffects(Player p, ChunkLevel level, BooleanSupplier onSoulBlock) {
        if (level == null || !p.isAlive()) return;
        if (ItemTools.enchantmentLevel(p.equipment(EquipmentSlot.FEET), "minecraft:soul_speed") <= 0) return;
        // random_chance 는 all_of 의 첫 항이라 나머지 조건이 거짓이어도 매 틱 레벨 난수를 쓴다
        boolean chance = level.random().nextFloat() < 0.35F;
        boolean met = false;
        if (p.combatTicks() % 5 == 0 && p.onGround() && !(p.isFallFlying() || p.vitals().isFlying())) {
            double[] mv = p.knownMovement();
            double sx = mv[0] * 20.0, sz = mv[2] * 20.0;
            met = sx * sx + sz * sz >= MIN_HORIZONTAL_SPEED_SQR && onSoulBlock.getAsBoolean();
        }
        if (!met) return;
        double[] mv = p.knownMovement();
        var rnd = p.random();
        double w = p.bbWidth();
        double px = p.x() + (rnd.nextDouble() - 0.5) * w;
        double pz = p.z() + (rnd.nextDouble() - 0.5) * w;
        level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("minecraft:soul"), px, p.y() + 0.1, pz,
                0, mv[0] * -0.2, 0.1, mv[2] * -0.2, 1.0);
        if (chance) {
            float pitch = rnd.nextFloat() * (1.0F - 0.6F) + 0.6F;
            level.playSound(null, p.x(), p.y(), p.z(), "minecraft:particle.soul_escape", "player", 0.6F, pitch);
        }
    }

    private static void deactivate(State st) {
        if (st.speedActive) { st.speedActive = false; st.remove(Attribute.MOVEMENT_SPEED); }
        if (st.efficiencyActive) { st.efficiencyActive = false; st.remove(Attribute.MOVEMENT_EFFICIENCY); }
    }

    private static void run(Player p, ChunkLevel level, BooleanSupplier onSoulBlock) {
        State st = p.locationEffects;
        ItemStack boots = p.equipment(EquipmentSlot.FEET);
        int soul = ItemTools.enchantmentLevel(boots, "minecraft:soul_speed");
        int frost = ItemTools.enchantmentLevel(boots, "minecraft:frost_walker");
        if (soul > 0) {
            boolean onSoul = onSoulBlock.getAsBoolean();
            boolean flying = p.isFallFlying() || p.vitals().isFlying();
            boolean ground = p.onGround();
            // 효과 ①: 탈것 아님 && ((활성 && 비행 아님 && (영혼 블록 || 공중)) || (비활성 && 비행 아님 && 영혼 블록))
            boolean c1 = p.vehicle() == null && (st.speedActive ? !flying && (onSoul || !ground) : !flying && onSoul);
            if (c1) {
                if (!st.speedActive) {
                    st.speedActive = true;
                    st.put(Attribute.MOVEMENT_SPEED, new AttributeModifier(SOUL_SPEED_ID, EnchantAttributes.linear(0.0405F, 0.0105F, soul), AttributeModifier.ADD_VALUE));
                }
            } else if (st.speedActive) {
                st.speedActive = false;
                st.remove(Attribute.MOVEMENT_SPEED);
            }
            // 효과 ②: 영혼 블록 위면 이동 효율 +1.0
            if (onSoul) {
                if (!st.efficiencyActive) {
                    st.efficiencyActive = true;
                    st.put(Attribute.MOVEMENT_EFFICIENCY, new AttributeModifier(SOUL_SPEED_ID, 1.0F, AttributeModifier.ADD_VALUE));
                }
            } else if (st.efficiencyActive) {
                st.efficiencyActive = false;
                st.remove(Attribute.MOVEMENT_EFFICIENCY);
            }
            // 효과 ③: all_of[random_chance 0.04F, 지면, 영혼 블록] — 난수는 조건 순서상 항상 먼저 굴린다(레벨 난수)
            if (level.random().nextFloat() < 0.04F && ground && onSoul) EnchantEffects.damageEquipment(level, p, EquipmentSlot.FEET, boots, 1);
        } else {
            deactivate(st);
        }
        if (frost > 0 && p.onGround()) frostWalker(level, p, frost);
    }

    /** replace_disk: 반경 r = (int) clamp(linear(3,+1),0,16), 높이 1, 오프셋 (0,-1,0). x 가 안쪽, z 가 바깥 순서(BlockPos.betweenClosed). */
    static void frostWalker(ChunkLevel level, Player p, int lvl) {
        int r = (int) Math.clamp(EnchantAttributes.linear(3.0F, 1.0F, lvl), 0.0F, 16.0F);
        int ox = (int) Math.floor(p.x()), oy = (int) Math.floor(p.y()) - 1, oz = (int) Math.floor(p.z());
        int frosted = B.defaultState("minecraft:frosted_ice");
        for (int z = oz - r; z <= oz + r; z++) {
            for (int x = ox - r; x <= ox + r; x++) {
                Pos pos = new Pos(x, oy, z);
                double dx = pos.x() + 0.5 - p.x(), dz = pos.z() + 0.5 - p.z();   // distToCenterSqr(x, y+0.5, z): y 항은 0
                if (!(dx * dx + dz * dz < (double) (r * r))) continue;
                if (!B.isTag(level.getBlockState(pos.above()), "minecraft:air")) continue;
                if (!B.is(level.getBlockState(pos), "minecraft:water")) continue;
                if (!Fluids.isWaterSource(level.getFluidState(pos))) continue;
                if (!level.isUnobstructed(Shape.BLOCK.move(x, oy, z))) continue;
                if (level.setBlockAndUpdate(pos, frosted)) level.gameEventFrom("minecraft:block_place", pos, p, frosted);
            }
        }
    }
}
