package org.bukkit.craftbukkit.entity;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.EntityPdc;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Furnace 엔티티 핸들 위의 Bukkit Entity. 위치·상태는 핸들을 만든 시점 값일 수 있다(Furnace 규칙).
 * CraftBukkit 과 같은 이름·계층(CraftEntity ← CraftLivingEntity ← CraftHumanEntity ← CraftPlayer)이라
 * {@code ((CraftEntity) e).getHandle()} 이 된다(H6). 같은 엔티티(UUID)는 Compat 캐시가 같은 객체를 주고, 새 스냅샷 핸들이 오면 갈아 끼운다.
 */
public class CraftEntity implements Entity {

    /** Furnace 핸들은 만든 순간의 스냅샷이다 — 같은 엔티티의 새 스냅샷이 오면 {@link #furnaceHandle(org.domi.furnace.api.entity.Entity)} 로 바꾼다. */
    private volatile org.domi.furnace.api.entity.Entity handle;
    private volatile net.minecraft.world.entity.Entity nms;

    /** 호환 모듈 전용(Compat 캐시가 만든다). */
    public CraftEntity(org.domi.furnace.api.entity.Entity handle) { this.handle = handle; }

    /** 호환 모듈 전용 — 지금 들고 있는 Furnace 핸들. */
    public org.domi.furnace.api.entity.Entity furnaceHandle() { return handle; }

    /**
     * 호환 모듈 전용 — 같은 엔티티의 더 새 스냅샷으로 바꾼다(UUID 가 같아야 한다). 지금 것보다 먼저 만든 스냅샷({@code snapshotStamp} 이 작다)은
     * 버린다 — 늦게 도착한 옛 이벤트 객체가 새 위치·체력을 되돌리지 않게. 순번을 모르는(0) 핸들은 늘 받는다.
     */
    public synchronized void furnaceHandle(org.domi.furnace.api.entity.Entity fresh) {
        org.domi.furnace.api.entity.Entity cur = handle;
        if (!fresh.uniqueId().equals(cur.uniqueId())) throw new IllegalArgumentException("다른 엔티티의 핸들: " + fresh.uniqueId() + " != " + cur.uniqueId());
        long f = fresh.snapshotStamp(), c = cur.snapshotStamp();
        if (f != 0 && c != 0 && f < c) return;
        handle = fresh;
    }

    /**
     * NMS 엔티티 shim. 같은 CraftEntity 는 같은 shim 을 준다(== 비교·Map 키). shim 의 멤버는 일부만 있다 —
     * 없는 멤버는 호출할 때 NoSuchMethodError/NoSuchFieldError, 있지만 못 하는 것은 UnsupportedNmsCall(docs/feature-parity/compat-h6-2026-11-07.md).
     */
    public net.minecraft.world.entity.Entity getHandle() {
        net.minecraft.world.entity.Entity n = nms;
        if (n == null) {
            synchronized (this) {
                n = nms;
                if (n == null) nms = n = createHandle();
            }
        }
        return n;
    }

    /** 이 종류의 shim 을 만든다(하위 클래스가 더 좁은 shim 을 준다). */
    protected net.minecraft.world.entity.Entity createHandle() { return new net.minecraft.world.entity.Entity(this); }

    @Override
    public UUID getUniqueId() { return handle.uniqueId(); }

    @Override
    public Location getLocation() { return Compat.location(handle.location()); }

    @Override
    public World getWorld() { return Compat.world(handle.location().world()); }

    @Override
    public boolean isValid() { return handle.isValid(); }

    @Override
    public boolean isDead() { return !handle.isValid(); }

    @Override
    public String getCustomName() { return Compat.legacy(handle.customName()); }

    @Override
    public String getTypeKey() { return handle.type(); }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location) { return handle.teleport(Compat.location(location)); }

    @Override
    public boolean teleport(Location location) {
        handle.teleport(Compat.location(location));
        return true;
    }

    @Override
    public org.bukkit.entity.EntityType getType() {
        org.bukkit.entity.EntityType t = org.bukkit.entity.EntityType.fromKey(handle.type());
        if (t == null) throw new UnsupportedFeature("Entity#getType(" + handle.type() + ")", "Bukkit EntityType 열거형에 없는 종류다(플러그인이 더한 엔티티)");
        return t;
    }

    @Override
    public java.util.List<org.bukkit.entity.Entity> getNearbyEntities(double x, double y, double z) {
        var loc = handle.location();
        double r = Math.sqrt(x * x + y * y + z * z);
        var found = handle.world().nearbyEntitiesNow(loc, r);
        java.util.List<org.bukkit.entity.Entity> out = new java.util.ArrayList<>();
        for (var e : found) {
            if (e.uniqueId().equals(handle.uniqueId())) continue;
            var l = e.location();
            if (Math.abs(l.x() - loc.x()) <= x && Math.abs(l.y() - loc.y()) <= y && Math.abs(l.z() - loc.z()) <= z) out.add(Compat.entity(e));
        }
        return out;
    }

    @Override
    public void setCustomName(String name) { throw new UnsupportedFeature("Entity#setCustomName", "엔티티 이름 쓰기 API 가 아직 없다"); }

    @Override
    public org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer() { return new EntityPdc(handle); }

    @Override
    public void remove() { handle.remove(); }

    // ---- CommandSender / Permissible: 플레이어가 아닌 엔티티는 메시지를 받지 않고 권한이 없다(Bukkit 과 같다)

    @Override
    public void sendMessage(String message) {}

    @Override
    public void sendMessage(String... messages) {}

    @Override
    public Server getServer() { return org.bukkit.Bukkit.getServer(); }

    @Override
    public String getName() {
        String custom = getCustomName();
        if (custom != null) return custom;
        String path = handle.type().substring(handle.type().indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.toString();
    }

    @Override
    public boolean isOp() { return false; }

    @Override
    public void setOp(boolean value) { throw new UnsupportedOperationException("엔티티는 OP 가 될 수 없다"); }

    @Override
    public boolean isPermissionSet(String name) { return false; }

    @Override
    public boolean hasPermission(String name) { return false; }

    @Override
    public boolean equals(Object o) { return o instanceof CraftEntity e && e.handle.uniqueId().equals(handle.uniqueId()); }

    @Override
    public int hashCode() { return handle.uniqueId().hashCode(); }

    @Override
    public String toString() { return "CraftEntity{type=" + handle.type() + ",uuid=" + handle.uniqueId() + "}"; }
}
