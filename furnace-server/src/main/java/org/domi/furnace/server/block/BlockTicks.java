// 예약 틱(바닐라 LevelTicks + LevelChunkTicks): 같은 (칸, 종류)는 하나만, 실행 순서는 (실행 시각, 우선순위, 예약 순번). 한 틱 최대 65536개.
// 종류는 블록 번호(블록 틱) 또는 유체 종류(유체 틱) — 서로 다른 저장소를 쓴다.
// 바닐라 모양 그대로 청크마다 컨테이너(그 청크 안은 실행 순서로 정렬된 큐 + (칸, 종류) 집합)를 두고, 꺼내기는 컨테이너 머리끼리 고른다
// (LevelTicks.sortContainersToTick·drainContainers·drainFromCurrentContainer). 청크 저장·내리기는 그 청크의 예약 수에만 비례한다.
// 컨테이너가 있는지(= 청크가 로드돼 있는지)는 여기서 보지 않는다 — ChunkLevel.scheduleTick 이 바닐라처럼 로드 안 된 청크의 예약을 버린다.
package org.domi.furnace.server.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.LongPredicate;

public final class BlockTicks {

    /** 예약 틱 하나(저장·조회용 보기). */
    public record Scheduled(long pos, int type, long triggerTick, int priority, long subTickOrder) {}

    public interface Ticker {
        void tick(Pos pos, int type);
    }

    /**
     * 바닐라 ScheduledTick. trigger 는 region 합치기 때 한 컨테이너의 모든 예약을 같은 만큼 미는 데만 바뀐다(순서가 그대로라 큐가 깨지지 않는다).
     */
    static final class Tick {
        final long pos;
        final int type;
        long trigger;
        final int priority;
        final long sub;

        Tick(long pos, int type, long trigger, int priority, long sub) {
            this.pos = pos;
            this.type = type;
            this.trigger = trigger;
            this.priority = priority;
            this.sub = sub;
        }

        Scheduled view() { return new Scheduled(pos, type, trigger, priority, sub); }
    }

    /** 바닐라 ScheduledTick.DRAIN_ORDER: 시각 → 우선순위 → 순번. */
    static final Comparator<Tick> DRAIN_ORDER = (a, b) -> {
        int c = Long.compare(a.trigger, b.trigger);
        if (c != 0) return c;
        c = Integer.compare(a.priority, b.priority);
        return c != 0 ? c : Long.compare(a.sub, b.sub);
    };

    /** 바닐라 ScheduledTick.INTRA_TICK_DRAIN_ORDER: 우선순위 → 순번(시각은 보지 않는다). */
    static final Comparator<Tick> INTRA_TICK = (a, b) -> {
        int c = Integer.compare(a.priority, b.priority);
        return c != 0 ? c : Long.compare(a.sub, b.sub);
    };

    private static final long NONE = Long.MAX_VALUE;   // nextTickForContainer 에 항목이 없음(바닐라 기본 반환값과 같은 값)

    /** 바닐라 LevelChunkTicks: 청크 하나의 예약 — 실행 순서 큐와 (칸, 종류) 집합(ticksPerPosition). */
    static final class Container {
        final long key;
        final PriorityQueue<Tick> queue = new PriorityQueue<>(DRAIN_ORDER);
        final PosTypeSet keys = new PosTypeSet();
        BlockTicks owner;
        /** nextTickForContainer 값(NONE 이면 항목 없음). */
        long next = NONE;
        /** waiting 목록에 들어 있는가(항목이 지워져도 다음 훑기까지는 남아 있을 수 있다). */
        boolean listed;
        /** 이번 틱에 꺼냈다 — 실행이 끝난 뒤 비었으면 내려 놓는다. */
        boolean drained;

        Container(long key, BlockTicks owner) {
            this.key = key;
            this.owner = owner;
        }

        Tick peek() { return queue.peek(); }

        Tick poll() {
            Tick t = queue.poll();
            if (t != null) keys.remove(t.pos, t.type);
            return t;
        }

        List<Scheduled> sortedViews() {
            Tick[] a = queue.toArray(new Tick[0]);
            Arrays.sort(a, DRAIN_ORDER);
            List<Scheduled> out = new ArrayList<>(a.length);
            for (Tick t : a) out.add(t.view());
            return out;
        }
    }

