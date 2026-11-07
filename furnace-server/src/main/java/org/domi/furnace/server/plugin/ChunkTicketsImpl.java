// 청크 티켓 API 구현(W4/46번): 티켓 장부는 플러그인 쪽에 두고, 청크를 붙잡는 일 자체는 World.addPluginTicket 이 센다.
// 플러그인이 꺼지면 PluginManagerImpl.disable 이 releaseAll 을 부른다 — 핫 리로드로 새 객체가 되면 옛 객체의 티켓은 옛 객체가 꺼질 때 풀린다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.ChunkTicket;
import org.domi.furnace.api.world.ChunkTickets;
import org.domi.furnace.server.world.World;

public final class ChunkTicketsImpl implements ChunkTickets {

    private final PluginHost host;
    /** 건 순서대로의 살아 있는 티켓(장부 접근은 이 객체 잠금 안). */
    private final List<TicketImpl> active = new ArrayList<>();
    /** 꺼지며 정리한 주인 — 꺼진 뒤 늦게 도착한 add 는 받지 않는다(다시 켜지면 isEnabled 로 풀린다). */
    private final java.util.Set<Object> cleared = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    ChunkTicketsImpl(PluginHost host) { this.host = host; }

    private static World server(org.domi.furnace.api.world.World w) {
        if (w instanceof WorldHandle h) return h.handle();
        throw new IllegalArgumentException("이 서버의 월드가 아니다: " + w);
    }

    private boolean rejected(FurnacePlugin owner) {
        if (owner.isEnabled()) {
            cleared.remove(owner);
            return false;
        }
        return cleared.contains(owner);
    }

    @Override
    public ChunkTicket add(FurnacePlugin owner, org.domi.furnace.api.world.World world, int chunkX, int chunkZ, int radius, boolean ticking) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        if (radius < 0 || radius > MAX_RADIUS) throw new IllegalArgumentException("radius 는 0.." + MAX_RADIUS + ": " + radius);
        World w = server(world);
        if (w.isUnloaded()) throw new IllegalStateException("내린 월드: " + world.name());
        if (rejected(owner)) throw new IllegalStateException("꺼진 플러그인은 청크 티켓을 걸 수 없다: " + owner.name());
        int n = 2 * radius + 1;
        TicketImpl t = new TicketImpl(this, owner, (WorldHandle) world, w, chunkX, chunkZ, radius, ticking);
        synchronized (this) {
            long held = (long) n * n;
            for (TicketImpl o : active) if (o.owner == owner) held += o.chunkCount();
            if (held > MAX_CHUNKS_PER_OWNER) {
                throw new IllegalStateException("플러그인 " + owner.name() + " 의 청크 티켓이 " + MAX_CHUNKS_PER_OWNER + " 청크를 넘는다(겹침 포함)");
            }
            active.add(t);
        }
        t.loadedFuture = RegionFuture.wrap(w.addPluginTicket(chunkX, chunkZ, radius, ticking));
        if (rejected(owner)) {   // 거는 사이 꺼졌다 — 이미 정리가 지나갔을 수 있으니 직접 놓는다
            t.release();
            throw new IllegalStateException("꺼진 플러그인은 청크 티켓을 걸 수 없다: " + owner.name());
        }
        return t;
    }

    @Override
    public synchronized List<ChunkTicket> tickets(FurnacePlugin owner) {
        List<ChunkTicket> out = new ArrayList<>();
        for (TicketImpl t : active) if (t.owner == owner) out.add(t);
        return out;
    }

    @Override
    public synchronized List<ChunkTicket> tickets(org.domi.furnace.api.world.World world) {
        List<ChunkTicket> out = new ArrayList<>();
        for (TicketImpl t : active) if (t.world == world || t.handle.handle() == server(world)) out.add(t);
        return out;
    }

    @Override
    public synchronized List<ChunkTicket> ticketsAt(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        List<ChunkTicket> out = new ArrayList<>();
        World w = server(world);
        for (TicketImpl t : active) if (t.handle.handle() == w && t.covers(chunkX, chunkZ)) out.add(t);
        return out;
    }

    @Override
    public int releaseAll(FurnacePlugin owner) {
        Objects.requireNonNull(owner, "owner");
        if (!owner.isEnabled()) cleared.add(owner);
        List<TicketImpl> mine;
        synchronized (this) {
            mine = new ArrayList<>();
            for (TicketImpl t : active) if (t.owner == owner) mine.add(t);
        }
        for (TicketImpl t : mine) t.release();
        return mine.size();
    }

    @Override
    public int releaseAll(FurnacePlugin owner, org.domi.furnace.api.world.World world) {
        Objects.requireNonNull(owner, "owner");
        World w = server(world);
        List<TicketImpl> mine;
        synchronized (this) {
            mine = new ArrayList<>();
            for (TicketImpl t : active) if (t.owner == owner && t.handle.handle() == w) mine.add(t);
        }
        for (TicketImpl t : mine) t.release();
        return mine.size();
    }

    private synchronized void forget(TicketImpl t) { active.remove(t); }

    private static final class TicketImpl implements ChunkTicket {
        private final ChunkTicketsImpl impl;
        private final FurnacePlugin owner;
        private final WorldHandle handle;
        private final org.domi.furnace.api.world.World world;
        private final World server;
        private final int cx, cz, radius;
        private final boolean ticking;
        private volatile boolean released;
        private volatile CompletableFuture<Void> loadedFuture = CompletableFuture.completedFuture(null);

        TicketImpl(ChunkTicketsImpl impl, FurnacePlugin owner, WorldHandle handle, World server, int cx, int cz, int radius, boolean ticking) {
            this.impl = impl;
            this.owner = owner;
            this.handle = handle;
            this.world = handle;
            this.server = server;
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.ticking = ticking;
        }

        @Override public FurnacePlugin owner() { return owner; }
        @Override public org.domi.furnace.api.world.World world() { return world; }
        @Override public int centerX() { return cx; }
        @Override public int centerZ() { return cz; }
        @Override public int radius() { return radius; }
        @Override public boolean ticking() { return ticking; }
        @Override public boolean isActive() { return !released && !server.isUnloaded(); }

        @Override
        public CompletableFuture<Void> loaded() { return released ? CompletableFuture.completedFuture(null) : loadedFuture; }

        @Override
        public void release() {
            synchronized (this) {
                if (released) return;
                released = true;
            }
            server.removePluginTicket(cx, cz, radius, ticking);
            impl.forget(this);
        }

        @Override
        public String toString() { return "ChunkTicket[" + owner.name() + " " + world.name() + " (" + cx + "," + cz + ") r=" + radius + (ticking ? " ticking" : "") + "]"; }
    }
}
