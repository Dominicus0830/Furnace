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
        for (Scheduled s : batch) ticker.tick(Pos.of(s.pos), s.type);
        runThisTick.clear();
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
}
