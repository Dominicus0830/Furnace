// 예약 틱(바닐라 LevelTicks): 같은 (칸, 종류)는 하나만, 실행 순서는 (실행 시각, 우선순위, 예약 순번). 한 틱 최대 65536개.
// 종류는 블록 번호(블록 틱) 또는 유체 종류(유체 틱) — 서로 다른 저장소를 쓴다.
package org.domi.furnace.server.block;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

public final class BlockTicks {

    /** 예약 틱 하나. */
    public record Scheduled(long pos, int type, long triggerTick, int priority, long subTickOrder) {}

    public interface Ticker {
        void tick(Pos pos, int type);
    }

    private final PriorityQueue<Scheduled> queue = new PriorityQueue<>((a, b) -> {
        int c = Long.compare(a.triggerTick, b.triggerTick);
        if (c != 0) return c;
        c = Integer.compare(a.priority, b.priority);
        return c != 0 ? c : Long.compare(a.subTickOrder, b.subTickOrder);
    });
    private final Set<Key> scheduled = new HashSet<>();
    private final Set<Key> runThisTick = new HashSet<>();
    /**
     * 실행 시각이 됐지만 청크가 블록 틱 범위 밖이라 꺼내지 않은 예약(청크 키 → 꺼낸 순서). 바닐라 LevelTicks 는 틱 범위 밖 청크의
     * 컨테이너를 건드리지 않고 남겨 두었다가 범위에 들면 그 틱에 함께 돌린다 — 여기서는 큐에서 빼 두었다가 범위에 들면 그대로 다시 넣는다
     * (실행 시각·우선순위·순번이 그대로라 순서도 같다). 키({@link #scheduled})는 그대로 남아 중복 예약을 막는다.
     */
    private final java.util.Map<Long, java.util.ArrayList<Scheduled>> parked = new java.util.HashMap<>();
    private final ChunkPredicateMemo memo = new ChunkPredicateMemo();

    private record Key(long pos, int type) {}

    /** schedule — 이미 같은 (칸, 종류)가 있으면 무시. */
    public void schedule(Pos pos, int type, long triggerTick, int priority, long subTickOrder) {
        Key k = new Key(pos.asLong(), type);
        if (scheduled.add(k)) queue.add(new Scheduled(k.pos, type, triggerTick, priority, subTickOrder));
    }

    public boolean hasScheduledTick(Pos pos, int type) { return scheduled.contains(new Key(pos.asLong(), type)); }

    public boolean willTickThisTick(Pos pos, int type) { return runThisTick.contains(new Key(pos.asLong(), type)); }

    public int size() {
        int n = queue.size();
        for (var l : parked.values()) n += l.size();
        return n;
    }

    /** gameTime 이하인 예약을 순서대로 실행(최대 maxAllowed). 실행 중 새로 예약된 것은 다음 틱 이후로. */
    public void tick(long gameTime, int maxAllowed, Ticker ticker) { tick(gameTime, maxAllowed, null, ticker); }