    private final LongMap<Container> containers = new LongMap<>();
    /** nextTickForContainer 의 열쇠들(항목이 있는 컨테이너). 값은 Container.next. */
    private final ArrayList<Container> waiting = new ArrayList<>();
    private final PriorityQueue<Container> containersToTick = new PriorityQueue<>((a, b) -> INTRA_TICK.compare(a.peek(), b.peek()));
    private final ArrayDeque<Tick> toRunThisTick = new ArrayDeque<>();
    /** 바닐라 toRunThisTickSet — willTickThisTick 을 처음 물을 때 남은 toRunThisTick 으로 채운다. */
    private final PosTypeSet toRunThisTickSet = new PosTypeSet();
    private final ArrayList<Container> drainedThisTick = new ArrayList<>();
    private final ChunkPredicateMemo memo = new ChunkPredicateMemo();
    private int size;

    /** 칸(Pos.asLong)의 청크 키({@link ChunkPredicateMemo#chunkKey}) — Pos 를 만들지 않는다. */
    static long chunkKeyOfPos(long packed) {
        int x = (int) (packed >> 38), z = (int) (packed << 26 >> 38);
        return ChunkPredicateMemo.chunkKey(x >> 4, z >> 4);
    }

    private Container container(long chunkKey) {
        Container c = containers.get(chunkKey);
        if (c == null) {
            c = new Container(chunkKey, this);
            containers.put(chunkKey, c);
        }
        return c;
    }

    /** 바닐라 updateContainerScheduling / nextTickForContainer.put. */
    private void setNext(Container c, long trigger) {
        c.next = trigger;
        if (!c.listed) {
            c.listed = true;
            waiting.add(c);
        }
    }

    /** 바닐라 LevelChunkTicks.schedule + onTickAdded(새 예약이 머리가 되면 컨테이너의 다음 시각을 바꾼다). */
    private boolean add(Container c, Tick t) {
        if (!c.keys.add(t.pos, t.type)) return false;
        c.queue.add(t);
        size++;
        if (c.peek() == t) setNext(c, t.trigger);
        return true;
    }

    /** schedule — 이미 같은 (칸, 종류)가 있으면 무시. */
    public void schedule(Pos pos, int type, long triggerTick, int priority, long subTickOrder) {
        long p = pos.asLong();
        add(container(chunkKeyOfPos(p)), new Tick(p, type, triggerTick, priority, subTickOrder));
    }

    public boolean hasScheduledTick(Pos pos, int type) {
        long p = pos.asLong();
        Container c = containers.get(chunkKeyOfPos(p));
        return c != null && c.keys.contains(p, type);
    }

    /** 바닐라 willTickThisTick: 이번 틱에 꺼냈고 아직 돌지 않은 예약인가. */
    public boolean willTickThisTick(Pos pos, int type) {
        if (toRunThisTickSet.isEmpty() && !toRunThisTick.isEmpty()) {
            for (Tick t : toRunThisTick) toRunThisTickSet.add(t.pos, t.type);
        }
        return toRunThisTickSet.contains(pos.asLong(), type);
    }

    public int size() { return size; }

    /** 그 청크에 예약이 있는가. */
    public boolean hasChunkTicks(int chunkX, int chunkZ) {
        Container c = containers.get(ChunkPredicateMemo.chunkKey(chunkX, chunkZ));
        return c != null && !c.queue.isEmpty();
    }

    /** gameTime 이하인 예약을 순서대로 실행(최대 maxAllowed). 실행 중 새로 예약된 것은 다음 틱 이후로. */
    public void tick(long gameTime, int maxAllowed, Ticker ticker) { tick(gameTime, maxAllowed, null, ticker); }

    /**
     * 바닐라 LevelTicks.tick(gameTime, max, ticker) — 생성자 술어(ServerLevel.isPositionTickingWithEntitiesLoaded)가 참인 청크의 예약만 꺼낸다.
     * chunkTicking 은 청크 키({@link ChunkPredicateMemo#chunkKey})를 받는다. null 이면 모든 청크. 범위 밖 청크의 예약은 컨테이너에 그대로 남는다.
     */
    public void tick(long gameTime, int maxAllowed, LongPredicate chunkTicking, Ticker ticker) {
        LongPredicate ticking = chunkTicking == null ? null : memo.reset(chunkTicking);
        sortContainersToTick(gameTime, ticking);
        drainContainers(gameTime, maxAllowed);
        rescheduleLeftoverContainers();
        // runCollectedTicks
        while (!toRunThisTick.isEmpty()) {
            Tick t = toRunThisTick.poll();
            if (!toRunThisTickSet.isEmpty()) toRunThisTickSet.remove(t.pos, t.type);
            ticker.tick(Pos.of(t.pos), t.type);
        }
        // cleanupAfterTick
        toRunThisTickSet.clear();
        for (Container c : drainedThisTick) {
            c.drained = false;
            if (c.owner == this && c.queue.isEmpty() && !c.listed) discard(c);   // 빈 컨테이너는 내려 놓는다(다시 예약하면 새로 만든다)
        }
        drainedThisTick.clear();
    }

