// 엔티티(플레이어 포함)에 붙은 플러그인 작업 대기열 — 플러그인 EntityScheduler 의 서버 쪽. 엔티티 객체에 붙어 있으므로 엔티티가
// 다른 region 으로 넘어가면 대기열도 함께 넘어가고, 그 엔티티를 가진 region 스레드가 틱마다 tick() 을 부른다.
// 넣기·취소·retire 는 아무 스레드에서(이 객체의 모니터로 보호), 작업 실행은 tick() 을 부른 소유 스레드에서 모니터 밖에서 한다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class EntityTasks {

    /** 대기 작업이 하나라도 있는 대기열 수 — 0 이면 레벨이 엔티티를 훑지 않는다(플러그인이 안 쓸 때 비용 0). */
    private static final AtomicInteger PENDING_QUEUES = new AtomicInteger();

    public static boolean anyPending() { return PENDING_QUEUES.get() > 0; }

    /** 작업 하나. 반복 작업은 실행이 끝난 뒤 period 로 다시 센다. */
    public static final class Entry {
        final Runnable task, retired;
        final long period;
        long countdown;
        volatile boolean cancelled;

        Entry(Runnable task, Runnable retired, long delay, long period) {
            this.task = task;
            this.retired = retired;
            this.countdown = Math.max(1, delay);
            this.period = period;
        }

        public void cancel() { cancelled = true; }

        public boolean isCancelled() { return cancelled; }
    }

    private final ArrayList<Entry> pending = new ArrayList<>();
    private boolean retired;
    private boolean counted;
    /** 이 대기열의 엔티티(Entity 또는 Player). retire 하면 null — 플러그인이 핸들을 쥐고 있어도 지운 엔티티를 붙잡지 않는다. */
    private volatile Object owner;

    public EntityTasks(Object owner) { this.owner = owner; }

    /** 이 대기열의 엔티티 — 작업 안(소유 스레드)에서 쓴다. retire 뒤면 null. */
    public Object owner() { return owner; }

    /** delay 틱 뒤(0·1 은 다음 틱) task. period > 0 이면 반복. 이미 retire 됐으면 null. */
    public synchronized Entry schedule(Runnable task, Runnable retiredCallback, long delay, long period) {
        if (task == null) throw new NullPointerException("task");
        if (retired) return null;
        Entry e = new Entry(task, retiredCallback, delay, period);
        add(e);
        return e;
    }

    private void add(Entry e) {
        pending.add(e);
        if (!counted) {
            counted = true;
            PENDING_QUEUES.incrementAndGet();
        }
    }

    public synchronized boolean isRetired() { return retired; }

    public synchronized int pendingCount() { return pending.size(); }

    /** 소유 region 스레드가 그 엔티티의 틱마다 부른다: 때가 된 작업을 돌린다. */
    public void tick() {
        List<Entry> due = null;
        synchronized (this) {
            if (retired || pending.isEmpty()) return;
            for (int i = pending.size() - 1; i >= 0; i--) {
                Entry e = pending.get(i);
                if (e.cancelled) {
                    pending.remove(i);
                } else if (--e.countdown <= 0) {
                    pending.remove(i);
                    if (due == null) due = new ArrayList<>();
                    due.add(e);
                }
            }
            uncountIfEmpty();
        }
        if (due == null) return;
        for (int i = due.size() - 1; i >= 0; i--) {   // 넣은 순서대로
            Entry e = due.get(i);
            if (e.cancelled) continue;
            try {
                e.task.run();
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger(EntityTasks.class).error("[PLUGIN] 엔티티 작업 실패", t);
            }
            if (e.period > 0 && !e.cancelled) {
                boolean again;
                synchronized (this) {
                    again = !retired;
                    if (again) {
                        e.countdown = e.period;
                        add(e);
                    }
                }
                if (!again) runRetired(e);   // 작업 중에 엔티티가 사라졌다
            }
        }
    }

    private void uncountIfEmpty() {
        if (counted && pending.isEmpty()) {
            counted = false;
            PENDING_QUEUES.decrementAndGet();
        }
    }

    /** 엔티티가 사라졌다 — 더 받지 않고, 돌지 않은(취소되지 않은) 작업마다 retired 를 한 번 부른다. 부른 스레드에서. 두 번째부터는 아무것도 안 한다. */
    public void retire() {
        List<Entry> left;
        synchronized (this) {
            if (retired) return;
            retired = true;
            owner = null;
            left = new ArrayList<>(pending);
            pending.clear();
            uncountIfEmpty();
        }
        for (Entry e : left) if (!e.cancelled) runRetired(e);
    }

    private static void runRetired(Entry e) {
        if (e.retired == null) return;
        try {
            e.retired.run();
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger(EntityTasks.class).error("[PLUGIN] 엔티티 작업 retired 콜백 실패", t);
        }
    }
}