    /**
     * 바닐라 LevelTicks.tick(gameTime, max, ticker) — 생성자 술어(ServerLevel.isPositionTickingWithEntitiesLoaded)가 참인 청크의 예약만 꺼낸다.
     * chunkTicking 은 청크 키({@link ChunkPredicateMemo#chunkKey})를 받는다. null 이면 모든 청크.
     */
    public void tick(long gameTime, int maxAllowed, java.util.function.LongPredicate chunkTicking, Ticker ticker) {
        runThisTick.clear();
        java.util.function.LongPredicate ticking = chunkTicking == null ? null : memo.reset(chunkTicking);
        if (!parked.isEmpty()) unpark(ticking);
        // 이번 틱에 꺼낼 수 있는 것(실행 시각 ≤ gameTime, 범위 안)을 큐 순서(시각 → 우선순위 → 순번)로 꺼낸다.
        // 큐가 시각 순이라 지난 예약이 있으면 맨 앞에 온다. 없으면 모두 이번 시각이라 큐 순서 = 바닐라 순서(아래)이므로 한도까지만 꺼낸다.
        java.util.ArrayList<Scheduled> due = new java.util.ArrayList<>();
        boolean overdue = false;
        while (!queue.isEmpty() && queue.peek().triggerTick <= gameTime && (overdue || due.size() < maxAllowed)) {
            Scheduled s = queue.poll();
            if (ticking != null) {
                long ck = ChunkPredicateMemo.chunkKeyOfPos(s.pos);
                if (!ticking.test(ck)) {   // 틱 범위 밖 — 꺼내지 않은 것으로 두고 개수에도 넣지 않는다
                    parked.computeIfAbsent(ck, k -> new java.util.ArrayList<>()).add(s);
                    continue;
                }
            }
            if (due.isEmpty() && s.triggerTick < gameTime) overdue = true;
            due.add(s);
        }
        java.util.List<Scheduled> batch = overdue ? drainByContainer(due, maxAllowed) : due;
        for (Scheduled s : batch) {
            Key k = new Key(s.pos, s.type);
            scheduled.remove(k);
            runThisTick.add(k);
        }
        for (Scheduled s : batch) {
            runThisTick.remove(new Key(s.pos, s.type));   // willTickThisTick 은 아직 돌지 않은 것만(바닐라 toRunThisTickSet)
            ticker.tick(Pos.of(s.pos), s.type);
        }
        runThisTick.clear();
    }

    /** 바닐라 ScheduledTick.INTRA_TICK_DRAIN_ORDER: 우선순위 → 순번(실행 시각은 보지 않는다). */
    private static final java.util.Comparator<Scheduled> INTRA_TICK = (a, b) -> {
        int c = Integer.compare(a.priority, b.priority);
        return c != 0 ? c : Long.compare(a.subTickOrder, b.subTickOrder);
    };

    /**
     * 지난 예약이 섞였을 때의 바닐라 LevelTicks 꺼내기(drainContainers·drainFromCurrentContainer): 청크(컨테이너) 안은 (시각, 우선순위, 순번),
     * 청크 사이는 머리끼리 (우선순위, 순번)으로 고른다. 지금 청크의 다음 것이 다음 청크 머리보다 앞서는 동안 이어서 꺼내고, 한도에 닿으면 멈춘다.
     * 꺼내지 못한 것은 큐로 되돌린다. due 는 큐 순서라 청크별 목록도 (시각, 우선순위, 순번) 순이다.
     */
    private java.util.List<Scheduled> drainByContainer(java.util.List<Scheduled> due, int maxAllowed) {
        java.util.Map<Long, java.util.ArrayDeque<Scheduled>> byChunk = new java.util.HashMap<>();
        for (Scheduled s : due) byChunk.computeIfAbsent(ChunkPredicateMemo.chunkKeyOfPos(s.pos), k -> new java.util.ArrayDeque<>()).add(s);
        PriorityQueue<java.util.ArrayDeque<Scheduled>> containers = new PriorityQueue<>((a, b) -> INTRA_TICK.compare(a.peekFirst(), b.peekFirst()));
        containers.addAll(byChunk.values());
        java.util.List<Scheduled> batch = new java.util.ArrayList<>();
        java.util.ArrayDeque<Scheduled> c;
        while (batch.size() < maxAllowed && (c = containers.poll()) != null) {
            batch.add(c.pollFirst());
            if (batch.size() < maxAllowed) {
                var next = containers.peek();
                Scheduled nextHead = next != null ? next.peekFirst() : null;
                while (batch.size() < maxAllowed) {
                    Scheduled t = c.peekFirst();
                    if (t == null || nextHead != null && INTRA_TICK.compare(t, nextHead) > 0) break;
                    batch.add(c.pollFirst());
                }
            }
            if (!c.isEmpty()) {
                if (batch.size() < maxAllowed) containers.add(c);
                else queue.addAll(c);
            }
        }
        for (var rest : containers) queue.addAll(rest);
        return batch;
    }

