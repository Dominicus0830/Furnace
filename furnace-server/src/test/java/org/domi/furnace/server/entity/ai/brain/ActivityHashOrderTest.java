// 바닐라 Activity.hashCode = 이름.hashCode(): 두뇌의 HashMap<Activity>·HashSet<Activity> 순회 순서가 실행마다 같다(play → core → idle → work).
// 식별 해시였을 때는 같은 우선순위의 core·idle 행동 시작 순서와 난수 소비가 실행마다 달랐다.
package org.domi.furnace.server.entity.ai.brain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivityHashOrderTest {

    @Test
    void hashCodeIsTheNameHash() {
        assertEquals("core".hashCode(), Activity.CORE.hashCode());
        assertEquals("pre_raid".hashCode(), Activity.PRE_RAID.hashCode());
        assertEquals(Activity.byKey("minecraft:work"), Activity.WORK);
    }

    @Test
    void hashMapIterationOrderIsFixedLikeVanilla() {
        // 같은 이름을 같은 순서로 넣은 String 맵과 Activity 맵의 순회 순서는 같아야 한다(해시가 이름 해시이므로).
        Activity[] all = {Activity.WORK, Activity.CORE, Activity.IDLE, Activity.PLAY, Activity.REST, Activity.PANIC, Activity.MEET};
        Map<Activity, Integer> byActivity = new HashMap<>();
        Map<String, Integer> byName = new HashMap<>();
        for (Activity a : all) {
            byActivity.put(a, 1);
            byName.put(a.getName(), 1);
        }
        List<String> got = new ArrayList<>();
        for (Activity a : byActivity.keySet()) got.add(a.getName());
        assertEquals(new ArrayList<>(byName.keySet()), got);
        // 바닐라(1.21.1)에서 play·core·idle·work 가 이 순서로 도는 것을 고정한다
        Map<Activity, Integer> four = new HashMap<>();
        for (Activity a : new Activity[] {Activity.WORK, Activity.IDLE, Activity.CORE, Activity.PLAY}) four.put(a, 1);
        List<String> order = new ArrayList<>();
        for (Activity a : four.keySet()) order.add(a.getName());
        assertEquals(List.of("play", "core", "idle", "work"), order);
        // 넣은 순서가 달라도 같다
        Map<Activity, Integer> reversed = new HashMap<>();
        for (Activity a : new Activity[] {Activity.PLAY, Activity.CORE, Activity.IDLE, Activity.WORK}) reversed.put(a, 1);
        assertEquals(order, reversed.keySet().stream().map(Activity::getName).toList());
    }

    @Test
    void pluginActivitiesAreInternedAndHashByKey() {
        Activity a = Activity.byKey("test:custom");
        assertSame(a, Activity.byKey("test:custom"));
        assertEquals("test:custom".hashCode(), a.hashCode());
        Set<Activity> set = new HashSet<>(List.of(Activity.CORE, a));
        assertTrue(set.contains(Activity.byKey("test:custom")));
    }
}
