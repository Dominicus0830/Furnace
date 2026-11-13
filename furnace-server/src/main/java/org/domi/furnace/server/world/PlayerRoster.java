package org.domi.furnace.server.world;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.domi.furnace.server.entity.Player;

/**
 * 접속자 집합(서버 전체 — 모든 차원 월드가 같은 것을 쓴다)과 차원별 플레이어 목록(바닐라 {@code ServerLevel.players}).
 *
 * <p>차원별 목록은 바닐라처럼 <b>그 차원에 들어온 순서</b>다: 접속·차원 이동으로 들어오면 끝에 붙고, 떠나면(퇴장·차원 이동) 빠진다.
 * 리스폰은 바닐라 {@code PlayerList.respawn}(옛 플레이어를 빼고 새 플레이어를 끝에 붙임)처럼 같은 차원이어도 끝으로 옮긴다
 * ({@link #relist}). 목록에 드는 플레이어 <b>집합</b>은 예전 정의(접속 중이고 {@code p.dimension()} 이 이 차원)와 같다 — 차원 필드가
 * 바뀌는 즉시 {@link Player#dimension(String)} 이 {@link #dimensionChanged} 를 부른다.
 *
 * <p>차원별 목록은 플레이어 근접 색인(청크 격자, {@link Level#forEachNear})도 함께 들고 있다. 격자는 후보를 걸러 줄 뿐이고
 * 부르는 쪽이 늘 원래의 거리·상자 판정을 다시 한다. 후보는 언제나 목록 순서로 넘기므로 "같은 거리면 먼저 찾은 쪽" 같은 순서
 * 규칙이 전체 훑기와 같다. 격자 칸은 위치를 쓰는 곳({@code Player.applyMove·setRidingPos·setPosRaw})이 쓴 직후 {@link Level#moved}
 * 로 맞춘다 — 같은 스레드에서 위치를 바꾼 뒤 조회하면 늘 새 칸으로 찾는다.
 *
 * <p>쓰기(접속·퇴장·차원 이동·칸 바뀜)는 {@link #map} 모니터 아래, 읽기는 잠금 없이 불변 스냅샷 하나를 읽는다.
 */
public final class PlayerRoster {
    /** 격자 칸 한 변(블록) = 2^CELL_SHIFT. 128: 반경 128 조회가 3×3 칸 안팎, 몸 상자 조회가 1~4 칸. */
    public static final int CELL_SHIFT = 7;
    /** 거리 제한 없는 "가장 가까운 플레이어" 조회의 1단계 반경(바닐라 사라짐 거리 128). 이 안에 아무도 없으면 전체를 훑는다. */
    public static final double NEAREST_FIRST_RADIUS = 128.0;
    /**
     * 이보다 적은 차원은 격자를 쓰지 않고 목록을 그대로 훑는다(PlayerScanBench: 15명은 목록이 조금 빠르고, 30명은 격자가 약 −25%).
     * -Dfurnace.playerGridMin 로 바꿔 잴 수 있다.
     */
    public static final int GRID_MIN_PLAYERS = Integer.getInteger("furnace.playerGridMin", 16);

    final ConcurrentHashMap<Integer, Player> map = new ConcurrentHashMap<>();
    /** 서버 전체 접속자, 바닐라 PlayerList.players 순서(접속 순, 리스폰 때 끝으로). 접속·퇴장·리스폰 때만 새로 만든다. */
    volatile Player[] array = new Player[0];
    private final HashMap<String, Level> levels = new HashMap<>();   // map 모니터 아래

    /** 이 차원 목록(없으면 만든다). 월드가 한 번 받아 둔다. */
    public Level level(String dimension) {
        synchronized (map) {
            return levels.computeIfAbsent(dimension, k -> new Level(this, k));
        }
    }

    /** 접속 등록(멱등 — 이미 있으면 차원 목록은 건드리지 않는다). 처음이면 그 차원 목록 끝에 붙는다(onTrackingStart). */
    /** World 전용. 처음이면 서버 전체 목록 끝(PlayerList.placeNewPlayer)과 그 차원 목록 끝(onTrackingStart)에 붙는다. 이미 있으면 그대로. */
    public void put(Player p) {
        synchronized (map) {
            Player old = map.put(p.entityId(), p);
            if (old != p) {
                if (old != null) {
                    array = without(array, old);
                    detach(old);
                }
                array = append(array, p);
                attach(p);
            }
            Player.bumpRoster();
        }
    }

    /** World 전용. */
    public void remove(int eid) {
        synchronized (map) {
            Player old = map.remove(eid);
            if (old != null) {
                array = without(array, old);
                detach(old);
            }
            Player.bumpRoster();
        }
    }

    /** 서버 전체 접속자(바닐라 PlayerList.players 순서 — 접속 순, 리스폰 때 끝으로). 고칠 수 없는 그때의 사본. */
    public java.util.List<Player> onlineInOrder() { return java.util.Collections.unmodifiableList(Arrays.asList(array)); }

