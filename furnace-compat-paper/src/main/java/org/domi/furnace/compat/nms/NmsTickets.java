package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.ChunkTicket;
import org.domi.furnace.api.world.ChunkTickets;
import org.slf4j.LoggerFactory;

/**
 * NMS 청크 티켓({@code ServerChunkCache#addRegionTicket}·{@code addTicketAtLevel}·{@code removeTicketAtLevel}) → Furnace 청크 티켓(N2b-6).
 *
 * <p>바닐라 티켓 수준 L 은 중심에서 거리 d 인 청크를 수준 L+d 로 올린다(33 이하 = 로드, 32 이하 = 블록 틱, 31 이하 = 엔티티 틱). Furnace 티켓은
 * 반경 안을 "로드만"(ticking=false) 또는 "엔티티 틱까지"(ticking=true) 붙잡으므로, 반경 33−L 의 로드 티켓과 (L ≤ 31 이면) 반경 31−L 의 틱 티켓으로 옮긴다.
 * 바닐라와 다른 점: 수준 32 링(블록 틱만 하고 엔티티 틱은 안 함)은 Furnace 에 해당 등급이 없어 로드만으로 둔다.
 *
 * <p>같은 (종류, 수준, 값 — 종류의 비교기로 0) 티켓은 바닐라처럼 하나로 세고, 만료 틱이 있는 종류(예: 내리기 유예 100틱)는 다시 걸면 만료가 미뤄진다.
 * 티켓 주인은 그 호출을 한 플러그인이다(스택에서 찾는다 — Furnace 티켓은 주인이 꺼지면 함께 풀린다). 플러그인을 못 찾으면 걸지 않고 한 번 경고한다.
 */
public final class NmsTickets {

    private NmsTickets() {}

    private static final class Entry {
        final TicketType<?> type;
        final int level;
        final Object value;
        final List<ChunkTicket> held = new ArrayList<>(2);
        volatile long expiresAt;

        Entry(TicketType<?> type, int level, Object value) {
            this.type = type;
            this.level = level;
            this.value = value;
        }
    }

    private static final Map<String, List<Entry>> BY_CHUNK = new ConcurrentHashMap<>();

    private static String key(ServerLevel level, ChunkPos pos) { return level.getWorld().getName() + "|" + pos.x + "," + pos.z; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean same(Entry e, TicketType<?> type, int level, Object value) {
        if (e.type != type || e.level != level) return false;
        if (e.value == value) return true;
        if (e.value == null || value == null) return false;
        return ((java.util.Comparator) type.getComparator()).compare(e.value, value) == 0;
    }

    /** 바닐라 addTicket(수준 L). L 이 33 보다 크면 로드되지 않는 티켓이라 바닐라처럼 아무것도 붙잡지 않는다(세기만 한다). */
    public static <T> void add(ServerLevel level, TicketType<T> type, ChunkPos pos, int ticketLevel, T value) {
        FurnacePlugin owner = callerPlugin();
        if (owner == null) {
            NmsCaller.warnOnce("nms-ticket-owner", "플러그인 {}: NMS 청크 티켓의 주인 플러그인을 찾지 못해 티켓을 걸지 않았다");
            return;
        }
        long now = Furnace.server().currentTick();
        String k = key(level, pos);
        Entry fresh;
        synchronized (NmsTickets.class) {
            List<Entry> list = BY_CHUNK.computeIfAbsent(k, x -> new ArrayList<>());
            for (Entry e : list) {
                if (same(e, type, ticketLevel, value)) {
                    if (type.timeout() > 0) e.expiresAt = now + type.timeout();
                    return;
                }
            }
            fresh = new Entry(type, ticketLevel, value);
            fresh.expiresAt = type.timeout() > 0 ? now + type.timeout() : Long.MAX_VALUE;
            list.add(fresh);
        }
        var world = level.getWorld().furnaceHandle();
        ChunkTickets tickets = Furnace.server().chunkTickets();
        int loadRadius = 33 - ticketLevel;
        int tickRadius = 31 - ticketLevel;
        try {
            if (loadRadius >= 0) fresh.held.add(tickets.add(owner, world, pos.x, pos.z, Math.min(loadRadius, ChunkTickets.MAX_RADIUS), false));
            if (tickRadius >= 0) fresh.held.add(tickets.add(owner, world, pos.x, pos.z, Math.min(tickRadius, ChunkTickets.MAX_RADIUS), true));
        } catch (RuntimeException ex) {
            LoggerFactory.getLogger("Furnace").warn("NMS 청크 티켓 {} ({}, {}) 수준 {} 를 걸지 못했다: {}", type, pos.x, pos.z, ticketLevel, ex.toString());
        }
        if (type.timeout() > 0) scheduleExpiry(level, pos, fresh, type.timeout());
    }

    /** 바닐라 removeTicket: 같은 (종류, 수준, 값) 티켓을 푼다. 없으면 아무 일도 없다. */
    public static <T> void remove(ServerLevel level, TicketType<T> type, ChunkPos pos, int ticketLevel, T value) {
        Entry found = null;
        String k = key(level, pos);
        synchronized (NmsTickets.class) {
            List<Entry> list = BY_CHUNK.get(k);
            if (list == null) return;
            for (var it = list.iterator(); it.hasNext(); ) {
                Entry e = it.next();
                if (same(e, type, ticketLevel, value)) {
                    found = e;
                    it.remove();
                    break;
                }
            }
            if (list.isEmpty()) BY_CHUNK.remove(k);
        }
        if (found != null) release(found);
    }

    private static void release(Entry e) {
        for (ChunkTicket t : e.held) {
            try {
                t.release();
            } catch (RuntimeException ignored) {
                // 주인이 이미 꺼져 서버가 풀었다
            }
        }
        e.held.clear();
    }

    private static void scheduleExpiry(ServerLevel level, ChunkPos pos, Entry e, long delay) {
        Furnace.server().scheduler().global().executeDelayed(() -> {
            long now = Furnace.server().currentTick();
            if (e.expiresAt > now) {
                scheduleExpiry(level, pos, e, e.expiresAt - now);   // 그 사이 다시 걸려 미뤄졌다
                return;
            }
            boolean removed;
            String k = key(level, pos);
            synchronized (NmsTickets.class) {
                List<Entry> list = BY_CHUNK.get(k);
                removed = list != null && list.remove(e);
                if (list != null && list.isEmpty()) BY_CHUNK.remove(k);
            }
            if (removed) release(e);
        }, Math.max(1, delay));
    }

    /** 스택에서 가장 가까운 Bukkit 플러그인(없으면 null). */
    static FurnacePlugin callerPlugin() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(f -> (FurnacePlugin) org.domi.furnace.compat.paper.PluginLoaders.of(f.getDeclaringClass().getClassLoader()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null));
    }

    /** 시험용: 그 청크에 지금 걸린 NMS 티켓 수. */
    public static int countAt(ServerLevel level, ChunkPos pos) {
        synchronized (NmsTickets.class) {
            List<Entry> list = BY_CHUNK.get(key(level, pos));
            return list == null ? 0 : list.size();
        }
    }
}
