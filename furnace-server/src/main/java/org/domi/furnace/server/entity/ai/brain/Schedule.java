// 바닐라 Schedule: 하루(24000틱) 중 시각별 활동. 활동마다 시각-값 키프레임 타임라인이 있고 값이 가장 큰 활동을 고른다.
package org.domi.furnace.server.entity.ai.brain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Schedule {
    public static final int WORK_START_TIME = 2000, TOTAL_WORK_TIME = 7000;

    private static final class Timeline {
        private final TreeMap<Integer, Float> keyframes = new TreeMap<>();

        void add(int ticks, float value) { keyframes.put(ticks, value); }

        float getValueAt(int ticks) {
            if (keyframes.isEmpty()) return 0.0F;
            Map.Entry<Integer, Float> e = keyframes.floorEntry(ticks);
            return e != null ? e.getValue() : keyframes.lastEntry().getValue();
        }
    }

    private final Map<Activity, Timeline> timelines = new LinkedHashMap<>();

    public static final Schedule EMPTY = builder().changeActivityAt(0, Activity.IDLE).build();
    public static final Schedule SIMPLE = builder().changeActivityAt(5000, Activity.WORK).changeActivityAt(11000, Activity.REST).build();
    public static final Schedule VILLAGER_BABY = builder().changeActivityAt(10, Activity.IDLE).changeActivityAt(3000, Activity.PLAY).changeActivityAt(6000, Activity.IDLE)
            .changeActivityAt(10000, Activity.PLAY).changeActivityAt(12000, Activity.REST).build();
    public static final Schedule VILLAGER_DEFAULT = builder().changeActivityAt(10, Activity.IDLE).changeActivityAt(2000, Activity.WORK).changeActivityAt(9000, Activity.MEET)
            .changeActivityAt(11000, Activity.IDLE).changeActivityAt(12000, Activity.REST).build();

    public static Builder builder() { return new Builder(new Schedule()); }

    public Activity getActivityAt(int tickOfDay) {
        Activity best = Activity.IDLE;
        float bestValue = Float.NEGATIVE_INFINITY;
        for (Map.Entry<Activity, Timeline> e : timelines.entrySet()) {
            float v = e.getValue().getValueAt(tickOfDay);
            if (v > bestValue) {
                bestValue = v;
                best = e.getKey();
            }
        }
        return best;
    }

    public static final class Builder {
        private final Schedule schedule;
        private final List<int[]> frames = new ArrayList<>();
        private final List<Activity> acts = new ArrayList<>();

        Builder(Schedule s) { schedule = s; }

        public Builder changeActivityAt(int ticks, Activity a) {
            frames.add(new int[] {ticks});
            acts.add(a);
            return this;
        }

        public Schedule build() {
            for (Activity a : acts) schedule.timelines.computeIfAbsent(a, k -> new Timeline());
            for (int i = 0; i < acts.size(); i++) {
                Activity a = acts.get(i);
                int t = frames.get(i)[0];
                for (Map.Entry<Activity, Timeline> e : schedule.timelines.entrySet()) if (e.getKey() != a) e.getValue().add(t, 0.0F);
                schedule.timelines.get(a).add(t, 1.0F);
            }
            return schedule;
        }
    }
}
