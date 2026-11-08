// Bukkit 이벤트 ↔ Furnace 이벤트 짝(1차 24종 + 인벤토리 창·사망). 새 짝을 더할 때는 Bukkit 이벤트 클래스를 org.bukkit.event 아래에 두고 여기에 reg 한 줄을 더한다.
// 원칙: Furnace 이벤트가 알리지 않는 값은 지어내지 않는다 — 읽기는 정직한 기본값(예: TeleportCause.UNKNOWN) 또는 UnsupportedFeature,
// 쓰기는 UnsupportedFeature. 처리기가 바꾼 값은 Furnace 이벤트에 되돌려 쓴다(바뀌지 않았으면 손대지 않아 서식이 깎이지 않는다).
package org.domi.furnace.compat.paper;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.text.Text;

final class Mappings {

    private Mappings() {}

    static void registerAll() {
        registerPlayer();
        registerWorld();
        registerOther();
        registerInventory();
        registerEntityInteract();
    }

    private static final java.util.Map<Object, String> FORMAT_BASE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static Player p(org.domi.furnace.api.entity.Player f) { return Compat.player(f); }

    /** 전달 직전: Furnace 의 현재 문구가 Bukkit 이벤트의 것과 다르면 Bukkit 쪽을 맞춘다. */
    private static void pullText(Text current, String bukkitLegacy, Consumer<String> setter) {
        String cur = Compat.legacy(current);
        if (!Objects.equals(cur, bukkitLegacy)) setter.accept(cur);
    }

    private static void pushText(String newLegacy, Text current, Consumer<Text> setter) {
        if (!Objects.equals(newLegacy, Compat.legacy(current))) setter.accept(Compat.text(newLegacy));
    }

