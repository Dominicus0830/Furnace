package org.domi.furnace.compat.paper;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Furnace 엔티티 핸들 위의 Bukkit Entity. 위치·상태는 핸들을 만든 시점 값일 수 있다(Furnace 규칙). */
class CompatEntity implements Entity {

    private final org.domi.furnace.api.entity.Entity handle;

    CompatEntity(org.domi.furnace.api.entity.Entity handle) { this.handle = handle; }

    org.domi.furnace.api.entity.Entity rawHandle() { return handle; }

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
    public boolean equals(Object o) { return o instanceof CompatEntity e && e.handle.uniqueId().equals(handle.uniqueId()); }

    @Override
    public int hashCode() { return handle.uniqueId().hashCode(); }

    @Override
    public String toString() { return "CraftEntity{type=" + handle.type() + ",uuid=" + handle.uniqueId() + "}"; }
}
