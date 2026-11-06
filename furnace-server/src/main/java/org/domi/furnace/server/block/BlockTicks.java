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

    private record Key(long pos, int type) {}

    /** schedule — 이미 같은 (칸, 종류)가 있으면 무시. */
    public void schedule(Pos pos, int type, long triggerTick, int priority, long subTickOrder) {
        Key k = new Key(pos.asLong(), type);
        if (scheduled.add(k)) queue.add(new Scheduled(k.pos, type, triggerTick, priority, subTickOrder));
    }

    public boolean hasScheduledTick(Pos pos, int type) { return scheduled.contains(new Key(pos.asLong(), type)); }

    public boolean willTickThisTick(Pos pos, int type) { return runThisTick.contains(new Key(pos.asLong(), type)); }

    public int size() { return queue.size(); }

    /** gameTime 이하인 예약을 순서대로 실행(최대 maxAllowed). 실행 중 새로 예약된 것은 다음 틱 이후로. */
    public void tick(long gameTime, int maxAllowed, Ticker ticker) {
        runThisTick.clear();
        java.util.ArrayList<Scheduled> batch = new java.util.ArrayList<>();
        while (!queue.isEmpty() && queue.peek().triggerTick <= gameTime && batch.size() < maxAllowed) {
            Scheduled s = queue.poll();
            Key k = new Key(s.pos, s.type);
            scheduled.remove(k);
            runThisTick.add(k);
            batch.add(s);
        }
        for (Scheduled s : batch) {
            runThisTick.remove(new Key(s.pos, s.type));   // willTickThisTick 은 아직 돌지 않은 것만(바닐라 toRunThisTickSet)
            ticker.tick(Pos.of(s.pos), s.type);
        }
        runThisTick.clear();
    }

    /**
     * 조건에 맞는 칸의 예약을 target 으로 옮긴다(region 합치기·나누기). 실행 시각은 timeOffset 만큼 민다(두 region 의 지역 시계 차이).
     * 순서(같은 시각·우선순위 안의 예약 순번)는 그대로 유지된다.
     */
    public void moveTo(BlockTicks target, java.util.function.LongPredicate posFilter, long timeOffset) {
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
        return out;
    }

    /**
     * 바닐라 LevelTicks.copyAreaFrom: src 에서 상자(포함) 안 칸의 예약을 (dx,dy,dz) 만큼 옮겨 이 쪽에 예약한다. 실행 시각·우선순위는 그대로,
     * 순번은 (원래 순번 - 모은 것 중 최소 + 최대 + 1) 이다(바닐라 식 그대로). 먼저 다 모은 뒤 넣으므로 src 가 this 여도 된다.
     */
    public void copyAreaFrom(BlockTicks src, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int dx, int dy, int dz) {
        java.util.List<Scheduled> list = new java.util.ArrayList<>();
        for (Scheduled s : src.queue) {
            Pos p = Pos.of(s.pos);
            if (p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ) list.add(s);
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
