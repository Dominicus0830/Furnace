// 속성 값 계산 순서: 바닐라 AttributeInstance 는 연산마다 Object2ObjectOpenHashMap 순서(해시 순서)로 차례대로 더하고 곱한다.
// 기대 순서는 실제 fastutil 8.5.12 로 같은 ID 를 넣어 본 순서를 박아 둔 것이다(O05 C2).
package org.domi.furnace.server.attribute;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttributeVanillaOrderTest {

    private static List<String> order(ModifierOrder o) {
        List<String> out = new ArrayList<>();
        o.forEachId(out::add);
        return out;
    }

    private static ModifierOrder put(String... ids) {
        ModifierOrder o = ModifierOrder.EMPTY;
        for (String id : ids) o = o.put(id);
        return o;
    }

    @Test
    void iterationOrderIsFastutilHashOrderNotInsertionOrder() {
        ModifierOrder six = put("test:a", "test:b", "test:c", "test:d", "test:e", "test:f");
        assertEquals(List.of("test:b", "test:d", "test:f", "test:a", "test:c", "test:e"), order(six));
        // 빼고 다시 넣어도 자리는 해시가 정한다(끝으로 가지 않는다)
        assertEquals(order(six), order(six.remove("test:b").put("test:b")));
    }

    @Test
    void growAndShrinkFollowFastutil() {
        String[] ids = new String[40];
        for (int i = 0; i < 40; i++) ids[i] = "test:m" + i;
        ModifierOrder forty = put(ids);
        assertEquals(List.of("test:m39", "test:m4", "test:m35", "test:m0", "test:m27", "test:m19", "test:m34", "test:m31", "test:m30", "test:m11",
                "test:m10", "test:m15", "test:m14", "test:m1", "test:m5", "test:m16", "test:m3", "test:m18", "test:m9", "test:m7", "test:m8",
                "test:m2", "test:m6", "test:m29", "test:m17", "test:m33", "test:m32", "test:m37", "test:m36", "test:m12", "test:m13", "test:m38",
                "test:m28", "test:m20", "test:m21", "test:m22", "test:m25", "test:m26", "test:m24", "test:m23"), order(forty));
        ModifierOrder o = forty;
        for (int i = 0; i < 33; i++) o = o.remove("test:m" + i);
        assertEquals(List.of("test:m33", "test:m39", "test:m34", "test:m35", "test:m37", "test:m36", "test:m38"), order(o));
    }

    @Test
    void namespaceDefaultsToMinecraft() {
        assertEquals(ModifierOrder.idHash("minecraft:x"), ModifierOrder.idHash("x"));
    }

    private static AttributeInstance instance() {
        return new AttributeMap().add(Attribute.MAX_HEALTH, 1.1).get(Attribute.MAX_HEALTH);
    }

    @Test
    void addValueSumsOneByOneInVanillaMapOrder() {
        var i = instance();
        double[] amt = {0.1, 0.7, 0.3, 0.2, 0.9, 0.4};   // a..f, 넣은 순서
        for (int k = 0; k < 6; k++) i.addOrReplaceModifier(new AttributeModifier("test:" + (char) ('a' + k), amt[k], AttributeModifier.ADD_VALUE));
        // 바닐라 순서 b d f a c e
        double expected = 1.1;
        for (char c : "bdfaec".toCharArray()) expected += amt[c - 'a'];
        assertEquals(expected, i.value(), 0.0);
        assertEquals(3.6999999999999997, i.value(), 0.0);   // 넣은 순서로 더한 값은 3.7 — 마지막 비트가 다르다
    }

    @Test
    void baseMultipliersUseTheSumOfAddsAsFactorOneByOne() {
        var i = instance();
        double[] amt = {0.1, 0.7, 0.3, 0.2, 0.9, 0.4};
        i.addOrReplaceModifier(new AttributeModifier("test:add", 2.6, AttributeModifier.ADD_VALUE));   // d = 1.1 + 2.6 = 3.7
        for (int k = 0; k < 6; k++) i.addOrReplaceModifier(new AttributeModifier("test:" + (char) ('a' + k), amt[k], AttributeModifier.ADD_MULTIPLIED_BASE));
        double d = 1.1 + 2.6, e = d;
        for (char c : "bdfaec".toCharArray()) e += d * amt[c - 'a'];
        assertEquals(e, i.value(), 0.0);
    }

    @Test
    void totalMultipliersMultiplyOneByOne() {
        var i = instance();
        double[] amt = {0.1, 0.7, 0.3, 0.2, 0.9, 0.4};
        i.addOrReplaceModifier(new AttributeModifier("test:add", 2.6, AttributeModifier.ADD_VALUE));
        for (int k = 0; k < 6; k++) i.addOrReplaceModifier(new AttributeModifier("test:" + (char) ('a' + k), amt[k], AttributeModifier.ADD_MULTIPLIED_TOTAL));
        double e = 1.1 + 2.6;
        for (char c : "bdfaec".toCharArray()) e *= 1.0 + amt[c - 'a'];
        assertEquals(e, i.value(), 0.0);
    }

    @Test
    void removeThenReAddKeepsHashOrderForValue() {
        var i = instance();
        double[] amt = {0.1, 0.7, 0.3, 0.2, 0.9, 0.4};
        for (int k = 0; k < 6; k++) i.addOrReplaceModifier(new AttributeModifier("test:" + (char) ('a' + k), amt[k], AttributeModifier.ADD_VALUE));
        double before = i.value();
        i.removeModifier("test:b");
        i.addOrReplaceModifier(new AttributeModifier("test:b", amt[1], AttributeModifier.ADD_VALUE));
        assertEquals(before, i.value(), 0.0);
    }

    @Test
    void replaceMovesToEndOfModifiersButTransientUpdateKeepsPlace() {
        var i = instance();
        for (String id : new String[] {"test:a", "test:b", "test:c"}) i.addOrReplaceModifier(new AttributeModifier(id, 1, AttributeModifier.ADD_VALUE));
        // 바닐라 addOrReplacePermanentModifier = 지우고 다시 넣기 → modifierById(넣은 순서 배열 맵)에서 끝으로
        i.addOrReplaceModifier(new AttributeModifier("test:a", 2, AttributeModifier.ADD_VALUE));
        assertEquals(List.of("test:b", "test:c", "test:a"), List.copyOf(i.modifiers().keySet()));
        assertEquals(1.1 + 1 + 1 + 2, i.value(), 1e-12);
        // addOrReplaceTransientModifier: 제자리
        i.addOrReplaceTransientModifier(new AttributeModifier("test:b", 5, AttributeModifier.ADD_VALUE));
        assertEquals(List.of("test:b", "test:c", "test:a"), List.copyOf(i.modifiers().keySet()));
        assertEquals(5, i.getModifier("test:b").amount());
        assertEquals(1.1 + 5 + 1 + 2, i.value(), 1e-12);
    }

    @Test
    void transientUpdateWithEqualModifierDoesNotMarkDirty() {
        int[] dirty = {0};
        var i = new AttributeInstance(Attribute.MAX_HEALTH, () -> dirty[0]++);
        var m = new AttributeModifier("test:a", 1, AttributeModifier.ADD_VALUE);
        i.addOrReplaceTransientModifier(m);
        int n = dirty[0];
        i.addOrReplaceTransientModifier(new AttributeModifier("test:a", 1, AttributeModifier.ADD_VALUE));
        assertEquals(n, dirty[0]);
    }

    @Test
    void transientUpdateCanChangeOperation() {
        var i = instance();
        i.addOrReplaceTransientModifier(new AttributeModifier("test:a", 1, AttributeModifier.ADD_VALUE));
        assertEquals(2.1, i.value(), 1e-12);
        i.addOrReplaceTransientModifier(new AttributeModifier("test:a", 1, AttributeModifier.ADD_MULTIPLIED_TOTAL));
        assertEquals(2.2, i.value(), 1e-12);
        i.removeModifier("test:a");
        assertEquals(1.1, i.value(), 0.0);
    }

    @Test
    void transientFlagFollowsTheLastAddAndClearsOnRemove() {
        var i = instance();
        i.addOrReplaceTransientModifier(new AttributeModifier("test:a", 1, AttributeModifier.ADD_VALUE));
        assertTrue(i.isTransient("test:a"));
        i.addOrReplaceModifier(new AttributeModifier("test:a", 1, AttributeModifier.ADD_VALUE));
        assertFalse(i.isTransient("test:a"));
        i.addOrReplaceTransientModifier(new AttributeModifier("test:a", 1, AttributeModifier.ADD_VALUE));
        assertTrue(i.isTransient("test:a"), "같은 값이어도 저장 안 함으로 바뀐다");
        i.removeModifier("test:a");
        assertFalse(i.isTransient("test:a"));
        i.addOrReplaceTransientModifier(new AttributeModifier("test:b", 1, AttributeModifier.ADD_VALUE));
        i.removeModifiers();
        assertFalse(i.isTransient("test:b"));
    }
}
