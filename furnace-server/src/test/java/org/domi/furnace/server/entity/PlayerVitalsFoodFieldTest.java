package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** NMS FoodData 보기가 쓰는 허기 값 하나씩 정하기·소모 더하기(NMS shim 5번 묶음) — 다른 값은 건드리지 않는다. */
class PlayerVitalsFoodFieldTest {

    private static PlayerVitals vitals() {
        return new Player(1, UUID.randomUUID(), "A", new org.domi.furnace.server.ConcurrentEmbeddedChannel()).vitals();
    }

    @Test
    void eachFieldChangesAlone() {
        var v = vitals();
        v.setFoodField(1, 3.5F);
        v.setFoodField(2, 2.0F);
        v.setFoodField(0, 7F);
        assertEquals(7, v.foodLevel());
        assertEquals(3.5F, v.saturation());
        assertEquals(2.0F, v.exhaustion());
        v.setFoodField(0, 99F);
        assertEquals(20, v.foodLevel(), "0..20 으로 자른다");
        v.setFoodField(0, -4F);
        assertEquals(0, v.foodLevel());
        assertEquals(3.5F, v.saturation(), "허기만 바꿨다");
    }

    @Test
    void addExhaustionCapsAtForty() {
        var v = vitals();
        v.addExhaustion(30F);
        v.addExhaustion(30F);
        assertEquals(40F, v.exhaustion());
    }

    @Test
    void unknownFieldIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> vitals().setFoodField(3, 1F));
    }
}
