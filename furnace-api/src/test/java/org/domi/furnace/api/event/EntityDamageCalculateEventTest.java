// 피해 계산 단계 이벤트: 기본은 규칙 그대로, 단계를 바꾸면 뒤 단계가 새 input 으로 다시 계산된다.
package org.domi.furnace.api.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;
import org.domi.furnace.api.event.entity.DamageRules;
import org.domi.furnace.api.event.entity.DamageStage;
import org.domi.furnace.api.event.entity.EntityDamageCalculateEvent;
import org.junit.jupiter.api.Test;

class EntityDamageCalculateEventTest {

    /** 단순한 규칙: 방어력 ×0.8, 저항 ×0.9, 인챈트 ×0.5, 엔티티 ×0.15 (적용 집합으로 켜고 끈다). */
    private static DamageRules rules(Set<DamageStage> applies) {
        return new DamageRules() {
            @Override public boolean applies(DamageStage s) { return applies.contains(s); }
            @Override public float apply(DamageStage s, float x) {
                return switch (s) {
                    case ARMOR -> x * 0.8F;
                    case RESISTANCE -> x * 0.9F;
                    case ENCHANTMENT -> x * 0.5F;
                    case ENTITY -> x * 0.15F;
                    case ABSORPTION -> x;
                };
            }
        };
    }

    private static EntityDamageCalculateEvent event(float base, float absorption, Set<DamageStage> applies) {
        return new EntityDamageCalculateEvent(null, "minecraft:generic", null, null, base, absorption, rules(applies));
    }

    private static final Set<DamageStage> ALL = EnumSet.allOf(DamageStage.class);

    @Test
    void defaultsFollowRulesInOrder() {
        var e = event(10f, 0f, ALL);
        float a = 10f * 0.8F, r = a * 0.9F, c = r * 0.5F, en = c * 0.15F;
        assertEquals(10f, e.input(DamageStage.ARMOR), 0f);
        assertEquals(a, e.output(DamageStage.ARMOR), 0f);
        assertEquals(a, e.input(DamageStage.RESISTANCE), 0f);
        assertEquals(r, e.output(DamageStage.RESISTANCE), 0f);
        assertEquals(c, e.output(DamageStage.ENCHANTMENT), 0f);
        assertEquals(en, e.output(DamageStage.ENTITY), 0f);
        assertEquals(en, e.finalDamage(), 0f, "흡수 체력이 없으면 마지막 값이 그대로 체력 피해");
        assertEquals(0f, e.absorbed(), 0f);
        assertFalse(e.isModified(DamageStage.ARMOR));
    }

    @Test
    void notAppliedStagesPassThrough() {
        var e = event(10f, 0f, EnumSet.of(DamageStage.RESISTANCE));
        assertFalse(e.applies(DamageStage.ARMOR));
        assertEquals(10f, e.output(DamageStage.ARMOR), 0f);
        assertEquals(10f * 0.9F, e.finalDamage(), 0f);
        assertEquals(0f, e.reduction(DamageStage.ARMOR), 0f);
    }

    @Test
    void absorptionTakesFirstAndLeavesRest() {
        var e = event(10f, 4f, EnumSet.noneOf(DamageStage.class));
        assertTrue(e.applies(DamageStage.ABSORPTION));
        assertEquals(6f, e.finalDamage(), 0f);
        assertEquals(4f, e.absorbed(), 0f);
        var more = event(3f, 4f, EnumSet.noneOf(DamageStage.class));
        assertEquals(0f, more.finalDamage(), 0f);
        assertEquals(3f, more.absorbed(), 0f, "남는 흡수 체력은 빠지지 않는다");
    }

