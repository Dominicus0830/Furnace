// GameEvents 의 플러그인 구현 — 구독자가 없으면 이벤트·핸들을 만들지 않고 바로 허용한다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.event.entity.EntityDeathEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.event.inventory.InventoryClickEvent;
import org.domi.furnace.api.event.inventory.InventoryCloseEvent;
import org.domi.furnace.api.event.inventory.InventoryOpenEvent;
import org.domi.furnace.api.event.player.PlayerDropItemEvent;
import org.domi.furnace.api.event.player.PlayerInteractEntityEvent;
import org.domi.furnace.api.event.player.PlayerInteractEvent;
import org.domi.furnace.api.event.player.PlayerMoveEvent;
import org.domi.furnace.api.event.player.PlayerPickupItemEvent;
import org.domi.furnace.api.event.player.PlayerTeleportEvent;
import org.domi.furnace.api.event.world.ChunkLoadEvent;
import org.domi.furnace.api.event.world.ChunkUnloadEvent;
import org.domi.furnace.api.world.Block;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.menu.ClickType;

public final class PluginGameEvents implements GameEvents {

    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};

    private final PluginHost host;

    PluginGameEvents(PluginHost host) { this.host = host; }

    private EventBusImpl bus() { return host.eventsImpl(); }

    private org.domi.furnace.server.world.ItemRegistry items() { return host.core().world().itemRegistry(); }

    @Override
    public float entityDamage(LivingEntity e, String cause, float amount) {
        if (!bus().hasSubscribers(EntityDamageEvent.class)) return amount;
        var ev = bus().call(new EntityDamageEvent(EntityHandle.of(host, e), cause.indexOf(':') < 0 ? "minecraft:" + cause : cause, amount));
        return ev.isCancelled() ? -1f : ev.damage();
    }

    @Override
    public float playerDamage(Player p, String cause, float amount) {
        if (!bus().hasSubscribers(EntityDamageEvent.class)) return amount;
        var ev = bus().call(new EntityDamageEvent(host.player(p), cause.indexOf(':') < 0 ? "minecraft:" + cause : cause, amount));
        return ev.isCancelled() ? -1f : ev.damage();
    }

    @Override
    public void entityDeath(LivingEntity e) {
        if (!bus().hasSubscribers(EntityDeathEvent.class)) return;
        bus().call(new EntityDeathEvent((org.domi.furnace.api.entity.LivingEntity) EntityHandle.of(host, e)));
    }

    @Override
    public boolean entitySpawn(Entity e) {
        if (!bus().hasSubscribers(EntitySpawnEvent.class)) return true;
        return !bus().call(new EntitySpawnEvent(EntityHandle.of(host, e))).isCancelled();
    }

    @Override
    public boolean playerMove(Player p, double fx, double fy, double fz, float fyaw, float fpitch,
                              double tx, double ty, double tz, float tyaw, float tpitch) {
        if (!bus().hasSubscribers(PlayerMoveEvent.class)) return true;
        String w = p.dimension();
        return !bus().call(new PlayerMoveEvent(host.player(p), new Location(w, fx, fy, fz, fyaw, fpitch), new Location(w, tx, ty, tz, tyaw, tpitch))).isCancelled();
    }

    @Override
    public boolean playerTeleport(Player p, double x, double y, double z) {
        if (!bus().hasSubscribers(PlayerTeleportEvent.class)) return true;
        String w = p.dimension();
        return !bus().call(new PlayerTeleportEvent(host.player(p), new Location(w, p.x(), p.y(), p.z(), p.yaw(), p.pitch()),
                new Location(w, x, y, z, p.yaw(), p.pitch()))).isCancelled();
    }

    @Override
    public boolean playerInteract(Player p, int hand, int[] blockPos, int blockState, int face, ItemStack inHand) {
        if (!bus().hasSubscribers(PlayerInteractEvent.class)) return true;
        var reg = host.core().world().blockRegistry();
        Block block = blockPos == null ? null : new Block(p.dimension(), new BlockPos(blockPos[0], blockPos[1], blockPos[2]), ApiConvert.block(blockState, reg));
        var action = blockPos == null ? PlayerInteractEvent.Action.RIGHT_CLICK_AIR : PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK;
        String f = blockPos == null || face < 0 || face >= FACES.length ? null : FACES[face];
        return !bus().call(new PlayerInteractEvent(host.player(p), action, ApiConvert.item(inHand, items()), block, f, hand == 0)).isCancelled();
    }

    @Override
    public boolean wantsInteract() { return bus().hasSubscribers(PlayerInteractEvent.class); }

    @Override
    public boolean playerLeftClick(Player p, int[] blockPos, int blockState, int face, ItemStack inHand) {
        if (!bus().hasSubscribers(PlayerInteractEvent.class)) return true;
        Block block = blockPos == null ? null
                : new Block(p.dimension(), new BlockPos(blockPos[0], blockPos[1], blockPos[2]), ApiConvert.block(blockState, host.core().world().blockRegistry()));
        var action = blockPos == null ? PlayerInteractEvent.Action.LEFT_CLICK_AIR : PlayerInteractEvent.Action.LEFT_CLICK_BLOCK;
        String f = blockPos == null || face < 0 || face >= FACES.length ? null : FACES[face];
        return !bus().call(new PlayerInteractEvent(host.player(p), action, ApiConvert.item(inHand, items()), block, f, true)).isCancelled();
    }

    @Override
    public boolean playerInteractEntity(Player p, Entity target, int hand, ItemStack inHand) {
        if (!bus().hasSubscribers(PlayerInteractEntityEvent.class)) return true;
        return !bus().call(new PlayerInteractEntityEvent(host.player(p), EntityHandle.of(host, target), ApiConvert.item(inHand, items()), hand == 0)).isCancelled();
    }

    @Override
    public boolean playerDrop(Player p, ItemStack stack) {
        if (!bus().hasSubscribers(PlayerDropItemEvent.class)) return true;
        return !bus().call(new PlayerDropItemEvent(host.player(p), ApiConvert.item(stack, items()))).isCancelled();
    }

    @Override
    public boolean playerPickup(Player p, ItemStack stack) {
        if (!bus().hasSubscribers(PlayerPickupItemEvent.class)) return true;
        return !bus().call(new PlayerPickupItemEvent(host.player(p), ApiConvert.item(stack, items()))).isCancelled();
    }

    @Override
    public boolean inventoryClick(Player p, AbstractContainerMenu menu, int slot, int button, ClickType type) {
        if (!bus().hasSubscribers(InventoryClickEvent.class)) return true;
        var cursor = ApiConvert.item(menu.getCarried(), items());
        return !bus().call(new InventoryClickEvent(host.player(p), ApiConvert.snapshot(menu, items()), slot, button,
                type.name().toLowerCase(java.util.Locale.ROOT), cursor)).isCancelled();
    }

    @Override
    public boolean inventoryOpen(Player p, AbstractContainerMenu menu) {
        if (!bus().hasSubscribers(InventoryOpenEvent.class)) return true;
        return !bus().call(new InventoryOpenEvent(host.player(p), ApiConvert.snapshot(menu, items()))).isCancelled();
    }

    @Override
    public void inventoryClose(Player p, AbstractContainerMenu menu) {
        if (!bus().hasSubscribers(InventoryCloseEvent.class)) return;
        bus().call(new InventoryCloseEvent(host.player(p), ApiConvert.snapshot(menu, items())));
    }

    @Override
    public void chunkLoad(String dimension, int cx, int cz) {
        if (!bus().hasSubscribers(ChunkLoadEvent.class)) return;
        bus().call(new ChunkLoadEvent(dimension, cx, cz));
    }

    @Override
    public void chunkUnload(String dimension, int cx, int cz) {
        if (!bus().hasSubscribers(ChunkUnloadEvent.class)) return;
        bus().call(new ChunkUnloadEvent(dimension, cx, cz));
    }
}