    private void sortContainersToTick(long gameTime, LongPredicate ticking) {
        int w = 0;
        for (int i = 0, n = waiting.size(); i < n; i++) {
            Container c = waiting.get(i);
            boolean keep = c.owner == this && c.next != NONE;
            if (keep && c.next <= gameTime) {
                Tick t = c.peek();
                if (t == null) {
                    c.next = NONE;
                    keep = false;
                } else if (t.trigger > gameTime) {
                    c.next = t.trigger;
                } else if (ticking == null || ticking.test(c.key)) {
                    c.next = NONE;
                    keep = false;
                    containersToTick.add(c);
                }
            }
            if (keep) waiting.set(w++, c);
            else if (c.owner == this) c.listed = false;
        }
        for (int i = waiting.size() - 1; i >= w; i--) waiting.remove(i);
    }

    private void drainContainers(long gameTime, int maxAllowed) {
        if (drainAllOnTime(gameTime, maxAllowed)) return;
        Container c;
        while (toRunThisTick.size() < maxAllowed && (c = containersToTick.poll()) != null) {
            if (!c.drained) {
                c.drained = true;
                drainedThisTick.add(c);
            }
            take(c);
            drainFromCurrentContainer(c, gameTime, maxAllowed);
            Tick n = c.peek();
            if (n != null) {
                if (n.trigger <= gameTime && toRunThisTick.size() < maxAllowed) containersToTick.add(c);
                else setNext(c, n.trigger);
            }
        }
    }

    private final ArrayList<Tick> batch = new ArrayList<>();

    /**
     * 빠른 길: 꺼낼 컨테이너의 머리가 모두 이번 시각이고(지난 예약 없음) 그 컨테이너들의 예약을 다 합쳐도 한도 안이면, 바닐라 drainContainers 의
     * 결과는 "이번 시각 예약 전부를 (우선순위, 순번)으로 정렬한 것"과 같다 — 컨테이너 안이 이미 그 순서이고, 머리끼리 고르는 것은 정렬된 목록들의 병합이다.
     * 컨테이너마다 같은 순서로 꺼내므로 컨테이너 큐의 모양(저장 순서)도 같다. 다르면 false(바닐라 그대로의 길로).
     * (우선순위, 순번)이 같은 두 청크 머리(동점)의 순서만 병합과 다를 수 있다 — 바닐라에서도 해시 순회에 달린 정의되지 않은 순서다.
     */
    private boolean drainAllOnTime(long gameTime, int maxAllowed) {
        long total = 0;
        for (Container c : containersToTick) {
            if (c.peek().trigger != gameTime) return false;
            total += c.queue.size();
        }
        if (total > maxAllowed - toRunThisTick.size()) return false;
        Container c;
        while ((c = containersToTick.poll()) != null) {
            if (!c.drained) {
                c.drained = true;
                drainedThisTick.add(c);
            }
            Tick t;
            while ((t = c.peek()) != null && t.trigger <= gameTime) {
                batch.add(c.poll());
                size--;
            }
            if (t != null) setNext(c, t.trigger);
        }
        batch.sort(INTRA_TICK);
        toRunThisTick.addAll(batch);
        batch.clear();
        return true;
    }

    private void drainFromCurrentContainer(Container c, long gameTime, int maxAllowed) {
        if (toRunThisTick.size() >= maxAllowed) return;
        Container next = containersToTick.peek();
        Tick nextHead = next != null ? next.peek() : null;
        while (toRunThisTick.size() < maxAllowed) {
            Tick t = c.peek();
            if (t == null || t.trigger > gameTime || nextHead != null && INTRA_TICK.compare(t, nextHead) > 0) break;
            take(c);
        }
    }

    private void take(Container c) {
        toRunThisTick.add(c.poll());
        size--;
    }

    private void rescheduleLeftoverContainers() {
        for (Container c : containersToTick) setNext(c, c.peek().trigger);
        containersToTick.clear();
    }

    private void discard(Container c) {
        containers.remove(c.key);
        c.owner = null;
        c.next = NONE;
    }