    @Test
    void setBaseRecomputesEveryStage() {
        var e = event(10f, 0f, ALL);
        e.setBase(20f);
        assertEquals(20f * 0.8F, e.output(DamageStage.ARMOR), 0f);
        assertEquals(20f * 0.8F * 0.9F * 0.5F * 0.15F, e.finalDamage(), 0f);
        e.setBase(0f);
        assertEquals(0f, e.finalDamage(), 0f);
        assertThrows(IllegalArgumentException.class, () -> e.setBase(-1f));
        assertThrows(IllegalArgumentException.class, () -> e.setBase(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> e.setBase(Float.POSITIVE_INFINITY));
    }

    @Test
    void setReductionIsAbsoluteAndDownstreamUsesNewInput() {
        var e = event(10f, 0f, EnumSet.of(DamageStage.ARMOR, DamageStage.RESISTANCE));
        e.setReduction(DamageStage.ARMOR, 5f);
        assertEquals(5f, e.output(DamageStage.ARMOR), 0f);
        assertEquals(5f * 0.9F, e.finalDamage(), 0f, "저항은 새 input 5 로 바닐라 식을 다시 계산");
        assertTrue(e.isModified(DamageStage.ARMOR));
        e.setBase(8f);
        assertEquals(3f, e.output(DamageStage.ARMOR), 0f, "절대 양은 base 가 바뀌어도 5 를 줄인다");
        e.setBase(2f);
        assertEquals(0f, e.output(DamageStage.ARMOR), 0f, "input 보다 많이 줄이지 못한다");
        assertEquals(0f, e.finalDamage(), 0f);
    }

    @Test
    void scaleReductionScalesVanillaAmount() {
        var e = event(10f, 0f, EnumSet.of(DamageStage.ARMOR));
        assertEquals(2f, e.vanillaReduction(DamageStage.ARMOR), 1e-6f);
        e.scaleReduction(DamageStage.ARMOR, 0.5f);
        assertEquals(9f, e.finalDamage(), 1e-6f);
        e.scaleReduction(DamageStage.ARMOR, 10f);
        assertEquals(0f, e.finalDamage(), 0f, "곱한 양이 input 을 넘으면 input 에서 멈춘다");
        e.scaleReduction(DamageStage.ARMOR, 1f);
        assertEquals(10f * 0.8F, e.finalDamage(), 0f, "1 배는 바닐라 값 그대로(비트 단위)");
        assertThrows(IllegalArgumentException.class, () -> e.scaleReduction(DamageStage.ARMOR, -0.1f));
        // 적용 안 되는 단계는 곱할 양이 0
        var n = event(10f, 0f, EnumSet.noneOf(DamageStage.class));
        n.scaleReduction(DamageStage.ARMOR, 3f);
        assertEquals(10f, n.finalDamage(), 0f);
    }

    @Test
    void skipAndForceApplication() {
        var e = event(10f, 0f, EnumSet.of(DamageStage.ARMOR));
        e.skip(DamageStage.ARMOR);
        assertFalse(e.applies(DamageStage.ARMOR));
        assertEquals(10f, e.finalDamage(), 0f);
        e.setApplied(DamageStage.ENCHANTMENT, true);   // 바닐라가 건너뛰는 단계를 강제로 켠다
        assertEquals(5f, e.finalDamage(), 0f);
        e.setReduction(DamageStage.ARMOR, 1f);   // 건너뛴 단계에 양을 정하면 다시 켜진다
        assertTrue(e.applies(DamageStage.ARMOR));
        assertEquals(4.5f, e.finalDamage(), 0f);
        e.reset(DamageStage.ARMOR);
        e.reset(DamageStage.ENCHANTMENT);
        assertFalse(e.isModified(DamageStage.ARMOR));
        assertEquals(10f * 0.8F, e.finalDamage(), 0f);
    }

    @Test
    void absorptionOverrideIsCappedByAvailableAbsorption() {
        var e = event(10f, 4f, EnumSet.noneOf(DamageStage.class));
        e.setReduction(DamageStage.ABSORPTION, 0f);
        assertEquals(10f, e.finalDamage(), 0f, "흡수를 무시하면 체력이 전부 깎인다");
        assertEquals(0f, e.absorbed(), 0f);
        e.setReduction(DamageStage.ABSORPTION, 100f);
        assertEquals(6f, e.finalDamage(), 0f, "가진 흡수 체력(4)을 넘게 쓰지 못한다");
        assertEquals(4f, e.absorbed(), 0f);
        e.scaleReduction(DamageStage.ABSORPTION, 0.5f);
        assertEquals(8f, e.finalDamage(), 1e-6f);
    }
}