    private static Player[] append(Player[] cur, Player p) {
        Player[] next = Arrays.copyOf(cur, cur.length + 1);
        next[cur.length] = p;
        return next;
    }

    private static Player[] without(Player[] cur, Player p) {
        int i = 0;
        while (i < cur.length && cur[i] != p) i++;
        if (i == cur.length) return cur;
        Player[] next = new Player[cur.length - 1];
        System.arraycopy(cur, 0, next, 0, i);
        System.arraycopy(cur, i + 1, next, i, cur.length - i - 1);
        return next;
    }

    /** {@code p.dimension()} 가 바뀌었다: 옛 차원 목록에서 빼고 새 차원 목록 끝에 붙인다(removePlayerImmediately → addDuringTeleport). */
    public void dimensionChanged(Player p) {
        synchronized (map) {
            Level cur = p.rosterLevel();
            if (cur == null || cur.key.equals(p.dimension())) return;
            detach(p);
            attach(p);
        }
    }

    /**
     * 리스폰(PlayerList.respawn): 서버 전체 목록과 지금 차원 목록에서 빼고 끝에 다시 붙인다 — 같은 차원이어도 순서가 끝으로 간다
     * (바닐라 players.remove(옛) → players.add(새), removePlayerImmediately → addRespawnedPlayer).
     */
    public void relist(Player p) {
        synchronized (map) {
            if (map.get(p.entityId()) != p) return;
            array = append(without(array, p), p);
            detach(p);
            attach(p);
        }
    }

    private void attach(Player p) {
        Level l = levels.computeIfAbsent(p.dimension(), k -> new Level(this, k));
        p.rosterLevel(l);
        p.gridCell = Snapshot.EMPTY_KEY;   // 먼저 "모름" — 이 사이 위치를 쓴 스레드는 moved 에서 잠금 쪽으로 온다
        p.gridCell = Level.cellOf(p.x(), p.z());
        l.publish(append(l.snap.players, p));
    }

    private void detach(Player p) {
        Level l = p.rosterLevel();
        if (l == null) return;
        p.rosterLevel(null);
        Player[] cur = l.snap.players, next = without(cur, p);
        if (next != cur) l.publish(next);
    }

    /** 한 차원의 플레이어 목록(도착 순) + 청크 격자 색인. */
    public static final class Level {
        final PlayerRoster roster;
        final String key;
        volatile Snapshot snap = Snapshot.EMPTY;

        Level(PlayerRoster roster, String key) {
            this.roster = roster;
            this.key = key;
        }

        public String dimension() { return key; }

        public PlayerRoster roster() { return roster; }

        /** 목록 순서대로 모두. */
        public void forEach(Consumer<Player> c) {
            for (Player p : snap.players) c.accept(p);
        }

        public boolean isEmpty() { return snap.players.length == 0; }

        public int size() { return snap.players.length; }

        /** 지금 격자로 후보를 거르는가(인원이 GRID_MIN_PLAYERS 이상). 아니면 forEachNear 는 전체를 넘긴다. */
        public boolean gridActive() { return snap.players.length >= GRID_MIN_PLAYERS; }

        /** 지금 목록 복사본(목록 순서). */
        public Player[] toArray() { return snap.players.clone(); }

