// 전투 진입/종료(바닐라 CombatTracker.recordDamage / recheckStatus) — 첫 생물 피해에서 진입, 마지막 피해 300틱 뒤(또는 죽으면) 종료.
package org.domi.furnace.server.entity.damage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatTrackerCombatTest {

    private final List<String> events = new ArrayList<>();

    private CombatTracker tracker() {
        CombatTracker c = new CombatTracker();
        c.hooks(new CombatTracker.Hooks() {
            public void enterCombat() { events.add("enter"); }
            public void leaveCombat(int d) { events.add("leave" + d); }
        });
        return c;
    }

    @Test
    void enterOnlyForLivingCause() {
        CombatTracker c = tracker();
        c.recordDamage(DamageType.GENERIC, 1f, null, 0f, 10, true, null, null, false);
        assertTrue(events.isEmpty());
        c.recordDamage(DamageType.GENERIC, 1f, null, 0f, 11, true, null, null, true);
        assertEquals(List.of("enter"), events);
        c.recordDamage(DamageType.GENERIC, 1f, null, 0f, 12, true, null, null, true);
        assertEquals(List.of("enter"), events);   // 이미 전투 중
    }

    @Test
    void leaveAfter300TicksWithDuration() {
        CombatTracker c = tracker();
        c.recordDamage(DamageType.GENERIC, 1f, null, 0f, 100, true, null, null, true);
        c.recheckStatus(400, true);   // 300틱 이내(경계 포함) — 유지
        assertEquals(List.of("enter"), events);
        c.recheckStatus(401, true);
        assertEquals(List.of("enter", "leave301"), events);
        c.recheckStatus(500, true);
        assertEquals(2, events.size());
    }

    @Test
    void leaveOnDeath() {
        CombatTracker c = tracker();
        c.recordDamage(DamageType.GENERIC, 1f, null, 0f, 5, true, null, null, true);
        c.recheckStatus(8, false);
        assertEquals(List.of("enter", "leave3"), events);
    }
}