    /**
     * 청크 조건(청크 키)에 맞는 컨테이너를 target 으로 옮긴다(region 합치기·나누기). 실행 시각은 timeOffset 만큼 민다(두 region 의 지역 시계 차이).
     * 순서(같은 시각·우선순위 안의 예약 순번)는 그대로 유지된다. target 에 같은 청크 컨테이너가 이미 있으면 하나씩 넣는다(이미 있는 (칸, 종류)는 target 쪽이 남는다).
     */
    public void moveTo(BlockTicks target, LongPredicate chunkFilter, long timeOffset) {
        List<Container> moving = new ArrayList<>();
        containers.forEachValue(c -> { if (chunkFilter.test(c.key)) moving.add(c); });
        if (moving.isEmpty()) return;
        for (Container c : moving) {
            containers.remove(c.key);
            size -= c.queue.size();
            c.owner = null;
            c.next = NONE;
            if (timeOffset != 0) for (Tick t : c.queue) t.trigger += timeOffset;   // 모두 같은 만큼 — 큐 순서가 그대로다
            Container existing = target.containers.get(c.key);
            if (existing == null) {
                c.owner = target;
                c.listed = false;
                c.drained = false;
                target.containers.put(c.key, c);
                target.size += c.queue.size();
                Tick head = c.peek();
                if (head != null) target.setNext(c, head.trigger);   // 바닐라 addContainer
            } else {
                for (Tick t : c.queue) target.add(existing, t);
            }
        }
        waiting.removeIf(c -> c.owner != this);
    }

    /** 그 청크의 예약(실행 순서대로) — 꺼내지 않는다(저장용). */
    public List<Scheduled> chunkTicks(int chunkX, int chunkZ) {
        Container c = containers.get(ChunkPredicateMemo.chunkKey(chunkX, chunkZ));
        return c == null ? new ArrayList<>() : c.sortedViews();
    }

    /** 청크를 내릴 때 그 청크의 예약을 꺼낸다(저장용, 실행 순서대로). 컨테이너도 내려 놓는다(바닐라 removeContainer). */
    public List<Scheduled> removeChunk(int chunkX, int chunkZ) {
        Container c = containers.get(ChunkPredicateMemo.chunkKey(chunkX, chunkZ));
        if (c == null) return new ArrayList<>();
        List<Scheduled> out = c.sortedViews();
        size -= c.queue.size();
        discard(c);
        return out;
    }

