// 바닐라 TimerQueue(ewp) + FunctionCallback(ewl) · FunctionTagCallback(ewm) — /schedule 이 쓰는 지연 실행 큐.
// 전역 스레드 전용(명령 본문·전역 틱이 부른다). save() 만 저장 스레드에서도 불릴 수 있어 전체를 synchronized 로 둔다.
// 정렬: (실행 시각, 등록 순번) — 같은 시각이면 먼저 등록한 것이 먼저. 같은 (이름, 시각) 은 두 번 들어가지 않는다.
// 저장: level.dat Data.ScheduledEvents = [{Name, TriggerTime(long, gameTime), Callback:{Type, Name}}], 실행 순서대로.
package org.domi.furnace.server.function;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScheduledEvents {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEvents.class);
    public static final ResourceId TYPE_FUNCTION = ResourceId.parse("minecraft:function");
    public static final ResourceId TYPE_FUNCTION_TAG = ResourceId.parse("minecraft:function_tag");

    /** 바닐라 TimerCallback: 함수 하나 또는 태그의 함수들. */
    public record Callback(boolean tag, ResourceId id) {}

    private record Event(long time, long seq, String name, Callback callback) {}

    private static final Comparator<Event> ORDER = Comparator.comparingLong(Event::time).thenComparingLong(Event::seq);

    private final PriorityQueue<Event> queue = new PriorityQueue<>(ORDER);
    private final Map<String, Map<Long, Event>> byName = new HashMap<>();
    private long seq;

    /**
     * 바닐라 tick: time <= gameTime 인 것을 순서대로 꺼내 실행한다. 이름×시각 표에서는 gameTime 키를 지운다(바닐라 그대로 —
     * 시각이 이미 지난 항목이면 표에 자기 키가 남는데, 같은 (이름, 시각) 을 다시 등록하려 할 때만 영향이 있다).
     */
    public void tick(long gameTime, Consumer<Callback> run) { tick(gameTime, () -> true, run); }

    /** ready 가 거짓이면(명령 큐가 닫힌 종료 중) 꺼내지 않고 멈춘다 — 실행 못 한 이벤트가 유실되지 않고 저장된다. */
    public void tick(long gameTime, java.util.function.BooleanSupplier ready, Consumer<Callback> run) {
        while (true) {
            Event e;
            synchronized (this) {
                e = queue.peek();
                if (e == null || e.time() > gameTime || !ready.getAsBoolean()) return;
                queue.remove();
                Map<Long, Event> row = byName.get(e.name());
                if (row != null) {
                    row.remove(gameTime);
                    if (row.isEmpty()) byName.remove(e.name());
                }
            }
            run.accept(e.callback());
        }
    }

    /** 바닐라 schedule: 같은 (이름, 시각) 이 이미 있으면 넣지 않는다. */
    public synchronized void schedule(String name, long time, Callback callback) {
        Map<Long, Event> row = byName.computeIfAbsent(name, k -> new HashMap<>());
        if (row.containsKey(time)) return;
        Event e = new Event(time, ++seq, name, callback);
        row.put(time, e);
        queue.add(e);
    }

    /** 바닐라 remove(name): 그 이름의 이벤트를 모두(시각 무관) 지우고 개수를 돌려준다. */
    public synchronized int remove(String name) {
        Map<Long, Event> row = byName.remove(name);
        if (row == null) return 0;
        for (Event e : row.values()) queue.remove(e);
        return row.size();
    }

    /** 이름 목록(명령 자동완성). */
    public synchronized Set<String> names() { return new TreeSet<>(byName.keySet()); }

    public synchronized int size() { return queue.size(); }

    // ------------------------------------------------------------------ 저장

    public synchronized NbtList save() {
        NbtList out = new NbtList(NbtIo.TAG_COMPOUND);
        List<Event> sorted = new ArrayList<>(queue);
        sorted.sort(ORDER);
        for (Event e : sorted) {
            NbtCompound t = new NbtCompound();
            t.putString("Name", e.name());
            t.putLong("TriggerTime", e.time());
            NbtCompound cb = new NbtCompound();
            cb.putString("Type", (e.callback().tag() ? TYPE_FUNCTION_TAG : TYPE_FUNCTION).toString());
            cb.putString("Name", e.callback().id().toString());
            t.putCompound("Callback", cb);
            out.add(t);
        }
        return out;
    }

    /** 바닐라 생성자: 형식이 틀리거나 모르는 Type 인 항목은 건너뛴다(바닐라 TimerCallbacks.deserialize 가 null → 무시). */
    public synchronized void load(NbtList list) {
        queue.clear();
        byName.clear();
        seq = 0;
        if (list == null) return;
        for (Object o : list.items()) {
            if (!(o instanceof NbtCompound t)) {
                log.warn("Invalid format of events: {}", o);
                continue;
            }
            if (!(t.tags().get("Callback") instanceof NbtCompound cb)) continue;
            ResourceId type = cb.tags().get("Type") instanceof String s ? ResourceId.tryParse(s) : null;
            boolean tag;
            if (TYPE_FUNCTION.equals(type)) tag = false;
            else if (TYPE_FUNCTION_TAG.equals(type)) tag = true;
            else continue;
            ResourceId id = cb.tags().get("Name") instanceof String s ? ResourceId.tryParse(s) : null;
            if (id == null) continue;
            String name = t.tags().get("Name") instanceof String s ? s : "";
            long time = t.tags().get("TriggerTime") instanceof Number n ? n.longValue() : 0L;
            schedule(name, time, new Callback(tag, id));
        }
    }
}