    private static void registerPlayer() {
        // 두 Bukkit 하위 이벤트가 PlayerChannelEvent 의 처리기 목록을 나눠 쓰므로 짝도 하나다. Bukkit 이 받지 않는 이름(MAX_CHANNEL_SIZE 초과)은 건너뛴다.
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerChannelEvent.class, org.bukkit.event.player.PlayerChannelEvent.class,
                f -> {
                    String name = CompatMessenger.bukkitName(f.channel());
                    if (name == null) return null;
                    return f.action() == org.domi.furnace.api.event.player.PlayerChannelEvent.Action.REGISTER
                            ? new org.bukkit.event.player.PlayerRegisterChannelEvent(p(f.player()), name)
                            : new org.bukkit.event.player.PlayerUnregisterChannelEvent(p(f.player()), name);
                },
                (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerJoinEvent.class, org.bukkit.event.player.PlayerJoinEvent.class,
                f -> new org.bukkit.event.player.PlayerJoinEvent(p(f.player()), Compat.legacy(f.joinMessage())),
                (f, b) -> pushText(b.getJoinMessage(), f.joinMessage(), t -> f.joinMessage(t)),
                (f, b) -> pullText(f.joinMessage(), b.getJoinMessage(), b::setJoinMessage));
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerQuitEvent.class, org.bukkit.event.player.PlayerQuitEvent.class,
                f -> new org.bukkit.event.player.PlayerQuitEvent(p(f.player()), Compat.legacy(f.quitMessage())),
                (f, b) -> pushText(b.getQuitMessage(), f.quitMessage(), t -> f.quitMessage(t)),
                (f, b) -> pullText(f.quitMessage(), b.getQuitMessage(), b::setQuitMessage));
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerKickEvent.class, org.bukkit.event.player.PlayerKickEvent.class,
                f -> new org.bukkit.event.player.PlayerKickEvent(p(f.player()), Compat.legacy(f.reason()), defaultLeaveMessage(f.player())),
                (f, b) -> {
                    pushText(b.getReason(), f.reason(), t -> f.setReason(t == null ? Text.empty() : t));
                    // Paper 1.21.1: Fix-kick-event-leave-message-not-being-sent — 바뀐 퇴장 문구는 이어지는 PlayerQuitEvent 의 처음 메시지가 된다(null·빈 문자열 = 알림 없음)
                    String lm = b.getLeaveMessage();
                    if (!Objects.equals(lm, defaultLeaveMessage(f.player()))) f.leaveMessage(lm == null || lm.isEmpty() ? null : Compat.text(lm));
                },
                (f, b) -> pullText(f.reason(), b.getReason(), b::setReason));
        EventBridge.reg(org.domi.furnace.api.event.player.AsyncChatEvent.class, org.bukkit.event.player.AsyncPlayerChatEvent.class,
                // Paper 1.21.1: Adventure 패치 ChatProcessor — 받는 사람 집합은 바꿀 수 있고(viewersFromLegacy) 서식의 %1$s 는 Player#getDisplayName()
                f -> new org.bukkit.event.player.AsyncPlayerChatEvent(f.isAsync(), p(f.player()), Compat.legacy(f.message()), new MappedPlayers(f::recipients)),
                (f, b) -> {
                    pushText(b.getMessage(), f.message(), t -> f.message(t == null ? Text.empty() : t));
                    String fmt = b.getFormat();
                    if (!Objects.equals(fmt, FORMAT_BASE.get(b))) {
                        f.renderer((source, name, message, viewer) -> Compat.text(String.format(fmt, p(source).getDisplayName(), Compat.legacy(message))));
                    }
                }, (f, b) -> {
                    pullText(f.message(), b.getMessage(), b::setMessage);
                    FORMAT_BASE.put(b, b.getFormat());   // 이 전달에서 서식이 바뀌었는지는 이 값과 견준다(다른 처리기가 정한 renderer 를 낡은 서식으로 덮지 않게)
                });
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerMoveEvent.class, org.bukkit.event.player.PlayerMoveEvent.class,
                f -> new org.bukkit.event.player.PlayerMoveEvent(p(f.player()), Compat.location(f.from()), Compat.location(f.to())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerTeleportEvent.class, org.bukkit.event.player.PlayerTeleportEvent.class,
                f -> new org.bukkit.event.player.PlayerTeleportEvent(p(f.player()), Compat.location(f.from()), Compat.location(f.to())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerRespawnEvent.class, org.bukkit.event.player.PlayerRespawnEvent.class,
                f -> new org.bukkit.event.player.PlayerRespawnEvent(p(f.player()), Compat.location(f.respawnLocation()), !f.isWorldSpawn()),
                (f, b) -> {
                    var loc = Compat.location(b.getRespawnLocation());
                    if (!loc.equals(f.respawnLocation())) f.respawnLocation(loc);
                }, (f, b) -> {
                    var cur = Compat.location(f.respawnLocation());
                    if (cur != null && !Compat.location(b.getRespawnLocation()).equals(f.respawnLocation())) b.setRespawnLocation(cur);
                });
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerChangedWorldEvent.class, org.bukkit.event.player.PlayerChangedWorldEvent.class,
                f -> {
                    var from = Compat.world(f.from());
                    return from == null ? null : new org.bukkit.event.player.PlayerChangedWorldEvent(p(f.player()), from);
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerGameModeChangeEvent.class, org.bukkit.event.player.PlayerGameModeChangeEvent.class,
                f -> new org.bukkit.event.player.PlayerGameModeChangeEvent(p(f.player()), GameMode.valueOf(f.newGameMode().name())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerToggleSneakEvent.class, org.bukkit.event.player.PlayerToggleSneakEvent.class,
                f -> new org.bukkit.event.player.PlayerToggleSneakEvent(p(f.player()), f.isSneaking()), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerToggleSprintEvent.class, org.bukkit.event.player.PlayerToggleSprintEvent.class,
                f -> new org.bukkit.event.player.PlayerToggleSprintEvent(p(f.player()), f.isSprinting()), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerToggleFlightEvent.class, org.bukkit.event.player.PlayerToggleFlightEvent.class,
                f -> new org.bukkit.event.player.PlayerToggleFlightEvent(p(f.player()), f.isFlying()), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent.class, org.bukkit.event.player.PlayerCommandPreprocessEvent.class,
                f -> f instanceof org.domi.furnace.api.event.player.PlayerSignCommandPreprocessEvent sign   // 표지판 클릭 명령 — Paper 처럼 같은 처리기에 하위 이벤트로 온다
                        ? new io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent(p(f.player()), f.message(),
                                sign.front() ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK)
                        : new org.bukkit.event.player.PlayerCommandPreprocessEvent(p(f.player()), f.message()),
                (f, b) -> {
                    if (!b.getMessage().equals(f.message())) f.message(b.getMessage());
                }, (f, b) -> {
                    if (!b.getMessage().equals(f.message())) b.setMessage(f.message());
                });
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerLevelChangeEvent.class, org.bukkit.event.player.PlayerLevelChangeEvent.class,
                f -> new org.bukkit.event.player.PlayerLevelChangeEvent(p(f.player()), f.oldLevel(), f.newLevel()), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerExpChangeEvent.class, org.bukkit.event.player.PlayerExpChangeEvent.class,
                f -> new org.bukkit.event.player.PlayerExpChangeEvent(p(f.player()), f.amount()),
                (f, b) -> {
                    if (b.getAmount() != f.amount()) f.setAmount(b.getAmount());
                }, (f, b) -> {
                    if (b.getAmount() != f.amount()) b.setAmount(f.amount());
                });
    }

    private static void registerWorld() {
        EventBridge.reg(org.domi.furnace.api.event.block.BlockBreakEvent.class, org.bukkit.event.block.BlockBreakEvent.class,
                f -> f.player() == null ? null : new org.bukkit.event.block.BlockBreakEvent(Compat.block(f.world(), f.pos()), p(f.player())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.world.WorldLoadEvent.class, org.bukkit.event.world.WorldLoadEvent.class,
                f -> {
                    var w = Compat.world(f.world());
                    return w == null ? null : new org.bukkit.event.world.WorldLoadEvent(w);
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.world.WorldUnloadEvent.class, org.bukkit.event.world.WorldUnloadEvent.class,
                f -> {
                    var w = Compat.world(f.world());
                    return w == null ? null : new org.bukkit.event.world.WorldUnloadEvent(w);
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.world.ChunkLoadEvent.class, org.bukkit.event.world.ChunkLoadEvent.class,
                f -> new org.bukkit.event.world.ChunkLoadEvent(new CompatChunk(f.world(), f.chunkX(), f.chunkZ())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.world.ChunkUnloadEvent.class, org.bukkit.event.world.ChunkUnloadEvent.class,
                f -> new org.bukkit.event.world.ChunkUnloadEvent(new CompatChunk(f.world(), f.chunkX(), f.chunkZ())), (f, b) -> {});
    }

    private static void registerOther() {
        EventBridge.reg(org.domi.furnace.api.event.server.PluginEnableEvent.class, org.bukkit.event.server.PluginEnableEvent.class,
                f -> {
                    Plugin pl = bukkitPlugin(f.plugin());
                    return pl == null ? null : new org.bukkit.event.server.PluginEnableEvent(pl);
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.server.PluginDisableEvent.class, org.bukkit.event.server.PluginDisableEvent.class,
                f -> {
                    Plugin pl = bukkitPlugin(f.plugin());
                    return pl == null ? null : new org.bukkit.event.server.PluginDisableEvent(pl);
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.server.ServerCommandEvent.class, org.bukkit.event.server.ServerCommandEvent.class,
                f -> new org.bukkit.event.server.ServerCommandEvent(Compat.sender(f.sender()), f.command()),
                (f, b) -> {
                    if (!Objects.equals(b.getCommand(), f.command())) f.command(b.getCommand());
                }, (f, b) -> {
                    if (!Objects.equals(b.getCommand(), f.command())) b.setCommand(f.command());
                });
        EventBridge.reg(org.domi.furnace.api.event.server.ServerLoadEvent.class, org.bukkit.event.server.ServerLoadEvent.class,
                f -> new org.bukkit.event.server.ServerLoadEvent(org.bukkit.event.server.ServerLoadEvent.LoadType.STARTUP), (f, b) -> {});
    }

    /**
     * 위쪽·아래쪽 인벤토리에 그 아이템과 같은 종류가 있는가 — CraftBukkit 이 두 인벤토리의 contains(Material) 로 보는 것. contains 는
     * getStorageContents 만 보므로 플레이어 인벤토리 창(minecraft:player)에서는 제작 칸(0..4)·갑옷(5..8)·왼손(45)을 빼고 주 36칸(9..44)만 본다.
     */
    private static boolean sameTypeIn(org.domi.furnace.api.inventory.InventorySnapshot snap, org.domi.furnace.api.item.ItemStack item) {
        var c = snap.contents();
        boolean playerWindow = snap.type().equals("minecraft:player");
        int from = playerWindow ? 9 : 0, to = playerWindow ? Math.min(45, c.size()) : c.size();
        for (int i = from; i < to; i++) if (!c.get(i).isEmpty() && c.get(i).type().equals(item.type())) return true;
        return false;
    }

    private static void registerInventory() {
        // 클릭: 끌어 나누기(quick_craft)는 InventoryDragEvent 로, 제작 결과 칸 꺼내기(Furnace CraftItemEvent)는 Bukkit 에서 CraftItemEvent 자기
        // 처리기 목록으로 가므로 InventoryClickEvent 처리기에는 오지 않는다(Bukkit 과 같다 — CraftItemEvent 는 아직 없다)
        EventBridge.reg(org.domi.furnace.api.event.inventory.InventoryClickEvent.class, org.bukkit.event.inventory.InventoryClickEvent.class,
                f -> {
                    if (f instanceof org.domi.furnace.api.event.inventory.CraftItemEvent || CompatInventories.isDrag(f.clickType())) return null;
                    CompatInventoryView view = CompatInventories.view(f.player(), f.inventory(), f.menu(), f.cursor());
                    int raw = f.slot();
                    var ct = CompatInventories.click(f.clickType(), raw, f.button());
                    org.domi.furnace.api.item.ItemStack hotbar = org.domi.furnace.api.item.ItemStack.EMPTY;
                    if (f.clickType().equals("swap")) {
                        int hr = CompatInventories.hotbarRaw(f.inventory().type(), f.inventory().size(), f.button());
                        hotbar = hr >= 0 ? f.inventory().get(hr) : CompatItems.toFurnace(view.getBottomInventory().getItem(40));
                    }
                    boolean creative = f.player().gameMode() == org.domi.furnace.api.entity.GameMode.CREATIVE;
                    var action = CompatInventories.action(f.clickType(), raw, f.button(), raw >= 0 ? f.current() : org.domi.furnace.api.item.ItemStack.EMPTY,
                            f.cursor(), hotbar, creative, f.clickType().equals("pickup_all") && sameTypeIn(f.inventory(), f.cursor()));
                    var st = raw < 0 || raw >= view.countSlots() ? org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE : view.getSlotType(raw);
                    return ct == org.bukkit.event.inventory.ClickType.NUMBER_KEY || ct == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                            ? new org.bukkit.event.inventory.InventoryClickEvent(view, st, raw, ct, action, f.button())
                            : new org.bukkit.event.inventory.InventoryClickEvent(view, st, raw, ct, action);
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.inventory.InventoryClickEvent.class, org.bukkit.event.inventory.InventoryDragEvent.class,
                f -> {
                    if (!CompatInventories.isDrag(f.clickType())) return null;
                    boolean creative = f.player().gameMode() == org.domi.furnace.api.entity.GameMode.CREATIVE;
                    var r = CompatInventories.dragStep(f.player().uniqueId(), f.slot(), f.button(), f.inventory(), f.cursor(), creative);
                    if (r == null) return null;
                    CompatInventoryView view = CompatInventories.view(f.player(), f.inventory(), f.menu(), r.oldCursor());
                    java.util.Map<Integer, org.bukkit.inventory.ItemStack> placed = new java.util.LinkedHashMap<>();
                    for (var e : r.placed().entrySet()) placed.put(e.getKey(), CompatItems.copyOf(e.getValue()));
                    return new org.bukkit.event.inventory.InventoryDragEvent(view, r.newCursor().isEmpty() ? null : CompatItems.copyOf(r.newCursor()),
                            CompatItems.copyOf(r.oldCursor()), r.kind() == 1, placed);
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.inventory.InventoryOpenEvent.class, org.bukkit.event.inventory.InventoryOpenEvent.class,
                f -> new org.bukkit.event.inventory.InventoryOpenEvent(CompatInventories.view(f.player(), f.inventory(), f.menu(),
                        org.domi.furnace.api.item.ItemStack.EMPTY)), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.inventory.InventoryCloseEvent.class, org.bukkit.event.inventory.InventoryCloseEvent.class,
                f -> new org.bukkit.event.inventory.InventoryCloseEvent(CompatInventories.view(f.player(), f.inventory(), f.menu(), null)), (f, b) -> {});

        // 사망: 플레이어는 Furnace PlayerDeathEvent(메시지·유지 여부를 되돌려 쓴다), 몹은 Furnace EntityDeathEvent. Bukkit 은 둘 다 EntityDeathEvent 목록이다.
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerDeathEvent.class, org.bukkit.event.entity.EntityDeathEvent.class,
                f -> new org.bukkit.event.entity.PlayerDeathEvent(p(f.player()), Compat.legacy(f.deathMessage()), f.keepInventory(), f.keepLevel()),
                (f, b) -> {
                    var pd = (org.bukkit.event.entity.PlayerDeathEvent) b;
                    if (!Objects.equals(pd.getDeathMessage(), Compat.legacy(f.deathMessage()))) f.deathMessage(Compat.text(pd.getDeathMessage()));
                    if (pd.getKeepInventory() != f.keepInventory()) f.keepInventory(pd.getKeepInventory());
                    if (pd.getKeepLevel() != f.keepLevel()) f.keepLevel(pd.getKeepLevel());
                }, (f, b) -> {
                    var pd = (org.bukkit.event.entity.PlayerDeathEvent) b;
                    String cur = Compat.legacy(f.deathMessage());
                    if (!Objects.equals(cur, pd.getDeathMessage())) pd.setDeathMessage(cur);
                    if (pd.getKeepInventory() != f.keepInventory()) pd.setKeepInventory(f.keepInventory());
                    if (pd.getKeepLevel() != f.keepLevel()) pd.setKeepLevel(f.keepLevel());
                });
        EventBridge.reg(org.domi.furnace.api.event.entity.EntityDeathEvent.class, org.bukkit.event.entity.EntityDeathEvent.class,
                f -> {
                    if (f.entity() instanceof org.domi.furnace.api.entity.Player) return null;   // 플레이어는 위 짝
                    return Compat.entity(f.entity()) instanceof org.bukkit.entity.LivingEntity le ? new org.bukkit.event.entity.EntityDeathEvent(le, null, 0) : null;
                }, (f, b) -> {});
    }

    private static Plugin bukkitPlugin(org.domi.furnace.api.plugin.FurnacePlugin p) {
        return p instanceof BukkitPluginAdapter a ? a.javaPlugin() : null;   // Furnace 고유 플러그인의 켜짐·꺼짐은 Bukkit 에 알리지 않는다
    }

    /** Furnace vanilla 피해 종류(minecraft:fall 등) → Bukkit DamageCause. 모르는 것은 CUSTOM. */
    static DamageCause cause(String type) {
        String k = type == null ? "" : type.substring(type.indexOf(':') + 1);
        return switch (k) {
            case "cactus", "sweet_berry_bush", "stalagmite" -> DamageCause.CONTACT;
            case "mob_attack", "mob_attack_no_aggro", "player_attack", "sting" -> DamageCause.ENTITY_ATTACK;
            case "thorns" -> DamageCause.THORNS;
            case "arrow", "trident", "mob_projectile", "fireball", "unattributed_fireball", "wither_skull", "thrown", "spit", "fireworks" -> DamageCause.PROJECTILE;
            case "in_wall" -> DamageCause.SUFFOCATION;
            case "fall", "ender_pearl" -> DamageCause.FALL;
            case "in_fire" -> DamageCause.FIRE;
            case "campfire" -> DamageCause.CAMPFIRE;
            case "on_fire" -> DamageCause.FIRE_TICK;
            case "lava" -> DamageCause.LAVA;
            case "drown" -> DamageCause.DROWNING;
            case "explosion", "player_explosion" -> DamageCause.ENTITY_EXPLOSION;
            case "bad_respawn_point" -> DamageCause.BLOCK_EXPLOSION;
            case "out_of_world" -> DamageCause.VOID;
            case "outside_border" -> DamageCause.WORLD_BORDER;
            case "generic_kill" -> DamageCause.KILL;   // /kill (Bukkit 의 KILL·WORLD_BORDER 는 기억 기준 — 원문 대조 전
            case "lightning_bolt" -> DamageCause.LIGHTNING;
            case "starve" -> DamageCause.STARVATION;
            case "magic", "indirect_magic" -> DamageCause.MAGIC;
            case "wither" -> DamageCause.WITHER;
            case "falling_block", "anvil", "falling_stalactite" -> DamageCause.FALLING_BLOCK;
            case "dragon_breath" -> DamageCause.DRAGON_BREATH;
            case "fly_into_wall" -> DamageCause.FLY_INTO_WALL;
            case "hot_floor" -> DamageCause.HOT_FLOOR;
            case "cramming" -> DamageCause.CRAMMING;
            case "dry_out" -> DamageCause.DRYOUT;
            case "freeze" -> DamageCause.FREEZE;
            case "sonic_boom" -> DamageCause.SONIC_BOOM;
            default -> DamageCause.CUSTOM;
        };
    }

    /** PlayerKickEvent#getLeaveMessage 의 기본값("§e이름 left the game"). */
    private static String defaultLeaveMessage(org.domi.furnace.api.entity.Player player) { return "§e" + player.name() + " left the game"; }

    /**
     * 채팅 받는 사람 — Furnace 이벤트의 받는 사람 집합(UUID 로 구분)을 Bukkit Player 로 보이는 변경 가능한 뷰. 처음 만질 때 Furnace 쪽 집합을 만든다.
     * 빼면 그 사람은 채팅을 받지 않고, 더하면 받는다(접속 중인 Player 만).
     */
    private static final class MappedPlayers extends AbstractSet<Player> {
        private final java.util.function.Supplier<Set<org.domi.furnace.api.entity.Player>> source;

        MappedPlayers(java.util.function.Supplier<Set<org.domi.furnace.api.entity.Player>> source) { this.source = source; }

        @Override
        public Iterator<Player> iterator() {
            Iterator<org.domi.furnace.api.entity.Player> it = source.get().iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }

                @Override
                public Player next() { return Compat.player(it.next()); }

                @Override
                public void remove() { it.remove(); }
            };
        }

        @Override
        public int size() { return source.get().size(); }

        @Override
        public boolean contains(Object o) { return o instanceof org.bukkit.craftbukkit.entity.CraftPlayer c && source.get().contains(c.furnaceHandle()); }

        @Override
        public boolean add(Player p) { return source.get().add(handle(p)); }

        @Override
        public boolean remove(Object o) { return o instanceof org.bukkit.craftbukkit.entity.CraftPlayer c && source.get().remove(c.furnaceHandle()); }

        @Override
        public void clear() { source.get().clear(); }

        private static org.domi.furnace.api.entity.Player handle(Player p) {
            Objects.requireNonNull(p, "player");
            if (p instanceof org.bukkit.craftbukkit.entity.CraftPlayer c) return c.furnaceHandle();
            throw new IllegalArgumentException("Furnace 가 모르는 Player 구현이다: " + p.getClass().getName());
        }
    }

    // ------------------------------------------------------------------ 엔티티 피해·상호작용(홀로그램 묶음)

    private static void registerEntityInteract() {
        // EntityDamageByEntityEvent 는 EntityDamageEvent 의 처리기 목록을 나눠 쓴다(Bukkit) — 짝도 하나이고, 때린 엔티티가 있으면 하위 이벤트를 만든다.
        // damager 는 Paper 처럼 직접 맞힌 엔티티(화살·TNT·때린 몹), Furnace 가 직접 엔티티 없이 주인만 알리면 주인.
        EventBridge.reg(org.domi.furnace.api.event.entity.EntityDamageEvent.class, org.bukkit.event.entity.EntityDamageEvent.class,
                f -> {
                    var target = Compat.entity(f.entity());
                    var source = f.directEntity() != null ? f.directEntity() : f.causingEntity();
                    if (source == null) return new org.bukkit.event.entity.EntityDamageEvent(target, cause(f.cause()), f.damage());
                    return new org.bukkit.event.entity.EntityDamageByEntityEvent(Compat.entity(source), target, byEntityCause(f.cause(), source), f.damage());
                },
                (f, b) -> {
                    if ((float) b.getDamage() != f.damage()) f.setDamage((float) b.getDamage());
                }, (f, b) -> {
                    if ((float) b.getDamage() != f.damage()) b.setDamage(f.damage());
                });
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerInteractEntityEvent.class, org.bukkit.event.player.PlayerInteractEntityEvent.class,
                f -> new org.bukkit.event.player.PlayerInteractEntityEvent(p(f.player()), Compat.entity(f.target()),
                        f.isMainHand() ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND),
                (f, b) -> {});
    }

    /** 때린 엔티티가 있는 피해의 DamageCause — Paper 처럼 폭발은 점화된 TNT 면 BLOCK_EXPLOSION, 다른 엔티티면 ENTITY_EXPLOSION. 그 밖은 피해 종류 키로. */
    static DamageCause byEntityCause(String type, org.domi.furnace.api.entity.Entity damager) {
        String k = type == null ? "" : type.substring(type.indexOf(':') + 1);
        if (k.equals("explosion") || k.equals("player_explosion")) {
            return "minecraft:tnt".equals(damager.type()) ? DamageCause.BLOCK_EXPLOSION : DamageCause.ENTITY_EXPLOSION;
        }
        return cause(type);
    }
}
