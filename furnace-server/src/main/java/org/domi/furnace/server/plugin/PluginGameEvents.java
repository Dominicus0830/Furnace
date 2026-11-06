// GameEvents 의 플러그인 구현 — 구독자가 없으면 이벤트·핸들을 만들지 않고 바로 허용한다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.event.entity.DamageRules;
import org.domi.furnace.api.event.entity.EntityDamageCalculateEvent;
import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.event.entity.EntityDeathEvent;
import org.domi.furnace.api.event.block.BlockExplodeEvent;
import org.domi.furnace.api.event.entity.EntityBreedEvent;
import org.domi.furnace.api.event.entity.EntityExplodeEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.event.entity.EntityTameEvent;
import org.domi.furnace.api.event.entity.EntityTargetEvent;
import org.domi.furnace.api.event.entity.ItemSpawnEvent;
import org.domi.furnace.api.event.entity.ProjectileHitEvent;
import org.domi.furnace.api.event.entity.ProjectileLaunchEvent;
import org.domi.furnace.api.event.player.PlayerAdvancementDoneEvent;
import org.domi.furnace.api.event.player.PlayerBedEnterEvent;
import org.domi.furnace.api.event.player.PlayerBedLeaveEvent;
import org.domi.furnace.api.event.player.PlayerChangedWorldEvent;
import org.domi.furnace.api.event.player.PlayerExpChangeEvent;
import org.domi.furnace.api.event.player.PlayerItemConsumeEvent;
import org.domi.furnace.api.event.player.PlayerItemHeldEvent;
import org.domi.furnace.api.event.player.PlayerKickEvent;
import org.domi.furnace.api.event.player.PlayerLevelChangeEvent;
import org.domi.furnace.api.event.player.PlayerSwapHandItemsEvent;
import org.domi.furnace.api.event.player.PlayerToggleFlightEvent;
import org.domi.furnace.api.event.player.PlayerToggleSneakEvent;
import org.domi.furnace.api.event.player.PlayerToggleSprintEvent;
import org.domi.furnace.api.event.server.ServerListPingEvent;
import org.domi.furnace.api.event.block.BlockBurnEvent;
import org.domi.furnace.api.event.block.BlockFromToEvent;
import org.domi.furnace.api.event.block.BlockGrowEvent;
import org.domi.furnace.api.event.block.BlockIgniteEvent;
import org.domi.furnace.api.event.block.BlockPhysicsEvent;
import org.domi.furnace.api.event.block.BlockPistonEvent;
import org.domi.furnace.api.event.block.BlockPistonExtendEvent;
import org.domi.furnace.api.event.block.BlockPistonRetractEvent;
import org.domi.furnace.api.event.block.BlockRedstoneEvent;
import org.domi.furnace.api.event.block.LeavesDecayEvent;
import org.domi.furnace.api.event.inventory.CraftItemEvent;
import org.domi.furnace.api.event.inventory.FurnaceBurnEvent;
import org.domi.furnace.api.event.inventory.FurnaceSmeltEvent;
import org.domi.furnace.api.event.inventory.InventoryClickEvent;
import org.domi.furnace.api.event.inventory.InventoryMoveItemEvent;
import org.domi.furnace.api.event.inventory.PrepareItemCraftEvent;
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
    public boolean wantsDamageCalc() { return bus().hasSubscribers(EntityDamageCalculateEvent.class); }

    @Override
    public EntityDamageCalculateEvent entityDamageCalc(LivingEntity e, String cause, org.domi.furnace.server.entity.Rider causing,
                                                       org.domi.furnace.server.entity.Rider direct, float base, float absorption, DamageRules rules) {
        return bus().call(new EntityDamageCalculateEvent(EntityHandle.of(host, e), causeKey(cause), riderHandle(causing), riderHandle(direct), base, absorption, rules));
    }

    @Override
    public EntityDamageCalculateEvent playerDamageCalc(Player p, String cause, org.domi.furnace.server.entity.Rider causing,
                                                       org.domi.furnace.server.entity.Rider direct, float base, float absorption, DamageRules rules) {
        return bus().call(new EntityDamageCalculateEvent(host.player(p), causeKey(cause), riderHandle(causing), riderHandle(direct), base, absorption, rules));
    }

    private static String causeKey(String cause) { return cause.indexOf(':') < 0 ? "minecraft:" + cause : cause; }

    private org.domi.furnace.api.entity.Entity riderHandle(org.domi.furnace.server.entity.Rider r) {
        return r instanceof Player p ? host.player(p) : r instanceof Entity e ? EntityHandle.of(host, e) : null;
    }

    @Override
    public void entityDeath(LivingEntity e) {
        if (!bus().hasSubscribers(EntityDeathEvent.class)) return;
        bus().call(new EntityDeathEvent((org.domi.furnace.api.entity.LivingEntity) EntityHandle.of(host, e)));
    }

    @Override
    public boolean entitySpawn(Entity e) {
        // Bukkit 처럼 투사체·떨어진 아이템은 하위 이벤트로(EntitySpawnEvent 구독자도 받는다)
        if (e instanceof org.domi.furnace.server.entity.Projectile) {
            if (!bus().hasSubscribers(ProjectileLaunchEvent.class)) return true;
            return !bus().call(new ProjectileLaunchEvent(EntityHandle.of(host, e))).isCancelled();
        }
        if (e instanceof org.domi.furnace.server.entity.ItemEntity) {
            if (!bus().hasSubscribers(ItemSpawnEvent.class)) return true;
            return !bus().call(new ItemSpawnEvent(EntityHandle.of(host, e))).isCancelled();
        }
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
        String recipe = craftRecipe(menu, slot, type);
        if (recipe != null) {   // 제작 결과 칸을 꺼내는 클릭은 하위 CraftItemEvent(InventoryClickEvent 구독자도 받는다)
            if (!bus().hasSubscribers(CraftItemEvent.class)) return true;
            var cursor = ApiConvert.item(menu.getCarried(), items());
            return !bus().call(new CraftItemEvent(host.player(p), ApiConvert.snapshot(menu, items()), slot, button,
                    type.name().toLowerCase(java.util.Locale.ROOT), cursor, recipe.isEmpty() ? null : recipe)).isCancelled();
        }
        if (!bus().hasSubscribers(InventoryClickEvent.class)) return true;
        var cursor = ApiConvert.item(menu.getCarried(), items());
        return !bus().call(new InventoryClickEvent(host.player(p), ApiConvert.snapshot(menu, items()), slot, button,
                type.name().toLowerCase(java.util.Locale.ROOT), cursor)).isCancelled();
    }

    /** 작업대·인벤토리 2×2 결과 칸에서 꺼내는 클릭이면 레시피 id(모르면 ""), 아니면 null. */
    private static String craftRecipe(AbstractContainerMenu menu, int slot, ClickType type) {
        if (slot != 0 || type == ClickType.QUICK_CRAFT || type == ClickType.PICKUP_ALL) return null;
        if (!(menu instanceof org.domi.furnace.server.menu.CraftingMenu) && !(menu instanceof org.domi.furnace.server.menu.InventoryMenu)) return null;
        var s = menu.getSlot(0);
        if (!(s instanceof org.domi.furnace.server.menu.ResultSlot) || !s.hasItem()) return null;
        if (s.container instanceof org.domi.furnace.server.menu.ResultContainer rc && rc.recipeUsed() != null) return rc.recipeUsed().id().toString();
        return "";
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

    // ------------------------------------------------------------------ 3단계: 플레이어

    @Override
    public boolean has(Class<? extends org.domi.furnace.api.event.Event> type) { return bus().hasSubscribers(type); }

    @Override
    public boolean playerToggle(Player p, int kind, boolean on) {
        var type = switch (kind) {
            case 0 -> PlayerToggleSneakEvent.class;
            case 1 -> PlayerToggleSprintEvent.class;
            default -> PlayerToggleFlightEvent.class;
        };
        if (!bus().hasSubscribers(type)) return true;
        var h = host.player(p);
        org.domi.furnace.api.event.Event ev = switch (kind) {
            case 0 -> new PlayerToggleSneakEvent(h, on);
            case 1 -> new PlayerToggleSprintEvent(h, on);
            default -> new PlayerToggleFlightEvent(h, on);
        };
        return !((org.domi.furnace.api.event.Cancellable) bus().call(ev)).isCancelled();
    }

    @Override
    public boolean playerItemHeld(Player p, int previous, int next) {
        if (!bus().hasSubscribers(PlayerItemHeldEvent.class)) return true;
        return !bus().call(new PlayerItemHeldEvent(host.player(p), previous, next)).isCancelled();
    }

    @Override
    public ItemStack[] playerSwapHands(Player p, ItemStack newMain, ItemStack newOff) {
        if (!bus().hasSubscribers(PlayerSwapHandItemsEvent.class)) return new ItemStack[] {newMain, newOff};
        var apiMain = ApiConvert.item(newMain, items());
        var apiOff = ApiConvert.item(newOff, items());
        var ev = bus().call(new PlayerSwapHandItemsEvent(host.player(p), apiMain, apiOff));
        if (ev.isCancelled()) return null;
        // 바꾸지 않은 손은 원래 스택 그대로(부품 정보 손실 없이)
        return new ItemStack[] {fromApi(ev.mainHandItem(), apiMain, newMain), fromApi(ev.offHandItem(), apiOff, newOff)};
    }

    /** 플러그인이 바꾼 아이템을 서버 스택으로(바꾸지 않았으면 원래 스택, null 은 빈 칸, 알 수 없는 아이템이면 원래 스택). */
    private ItemStack fromApi(org.domi.furnace.api.item.ItemStack now, org.domi.furnace.api.item.ItemStack given, ItemStack original) {
        if (now == given) return original;
        if (now == null) return ItemStack.EMPTY;
        try {
            return ApiConvert.item(now, items());
        } catch (IllegalArgumentException e) {
            org.slf4j.LoggerFactory.getLogger(PluginGameEvents.class).warn("플러그인이 준 아이템을 쓸 수 없어 원래 아이템을 둔다: {}", e.getMessage());
            return original;
        }
    }

    @Override
    public boolean playerConsume(Player p, ItemStack item, int hand) {
        if (!bus().hasSubscribers(PlayerItemConsumeEvent.class)) return true;
        return !bus().call(new PlayerItemConsumeEvent(host.player(p), ApiConvert.item(item, items()), hand == 0)).isCancelled();
    }

    @Override
    public void playerChangedWorld(Player p, String from) {
        if (!bus().hasSubscribers(PlayerChangedWorldEvent.class)) return;
        bus().call(new PlayerChangedWorldEvent(host.player(p), from));
    }

    @Override
    public org.domi.furnace.api.text.Text playerKick(Player p, org.domi.furnace.api.text.Text reason) {
        if (!bus().hasSubscribers(PlayerKickEvent.class)) return reason;
        var ev = bus().call(new PlayerKickEvent(host.player(p), reason));
        return ev.isCancelled() ? null : ev.reason();
    }

    @Override
    public int playerExpChange(Player p, int amount) {
        if (!bus().hasSubscribers(PlayerExpChangeEvent.class)) return amount;
        return bus().call(new PlayerExpChangeEvent(host.player(p), amount)).amount();
    }

    @Override
    public void playerLevelChange(Player p, int oldLevel, int newLevel) {
        if (!bus().hasSubscribers(PlayerLevelChangeEvent.class)) return;
        bus().call(new PlayerLevelChangeEvent(host.player(p), oldLevel, newLevel));
    }

    @Override
    public org.domi.furnace.api.text.Text playerAdvancementDone(Player p, String advancement, org.domi.furnace.api.text.Text message) {
        if (!bus().hasSubscribers(PlayerAdvancementDoneEvent.class)) return message;
        return bus().call(new PlayerAdvancementDoneEvent(host.player(p), advancement, message)).message();
    }

    @Override
    public boolean playerBedEnter(Player p, String dimension, int x, int y, int z, int bedState) {
        if (!bus().hasSubscribers(PlayerBedEnterEvent.class)) return true;
        var bed = new Block(dimension, new BlockPos(x, y, z), ApiConvert.block(bedState, host.core().world().blockRegistry()));
        return !bus().call(new PlayerBedEnterEvent(host.player(p), bed)).isCancelled();
    }

    @Override
    public void playerBedLeave(Player p, String dimension, org.domi.furnace.server.block.Pos pos) {
        if (!bus().hasSubscribers(PlayerBedLeaveEvent.class)) return;
        bus().call(new PlayerBedLeaveEvent(host.player(p), dimension, pos == null ? null : new BlockPos(pos.x(), pos.y(), pos.z())));
    }

    // ------------------------------------------------------------------ 3단계: 엔티티·블록·서버

    @Override
    public boolean explode(String dimension, Entity source, Player sourcePlayer, double x, double y, double z, int blockState,
                           java.util.List<org.domi.furnace.server.block.Pos> blocks) {
        boolean byEntity = source != null || sourcePlayer != null;
        Class<? extends org.domi.furnace.api.event.Event> type = byEntity ? EntityExplodeEvent.class : BlockExplodeEvent.class;
        if (!bus().hasSubscribers(type)) return true;
        java.util.List<BlockPos> list = new java.util.ArrayList<>(blocks.size());
        for (var b : blocks) list.add(new BlockPos(b.x(), b.y(), b.z()));
        boolean cancelled;
        if (byEntity) {
            org.domi.furnace.api.entity.Entity who = source != null ? EntityHandle.of(host, source) : host.player(sourcePlayer);
            cancelled = bus().call(new EntityExplodeEvent(who, new Location(dimension, x, y, z, 0f, 0f), list)).isCancelled();
        } else {
            var center = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            String blockType = ApiConvert.block(blockState, host.core().world().blockRegistry()).type();
            cancelled = bus().call(new BlockExplodeEvent(dimension, center, blockType, list)).isCancelled();
        }
        if (cancelled) return false;
        blocks.clear();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        for (BlockPos b : list) {
            if (b != null && seen.add(b)) blocks.add(new org.domi.furnace.server.block.Pos(b.x(), b.y(), b.z()));
        }
        return true;
    }

    @Override
    public boolean projectileHit(Entity projectile, Entity hitEntity, Player hitPlayer, org.domi.furnace.server.block.Pos hitBlock, int blockState, int face) {
        if (!bus().hasSubscribers(ProjectileHitEvent.class)) return true;
        org.domi.furnace.api.entity.Entity target = hitPlayer != null ? host.player(hitPlayer) : hitEntity != null ? EntityHandle.of(host, hitEntity) : null;
        Block block = hitBlock == null ? null : new Block(projectile.dimensionKey(), new BlockPos(hitBlock.x(), hitBlock.y(), hitBlock.z()),
                ApiConvert.block(blockState, host.core().world().blockRegistry()));
        String f = hitBlock == null || face < 0 || face >= FACES.length ? null : FACES[face];
        return !bus().call(new ProjectileHitEvent(EntityHandle.of(host, projectile), target, block, f)).isCancelled();
    }

    @Override
    public boolean entityTame(Entity e, Player owner) {
        if (!bus().hasSubscribers(EntityTameEvent.class)) return true;
        return !bus().call(new EntityTameEvent(EntityHandle.of(host, e), host.player(owner))).isCancelled();
    }

    @Override
    public boolean entityBreed(Entity child, LivingEntity mother, LivingEntity father, Player breeder) {
        if (!bus().hasSubscribers(EntityBreedEvent.class)) return true;
        return !bus().call(new EntityBreedEvent(EntityHandle.of(host, child), (org.domi.furnace.api.entity.LivingEntity) EntityHandle.of(host, mother),
                (org.domi.furnace.api.entity.LivingEntity) EntityHandle.of(host, father), breeder == null ? null : host.player(breeder))).isCancelled();
    }

    @Override
    public boolean entityTarget(Entity e, Entity targetEntity, Player targetPlayer) {
        if (!bus().hasSubscribers(EntityTargetEvent.class)) return true;
        org.domi.furnace.api.entity.Entity t = targetPlayer != null ? host.player(targetPlayer) : targetEntity != null ? EntityHandle.of(host, targetEntity) : null;
        return !bus().call(new EntityTargetEvent(EntityHandle.of(host, e), t)).isCancelled();
    }

    @Override
    public StatusOverride serverListPing(java.net.InetAddress address, String motd, int online, int max, boolean hidePlayers) {
        if (!bus().hasSubscribers(ServerListPingEvent.class)) return null;
        var motdText = org.domi.furnace.api.text.Text.text(motd);
        var ev = bus().call(new ServerListPingEvent(address, motdText, online, max, hidePlayers));
        if (ev.motd() == motdText && ev.numPlayers() == online && ev.maxPlayers() == max && ev.hidePlayers() == hidePlayers) return null;
        return new StatusOverride(ev.motd() == motdText ? null : ev.motd().toJson(), ev.numPlayers(), ev.maxPlayers(), ev.hidePlayers());
    }

    // ------------------------------------------------------------------ 3단계 둘째 묶음: 블록

    private org.domi.furnace.api.world.BlockData data(int state) { return ApiConvert.block(state, host.core().world().blockRegistry()); }

    private static BlockPos bp(org.domi.furnace.server.block.Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }

    @Override
    public boolean blockPhysics(String dimension, org.domi.furnace.server.block.Pos pos, int state, org.domi.furnace.server.block.Pos sourcePos, int sourceBlock) {
        if (!bus().hasSubscribers(BlockPhysicsEvent.class)) return true;
        String changed = data(org.domi.furnace.server.block.B.defaultState(sourceBlock)).type();
        return !bus().call(new BlockPhysicsEvent(dimension, bp(pos), data(state), bp(sourcePos), changed)).isCancelled();
    }

    @Override
    public int blockRedstone(String dimension, org.domi.furnace.server.block.Pos pos, int state, int oldCurrent, int newCurrent) {
        if (!bus().hasSubscribers(BlockRedstoneEvent.class)) return newCurrent;
        return bus().call(new BlockRedstoneEvent(dimension, bp(pos), data(state).type(), oldCurrent, newCurrent)).newCurrent();
    }

    @Override
    public boolean piston(String dimension, org.domi.furnace.server.block.Pos pos, int dir, boolean sticky, boolean extending,
                          java.util.List<org.domi.furnace.server.block.Pos> blocks) {
        Class<? extends BlockPistonEvent> type = extending ? BlockPistonExtendEvent.class : BlockPistonRetractEvent.class;
        if (!bus().hasSubscribers(type)) return true;
        java.util.List<BlockPos> list = new java.util.ArrayList<>(blocks.size());
        for (var b : blocks) list.add(bp(b));
        String face = org.domi.furnace.server.block.Dir.NAME[dir];
        BlockPistonEvent ev = extending ? new BlockPistonExtendEvent(dimension, bp(pos), face, sticky, list)
                : new BlockPistonRetractEvent(dimension, bp(pos), face, sticky, list);
        return !bus().call(ev).isCancelled();
    }

    @Override
    public boolean blockFromTo(String dimension, org.domi.furnace.server.block.Pos pos, int state, org.domi.furnace.server.block.Pos to, int dir) {
        if (!bus().hasSubscribers(BlockFromToEvent.class)) return true;
        return !bus().call(new BlockFromToEvent(dimension, bp(pos), data(state).type(), bp(to), org.domi.furnace.server.block.Dir.NAME[dir])).isCancelled();
    }

    @Override
    public boolean blockGrow(String dimension, org.domi.furnace.server.block.Pos pos, int oldState, int newState) {
        if (!bus().hasSubscribers(BlockGrowEvent.class)) return true;
        return !bus().call(new BlockGrowEvent(dimension, bp(pos), data(oldState), data(newState))).isCancelled();
    }

    @Override
    public boolean blockBurn(String dimension, org.domi.furnace.server.block.Pos pos, int state, org.domi.furnace.server.block.Pos fire) {
        if (!bus().hasSubscribers(BlockBurnEvent.class)) return true;
        return !bus().call(new BlockBurnEvent(dimension, bp(pos), data(state).type(), bp(fire))).isCancelled();
    }

    private static final BlockIgniteEvent.Cause[] IGNITE_CAUSES = {BlockIgniteEvent.Cause.SPREAD, BlockIgniteEvent.Cause.LAVA,
            BlockIgniteEvent.Cause.FLINT_AND_STEEL, BlockIgniteEvent.Cause.FIREBALL};

    @Override
    public boolean blockIgnite(String dimension, org.domi.furnace.server.block.Pos pos, int cause, org.domi.furnace.server.block.Pos ignitingPos, Player player) {
        if (!bus().hasSubscribers(BlockIgniteEvent.class)) return true;
        return !bus().call(new BlockIgniteEvent(dimension, bp(pos), IGNITE_CAUSES[cause], ignitingPos == null ? null : bp(ignitingPos),
                player == null ? null : host.player(player))).isCancelled();
    }

    @Override
    public boolean leavesDecay(String dimension, org.domi.furnace.server.block.Pos pos, int state) {
        if (!bus().hasSubscribers(LeavesDecayEvent.class)) return true;
        return !bus().call(new LeavesDecayEvent(dimension, bp(pos), data(state))).isCancelled();
    }

    // ------------------------------------------------------------------ 3단계 둘째 묶음: 인벤토리

    @Override
    public ItemStack prepareCraft(Player p, AbstractContainerMenu menu, String recipe, ItemStack result) {
        if (!bus().hasSubscribers(PrepareItemCraftEvent.class)) return result;
        var given = ApiConvert.item(result, items());
        var ev = bus().call(new PrepareItemCraftEvent(host.player(p), ApiConvert.snapshot(menu, items()), recipe, given));
        return fromApi(ev.result(), given, result);
    }

    @Override
    public FurnaceBurn furnaceBurn(String dimension, org.domi.furnace.server.block.Pos pos, int state, ItemStack fuel, int burnTime) {
        if (!bus().hasSubscribers(FurnaceBurnEvent.class)) return new FurnaceBurn(burnTime, true);
        var ev = bus().call(new FurnaceBurnEvent(dimension, bp(pos), data(state).type(), ApiConvert.item(fuel, items()), burnTime));
        return ev.isCancelled() ? null : new FurnaceBurn(ev.burnTime(), ev.isBurning());
    }

    @Override
    public ItemStack furnaceSmelt(String dimension, org.domi.furnace.server.block.Pos pos, int state, ItemStack source, ItemStack result) {
        if (!bus().hasSubscribers(FurnaceSmeltEvent.class)) return result;
        var given = ApiConvert.item(result, items());
        var ev = bus().call(new FurnaceSmeltEvent(dimension, bp(pos), data(state).type(), ApiConvert.item(source, items()), given));
        return ev.isCancelled() ? null : fromApi(ev.result(), given, result);
    }

    @Override
    public ItemStack inventoryMove(String dimension, org.domi.furnace.server.block.entity.HopperBlockEntity.Inv source, double sx, double sy, double sz,
                                   org.domi.furnace.server.block.entity.HopperBlockEntity.Inv destination, double dx, double dy, double dz,
                                   ItemStack item, boolean initiatorIsSource) {
        if (!bus().hasSubscribers(InventoryMoveItemEvent.class)) return item;
        var given = ApiConvert.item(item, items());
        var ev = bus().call(new InventoryMoveItemEvent(snapshot(source), new Location(dimension, sx, sy, sz), snapshot(destination),
                new Location(dimension, dx, dy, dz), given, initiatorIsSource));
        return ev.isCancelled() ? null : fromApi(ev.item(), given, item);
    }

    private org.domi.furnace.api.inventory.InventorySnapshot snapshot(org.domi.furnace.server.block.entity.HopperBlockEntity.Inv inv) {
        java.util.List<org.domi.furnace.api.item.ItemStack> list = new java.util.ArrayList<>(inv.size());
        for (int i = 0; i < inv.size(); i++) list.add(ApiConvert.item(inv.get(i), items()));
        return new org.domi.furnace.api.inventory.InventorySnapshot(inv.typeKey(), list);
    }
}