    /** 틱 범위에 든 청크(ticking 이 null 이면 모두)의 미뤄 둔 예약을 큐로 되돌린다. */
    private void unpark(java.util.function.LongPredicate ticking) {
        var it = parked.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (ticking == null || ticking.test(e.getKey())) {
                queue.addAll(e.getValue());
                it.remove();
            }
        }
    }

    /**
     * 조건에 맞는 칸의 예약을 target 으로 옮긴다(region 합치기·나누기). 실행 시각은 timeOffset 만큼 민다(두 region 의 지역 시계 차이).
     * 순서(같은 시각·우선순위 안의 예약 순번)는 그대로 유지된다.
     */
    public void moveTo(BlockTicks target, java.util.function.LongPredicate posFilter, long timeOffset) {
        if (!parked.isEmpty()) unpark(null);   // 드문 일(합치기·나누기) — 모두 큐로 되돌린 뒤 옮긴다. 다음 틱에 범위를 다시 본다.
        queue.removeIf(s -> {
            if (!posFilter.test(s.pos)) return false;
            scheduled.remove(new Key(s.pos, s.type));
            Key k = new Key(s.pos, s.type);
            if (target.scheduled.add(k)) {
                target.queue.add(new Scheduled(s.pos, s.type, s.triggerTick + timeOffset, s.priority, s.subTickOrder));
            }
            return true;
        });
    }

    /** 그 청크의 예약(실행 순서대로) — 꺼내지 않는다(저장용). */
    public java.util.List<Scheduled> chunkTicks(int chunkX, int chunkZ) {
        java.util.List<Scheduled> out = new java.util.ArrayList<>();
        for (Scheduled s : queue) {
            Pos p = Pos.of(s.pos);
            if (p.x() >> 4 == chunkX && p.z() >> 4 == chunkZ) out.add(s);
        }
        var held = parked.get(ChunkPredicateMemo.chunkKey(chunkX, chunkZ));
        if (held != null) out.addAll(held);
        out.sort(queue.comparator());
        return out;
    }

    /** 청크를 내릴 때 그 청크의 예약을 꺼낸다(저장용). */
    public java.util.List<Scheduled> removeChunk(int chunkX, int chunkZ) {
        java.util.List<Scheduled> out = new java.util.ArrayList<>();
        queue.removeIf(s -> {
            Pos p = Pos.of(s.pos);
            if (p.x() >> 4 == chunkX && p.z() >> 4 == chunkZ) {
                out.add(s);
                scheduled.remove(new Key(s.pos, s.type));
                return true;
            }
            return false;
        });
        var held = parked.remove(ChunkPredicateMemo.chunkKey(chunkX, chunkZ));
        if (held != null) {
            for (Scheduled s : held) scheduled.remove(new Key(s.pos, s.type));
            out.addAll(held);
            out.sort(queue.comparator());
        }
        return out;
    }

    /**
     * 바닐라 LevelTicks.copyAreaFrom: src 에서 상자(포함) 안 칸의 예약을 (dx,dy,dz) 만큼 옮겨 이 쪽에 예약한다. 실행 시각·우선순위는 그대로,
     * 순번은 (원래 순번 - 모은 것 중 최소 + 최대 + 1) 이다(바닐라 식 그대로). 먼저 다 모은 뒤 넣으므로 src 가 this 여도 된다.
     */
    public void copyAreaFrom(BlockTicks src, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int dx, int dy, int dz) {
        java.util.List<Scheduled> list = new java.util.ArrayList<>();
        java.util.List<java.util.Collection<Scheduled>> sources = new java.util.ArrayList<>();
        sources.add(src.queue);
        sources.addAll(src.parked.values());   // 틱 범위 밖이라 미뤄 둔 것도 예약이다
        for (var from : sources) {
            for (Scheduled s : from) {
                Pos p = Pos.of(s.pos);
                if (p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ) list.add(s);
            }
        }
        if (list.isEmpty()) return;
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (Scheduled s : list) {
            min = Math.min(min, s.subTickOrder);
            max = Math.max(max, s.subTickOrder);
        }
        list.sort(src.queue.comparator());
        for (Scheduled s : list) {
            Pos p = Pos.of(s.pos);
            schedule(new Pos(p.x() + dx, p.y() + dy, p.z() + dz), s.type, s.triggerTick, s.priority, s.subTickOrder - min + max + 1L);
        }
    }
}