    /**
     * 바닐라 LevelTicks.copyAreaFrom: src 에서 상자(포함) 안 칸의 예약을 (dx,dy,dz) 만큼 옮겨 이 쪽에 예약한다. 실행 시각·우선순위는 그대로,
     * 순번은 (원래 순번 - 모은 것 중 최소 + 최대 + 1) 이다(바닐라 식 그대로). 먼저 다 모은 뒤 넣으므로 src 가 this 여도 된다.
     */
    public void copyAreaFrom(BlockTicks src, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int dx, int dy, int dz) {
        List<Tick> list = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                Container c = src.containers.get(ChunkPredicateMemo.chunkKey(cx, cz));
                if (c == null) continue;
                for (Tick t : c.queue) {
                    Pos p = Pos.of(t.pos);
                    if (p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ) list.add(t);
                }
            }
        }
        if (list.isEmpty()) return;
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (Tick t : list) {
            min = Math.min(min, t.sub);
            max = Math.max(max, t.sub);
        }
        list.sort(DRAIN_ORDER);
        for (Tick t : list) {
            Pos p = Pos.of(t.pos);
            schedule(new Pos(p.x() + dx, p.y() + dy, p.z() + dz), t.type, t.trigger, t.priority, t.sub - min + max + 1L);
        }
    }

    // ------------------------------------------------------------------ 원시 자료 구조

    /** (칸 long, 종류 int) 열린 주소 집합(선형 탐사, 지울 때 뒤로 당기기). 박싱·열쇠 객체를 만들지 않는다. */
    static final class PosTypeSet {
        private static final int EMPTY = Integer.MIN_VALUE;
        private long[] pos = new long[8];
        private int[] type = filled(8);
        private int size;

        private static int[] filled(int n) {
            int[] a = new int[n];
            Arrays.fill(a, EMPTY);
            return a;
        }

        private static int hash(long p, int t) {
            long h = (p ^ (long) t * 0x9E3779B97F4A7C15L) * 0xC2B2AE3D27D4EB4FL;
            return (int) (h ^ h >>> 32);
        }

        int size() { return size; }

        boolean isEmpty() { return size == 0; }

        boolean contains(long p, int t) {
            int m = type.length - 1;
            for (int i = hash(p, t) & m; type[i] != EMPTY; i = i + 1 & m) {
                if (type[i] == t && pos[i] == p) return true;
            }
            return false;
        }

        boolean add(long p, int t) {
            if (t == EMPTY) throw new IllegalArgumentException("종류 값 " + t);
            int m = type.length - 1;
            int i = hash(p, t) & m;
            for (; type[i] != EMPTY; i = i + 1 & m) {
                if (type[i] == t && pos[i] == p) return false;
            }
            pos[i] = p;
            type[i] = t;
            if (++size * 4 > type.length * 3) grow();
            return true;
        }

        boolean remove(long p, int t) {
            int m = type.length - 1;
            int i = hash(p, t) & m;
            for (; type[i] != EMPTY; i = i + 1 & m) {
                if (type[i] == t && pos[i] == p) {
                    shiftBack(i, m);
                    size--;
                    return true;
                }
            }
            return false;
        }

        /** 지운 칸 i 뒤의 사슬을 당겨 탐사 길이 빈칸을 없앤다. */
        private void shiftBack(int i, int m) {
            int last = i;
            for (int j = i + 1 & m; type[j] != EMPTY; j = j + 1 & m) {
                int home = hash(pos[j], type[j]) & m;
                // j 의 원래 자리가 (last, j] 밖에 있으면 last 로 당길 수 있다
                if (last <= j ? home <= last || home > j : home <= last && home > j) {
                    pos[last] = pos[j];
                    type[last] = type[j];
                    last = j;
                }
            }
            type[last] = EMPTY;
        }

        private void grow() {
            long[] op = pos;
            int[] ot = type;
            pos = new long[op.length * 2];
            type = filled(op.length * 2);
            int m = type.length - 1;
            for (int k = 0; k < ot.length; k++) {
                if (ot[k] == EMPTY) continue;
                int i = hash(op[k], ot[k]) & m;
                while (type[i] != EMPTY) i = i + 1 & m;
                pos[i] = op[k];
                type[i] = ot[k];
            }
        }

        void clear() {
            if (size == 0) return;
            if (type.length > 1024) {
                pos = new long[8];
                type = filled(8);
            } else {
                Arrays.fill(type, EMPTY);
            }
            size = 0;
        }
    }

    /** long 열쇠 → 값 열린 주소 사전(선형 탐사, 지울 때 뒤로 당기기). 청크 키 → 컨테이너. */
    static final class LongMap<V> {
        private long[] keys = new long[16];
        private Object[] vals = new Object[16];
        private int size;

        private static int hash(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ h >>> 32);
        }

        @SuppressWarnings("unchecked")
        V get(long k) {
            int m = vals.length - 1;
            for (int i = hash(k) & m; vals[i] != null; i = i + 1 & m) {
                if (keys[i] == k) return (V) vals[i];
            }
            return null;
        }

        void put(long k, V v) {
            int m = vals.length - 1;
            int i = hash(k) & m;
            for (; vals[i] != null; i = i + 1 & m) {
                if (keys[i] == k) {
                    vals[i] = v;
                    return;
                }
            }
            keys[i] = k;
            vals[i] = v;
            if (++size * 4 > vals.length * 3) grow();
        }

        void remove(long k) {
            int m = vals.length - 1;
            for (int i = hash(k) & m; vals[i] != null; i = i + 1 & m) {
                if (keys[i] == k) {
                    int last = i;
                    for (int j = i + 1 & m; vals[j] != null; j = j + 1 & m) {
                        int home = hash(keys[j]) & m;
                        if (last <= j ? home <= last || home > j : home <= last && home > j) {
                            keys[last] = keys[j];
                            vals[last] = vals[j];
                            last = j;
                        }
                    }
                    vals[last] = null;
                    size--;
                    return;
                }
            }
        }

        int size() { return size; }

        @SuppressWarnings("unchecked")
        void forEachValue(java.util.function.Consumer<V> f) {
            for (Object v : vals) if (v != null) f.accept((V) v);
        }

        private void grow() {
            long[] ok = keys;
            Object[] ov = vals;
            keys = new long[ok.length * 2];
            vals = new Object[ov.length * 2];
            int m = vals.length - 1;
            for (int k = 0; k < ov.length; k++) {
                if (ov[k] == null) continue;
                int i = hash(ok[k]) & m;
                while (vals[i] != null) i = i + 1 & m;
                keys[i] = ok[k];
                vals[i] = ov[k];
            }
        }
    }
}
