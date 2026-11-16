package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.world.entity.Entity;
import org.domi.furnace.compat.paper.Compat;

/**
 * 서버 월드 shim(Mojang 이름, H6 최소 표면): {@code ((CraftWorld) w).getHandle()}. 같은 CraftWorld 는 같은 shim 이다.
 * 블록 읽기·쓰기·청크 얻기(N1·N2a)는 {@link Level} 에 있다.
 */
public class ServerLevel extends Level {

    private final ServerChunkCache chunkSource = new ServerChunkCache(this);

    /** 호환 모듈 전용(CraftWorld 가 만든다). */
    public ServerLevel(CraftWorld world) { super(world); }

    /** 청크 공급원(N2a) — 서버 청크의 보기를 준다. */
    @Override
    public ServerChunkCache getChunkSource() { return chunkSource; }

    /** 바닐라 WorldGenLevel#getLevel: 자기 자신. */
    public ServerLevel getLevel() { return this; }

    /** 바닐라 ServerLevelAccessor#getMinecraftWorld: 자기 자신. */
    public ServerLevel getMinecraftWorld() { return this; }

    public long getSeed() { return getWorld().getSeed(); }

    public MinecraftServer getServer() { return MinecraftServer.getServer(); }

    /** 대기 한도(ms) — 네트워크·비동기 스레드가 id 로 엔티티를 찾을 때 소유 region 의 응답을 기다리는 시간. */
    private static final long LOOKUP_WAIT_MILLIS = Long.getLong("furnace.compat.entityLookupWaitMillis", 1_000L);

    @Override
    public Entity getEntity(int id) {
        var w = getWorld().furnaceHandle();
        java.util.Optional<org.domi.furnace.api.entity.Entity> found;
        try {
            found = w.entityByIdNow(id);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException notOwner) {
            // Paper·Folia 의 EntityLookup#get(int) 는 어느 스레드에서나 기다림 없이 돌려준다 — 그 엔티티의 Bukkit 객체를 이미 알면(UUID 캐시)
            // 소유 region 을 기다리지 않고 그것(마지막 스냅샷)을 준다. 상태를 읽을 때의 스레드 검사는 지금처럼 그 호출이 한다.
            var uuid = w.entityUuidById(id);
            if (uuid.isEmpty()) return null;   // 그 사이 사라졌다
            var cached = Compat.cachedEntity(uuid.get());
            if (cached != null) return cached.getHandle();
            // 처음 보는 엔티티: 스냅샷은 그 엔티티를 가진 region 이 떠야 한다. 틱 스레드가 아닌 쪽만 기다린다
            if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) throw notOwner;
            try {
                found = w.entityById(id).get(LOOKUP_WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw notOwner;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException late) {
                throw notOwner;
            }
        }
        return found.map(e -> ((org.bukkit.craftbukkit.entity.CraftEntity) Compat.entity(e)).getHandle()).orElse(null);
    }

    /**
     * 바닐라 ServerLevel#getEntity(UUID): 이 월드의 그 엔티티(플레이어 포함), 없으면 null. 스레드 규칙은 {@link #getEntity(int)} 와 같다
     * (그 엔티티의 Bukkit 객체를 이미 알면 어느 스레드에서나 그 스냅샷, 처음 보는 것은 소유 region 스레드이거나 틱 스레드가 아닌 스레드가 최대 1초 기다린다).
     */
    public Entity getEntity(java.util.UUID uuid) {
        var w = getWorld().furnaceHandle();
        java.util.Optional<org.domi.furnace.api.entity.Entity> found;
        try {
            found = w.entityByUuidNow(uuid);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException notOwner) {
            var cached = Compat.cachedEntity(uuid);
            if (cached != null) return cached.getWorld() == getWorld() ? cached.getHandle() : null;
            if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) throw notOwner;
            try {
                found = w.entityByUuid(uuid).get(LOOKUP_WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw notOwner;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException late) {
                throw notOwner;
            }
        }
        return found.map(e -> ((org.bukkit.craftbukkit.entity.CraftEntity) Compat.entity(e)).getHandle()).orElse(null);
    }

    /** 이 월드에 있는 접속 플레이어. */
    public List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (var p : getWorld().getPlayers()) out.add(((CraftPlayer) p).getHandle());
        return out;
    }
}