        /**
         * 격자 칸이 [x0,x1]×[z0,z1] 와 겹치는 플레이어를 <b>목록 순서대로</b>. 상자 안에 있는 플레이어는 모두 들어 있고(위치를 쓴
         * 스레드가 바로 칸을 맞추므로), 상자 밖 플레이어가 섞일 수 있다 — 부르는 쪽이 원래 판정을 다시 한다. 칸이 플레이어 수보다
         * 많으면 그냥 전체를 넘긴다(같은 규칙: 상위 집합 + 목록 순서).
         */
        public void forEachNear(double x0, double z0, double x1, double z1, Consumer<Player> c) {
            Snapshot s = snap;
            Player[] ps = s.players;
            if (ps.length < GRID_MIN_PLAYERS || !(x0 <= x1) || !(z0 <= z1)) {   // 적은 인원·NaN 등 — 걸러 내지 않는다
                for (Player p : ps) c.accept(p);
                return;
            }
            int cx0 = cellCoord(x0), cx1 = cellCoord(x1), cz0 = cellCoord(z0), cz1 = cellCoord(z1);
            long cells = ((long) cx1 - cx0 + 1) * ((long) cz1 - cz0 + 1);
            if (cells > s.cellCount || cells > 64) {   // 볼 칸이 플레이어 있는 칸보다 많으면(몰린 서버의 넓은 조회) 그냥 전부가 싸다
                for (Player p : ps) c.accept(p);
                return;
            }
            int w = s.words;
            if (w == 1) {   // 64명 이하: 칸 비트를 OR 해 낮은 번호부터 = 목록 순서
                long acc = 0;
                for (int cx = cx0; cx <= cx1; cx++) {
                    for (int cz = cz0; cz <= cz1; cz++) {
                        long[] v = s.get(key(cx, cz));
                        if (v != null) acc |= v[0];
                    }
                }
                while (acc != 0) {
                    c.accept(ps[Long.numberOfTrailingZeros(acc)]);
                    acc &= acc - 1;
                }
                return;
            }
            long[] acc = null;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    long[] v = s.get(key(cx, cz));
                    if (v == null) continue;
                    if (acc == null) acc = new long[w];
                    for (int k = 0; k < w; k++) acc[k] |= v[k];
                }
            }
            if (acc == null) return;
            for (int k = 0; k < w; k++) {
                long bits = acc[k];
                while (bits != 0) {
                    c.accept(ps[(k << 6) + Long.numberOfTrailingZeros(bits)]);
                    bits &= bits - 1;
                }
            }
        }

        /**
         * 플레이어 위치를 쓴 직후(쓴 스레드에서): 격자 칸이 바뀌었으면 스냅샷을 다시 만든다.
         *
         * <p>잠금 없는 빠른 길(칸이 같으면 돌아감)의 정확성은 {@code Player.x/z} 와 {@code gridCell} 이 <b>volatile</b> 인 데 기댄다(Dekker 꼴):
         * 쓴 쪽은 x/z 쓰기 → gridCell 읽기, 잠금 쪽은 gridCell 을 "모름"으로 쓰기 → x/z 읽기라 둘 중 하나는 반드시 상대를 본다.
         * 그 volatile 을 떼면 칸이 낡은 채 남을 수 있다(PlayerRosterRaceTest).
         *
         * <p>비용: 칸을 넘는 쓰기 한 번 = 스냅샷 전체 재생성(검토 실측 32명 0.56µs, 100명 1.7µs, 300명 7.0µs; 같은 칸 쓰기는 수십 ns).
         * 잠금은 모든 차원이 같이 쓰는 {@code roster.map} 이다.
         */
        public void moved(Player p, double x, double z) {
            if (cellOf(x, z) == p.gridCell) return;
            synchronized (roster.map) {
                if (p.rosterLevel() != this) return;   // 그 사이 떠났다
                long before = p.gridCell;
                p.gridCell = Snapshot.EMPTY_KEY;        // attach 와 같은 이유(다른 스레드가 동시에 위치를 쓰면 그쪽도 잠금으로 온다)
                long c = cellOf(p.x(), p.z());          // 잠금 안에서 최신 위치로
                p.gridCell = c;
                if (c != before) publish(snap.players);
            }
        }

        /** 잠금 안에서: 목록(과 각자의 gridCell)으로 스냅샷을 새로 만든다. */
        void publish(Player[] players) {
            snap = Snapshot.build(players);
        }

        static int cellCoord(double v) {
            return Math.floorDiv((int) Math.max(Math.min(Math.floor(v), 1.0E9), -1.0E9), 1 << CELL_SHIFT);
        }

        static long cellOf(double x, double z) { return key(cellCoord(x), cellCoord(z)); }

        static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xffffffffL); }
    }

    /** 불변: 목록 + 칸 → 그 칸 플레이어의 목록 번호 비트 집합(words 개 long). 열린 주소 해시. */
    static final class Snapshot {
        static final long EMPTY_KEY = Long.MIN_VALUE;   // cellCoord 범위(±1e9/128)에서 나올 수 없는 값
        static final Snapshot EMPTY = new Snapshot(new Player[0], new long[] {EMPTY_KEY}, new long[1][], 0, 1, 0);

        final Player[] players;
        private final long[] keys;
        private final long[][] vals;
        private final int mask;
        final int words;
        /** 플레이어가 있는 칸 수. */
        final int cellCount;

        private Snapshot(Player[] players, long[] keys, long[][] vals, int mask, int words, int cellCount) {
            this.players = players;
            this.keys = keys;
            this.vals = vals;
            this.mask = mask;
            this.words = words;
            this.cellCount = cellCount;
        }

        static Snapshot build(Player[] players) {
            int n = players.length;
            if (n == 0) return EMPTY;
            int words = (n + 63) >>> 6;
            int cap = Integer.highestOneBit(Math.max(4, n * 4 - 1)) << 1;
            long[] keys = new long[cap];
            Arrays.fill(keys, EMPTY_KEY);
            long[][] vals = new long[cap][];
            int mask = cap - 1, cells = 0;
            for (int i = 0; i < n; i++) {
                long k = players[i].gridCell;
                int s = slot(k, mask);
                while (keys[s] != EMPTY_KEY && keys[s] != k) s = (s + 1) & mask;
                if (keys[s] == EMPTY_KEY) {
                    keys[s] = k;
                    vals[s] = new long[words];
                    cells++;
                }
                vals[s][i >>> 6] |= 1L << i;
            }
            return new Snapshot(players, keys, vals, mask, words, cells);
        }

        long[] get(long k) {
            int s = slot(k, mask);
            while (true) {
                long kk = keys[s];
                if (kk == k) return vals[s];
                if (kk == EMPTY_KEY) return null;
                s = (s + 1) & mask;
            }
        }

        private static int slot(long k, int mask) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32)) & mask;
        }
    }
}
