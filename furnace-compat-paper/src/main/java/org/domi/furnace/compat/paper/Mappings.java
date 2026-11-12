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
        InventoryResultMappings.register();
        registerEntityInteract();
        registerInteractAndBlocks();
        registerLogin();
        WorldChangeMappings.register();
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
                f -> new org.bukkit.event.world.ChunkLoadEvent(new CompatChunk(f.world(), f.chunkX(), f.chunkZ()), f.newChunk()), (f, b) -> {});
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
        // 클릭: 끌어 나누기(quick_craft)는 InventoryDragEvent 로. 제작 결과 칸 꺼내기(Furnace CraftItemEvent)는 Bukkit CraftItemEvent 인데, Bukkit 에서
        // CraftItemEvent 는 자기 처리기 목록이 없어 InventoryClickEvent 목록으로 간다 — InventoryClickEvent 처리기도 받는다(Paper 와 같다).
        // 레시피를 모르면(Furnace recipe()==null) Paper 처럼 보통 InventoryClickEvent 다.
        EventBridge.reg(org.domi.furnace.api.event.inventory.InventoryClickEvent.class, org.bukkit.event.inventory.InventoryClickEvent.class,
                f -> {
                    if (CompatInventories.isDrag(f.clickType())) return null;
                    EventInventories.CompatRecipe recipe = f instanceof org.domi.furnace.api.event.inventory.CraftItemEvent ce
                            ? EventInventories.CompatRecipe.of(ce.recipe(), ce.result()) : null;
                    CompatInventoryView view = CompatInventories.view(f.player(), f.inventory(), f.menu(), f.cursor(),
                            recipe == null ? CompatInventories.Extras.NONE : new CompatInventories.Extras(recipe, false, null, null, null));
                    int raw = f.slot();
                    var ct = CompatInventories.click(f.clickType(), raw, f.button());
                    org.domi.furnace.api.item.ItemStack hotbar = org.domi.furnace.api.item.ItemStack.EMPTY;
                    if (f.clickType().equals("swap")) {
                        int hr = CompatInventories.hotbarRaw(f.inventory().type(), f.inventory().size(), f.button());
                        hotbar = hr >= 0 ? f.inventory().get(hr) : CompatItems.toFurnace(view.getBottomInventory().getItem(40));
                    }
                    boolean creative = f.player().gameMode() == org.domi.furnace.api.entity.GameMode.CREATIVE;
                    var action = recipe != null
                            ? CompatInventories.resultAction(f.clickType(), raw, f.button(), f.current(), f.cursor(), hotbar, creative)
                            : CompatInventories.action(f.clickType(), raw, f.button(), raw >= 0 ? f.current() : org.domi.furnace.api.item.ItemStack.EMPTY,
                                    f.cursor(), hotbar, creative, f.clickType().equals("pickup_all") && sameTypeIn(f.inventory(), f.cursor()));
                    var st = raw < 0 || raw >= view.countSlots() ? org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE : view.getSlotType(raw);
                    if (recipe != null) {
                        return ct == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                                ? new org.bukkit.event.inventory.CraftItemEvent(recipe, view, st, raw, ct, action, f.button())
                                : new org.bukkit.event.inventory.CraftItemEvent(recipe, view, st, raw, ct, action);
                    }
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
                f -> new org.bukkit.event.inventory.InventoryCloseEvent(CompatInventories.view(f.player(), f.inventory(), f.menu(), null),
                        org.bukkit.event.inventory.InventoryCloseEvent.Reason.valueOf(f.reason().name())), (f, b) -> {});

        // 사망: 플레이어는 Furnace PlayerDeathEvent(메시지·유지 여부·드롭·경험치를 되돌려 쓴다), 몹은 Furnace EntityDeathEvent. Bukkit 은 둘 다 EntityDeathEvent 목록이다.
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerDeathEvent.class, org.bukkit.event.entity.EntityDeathEvent.class,
                f -> {
                    var b = new org.bukkit.event.entity.PlayerDeathEvent(p(f.player()), bukkitDrops(f.drops()), f.droppedExp(), Compat.legacy(f.deathMessage()),
                            f.keepInventory(), f.keepLevel());
                    b.setNewExp(f.newExp());
                    b.setNewLevel(f.newLevel());
                    b.setNewTotalExp(f.newTotalExp());
                    b.getItemsToKeep().addAll(bukkitDrops(f.itemsToKeep()));
                    b.setShouldDropExperience(f.shouldDropExperience());
                    b.initReviveHealth(f.reviveHealth());
                    return b;
                },
                (f, b) -> {
                    var pd = (org.bukkit.event.entity.PlayerDeathEvent) b;
                    if (!Objects.equals(pd.getDeathMessage(), Compat.legacy(f.deathMessage()))) f.deathMessage(Compat.text(pd.getDeathMessage()));
                    if (pd.getKeepInventory() != f.keepInventory()) f.keepInventory(pd.getKeepInventory());
                    if (pd.getKeepLevel() != f.keepLevel()) f.keepLevel(pd.getKeepLevel());
                    var now = furnaceDrops(pd.getDrops());
                    if (!now.equals(f.drops())) {
                        f.drops().clear();
                        f.drops().addAll(now);
                    }
                    if (pd.getDroppedExp() != f.droppedExp()) f.droppedExp(pd.getDroppedExp());
                    if (pd.getNewExp() != f.newExp()) f.newExp(pd.getNewExp());
                    if (pd.getNewLevel() != f.newLevel()) f.newLevel(pd.getNewLevel());
                    if (pd.getNewTotalExp() != f.newTotalExp()) f.newTotalExp(pd.getNewTotalExp());
                    var keep = furnaceDrops(pd.getItemsToKeep());
                    if (!keep.equals(f.itemsToKeep())) {
                        f.itemsToKeep().clear();
                        f.itemsToKeep().addAll(keep);
                    }
                    if (pd.shouldDropExperience() != f.shouldDropExperience()) f.shouldDropExperience(pd.shouldDropExperience());
                    if ((float) pd.getReviveHealth() != f.reviveHealth()) f.reviveHealth((float) pd.getReviveHealth());
                }, (f, b) -> {
                    var pd = (org.bukkit.event.entity.PlayerDeathEvent) b;
                    String cur = Compat.legacy(f.deathMessage());
                    if (!Objects.equals(cur, pd.getDeathMessage())) pd.setDeathMessage(cur);
                    if (pd.getKeepInventory() != f.keepInventory()) pd.setKeepInventory(f.keepInventory());
                    if (pd.getKeepLevel() != f.keepLevel()) pd.setKeepLevel(f.keepLevel());
                    if (!furnaceDrops(pd.getDrops()).equals(f.drops())) {   // 앞선 Furnace 처리기가 목록을 바꿨다 — 같은 목록으로 맞춘다
                        pd.getDrops().clear();
                        pd.getDrops().addAll(bukkitDrops(f.drops()));
                    }
                    if (pd.getDroppedExp() != f.droppedExp()) pd.setDroppedExp(f.droppedExp());
                    if (pd.getNewExp() != f.newExp()) pd.setNewExp(f.newExp());
                    if (pd.getNewLevel() != f.newLevel()) pd.setNewLevel(f.newLevel());
                    if (pd.getNewTotalExp() != f.newTotalExp()) pd.setNewTotalExp(f.newTotalExp());
                    if (!furnaceDrops(pd.getItemsToKeep()).equals(f.itemsToKeep())) {
                        pd.getItemsToKeep().clear();
                        pd.getItemsToKeep().addAll(bukkitDrops(f.itemsToKeep()));
                    }
                    if (pd.shouldDropExperience() != f.shouldDropExperience()) pd.setShouldDropExperience(f.shouldDropExperience());
                    if ((float) pd.getReviveHealth() != f.reviveHealth()) pd.initReviveHealth(f.reviveHealth());
                });
        EventBridge.reg(org.domi.furnace.api.event.entity.EntityDeathEvent.class, org.bukkit.event.entity.EntityDeathEvent.class,
                f -> {
                    if (f.entity() instanceof org.domi.furnace.api.entity.Player) return null;   // 플레이어는 위 짝
                    if (!(Compat.entity(f.entity()) instanceof org.bukkit.entity.LivingEntity le)) return null;
                    var b = new org.bukkit.event.entity.EntityDeathEvent(le, bukkitDrops(f.drops()), f.droppedExp());
                    b.initReviveHealth(f.reviveHealth());
                    return b;
                },
                (f, b) -> {   // Bukkit 처리기가 바꾼 드롭·경험치·되살릴 체력을 Furnace 이벤트로(취소는 다리가 옮긴다)
                    var now = furnaceDrops(b.getDrops());
                    if (!now.equals(f.drops())) {
                        f.drops().clear();
                        f.drops().addAll(now);
                    }
                    if (b.getDroppedExp() != f.droppedExp()) f.droppedExp(b.getDroppedExp());
                    if ((float) b.getReviveHealth() != f.reviveHealth()) f.reviveHealth((float) b.getReviveHealth());
                }, (f, b) -> {   // 앞선 Furnace 처리기가 바꾼 값을 Bukkit 이벤트에 맞춘다
                    if (!furnaceDrops(b.getDrops()).equals(f.drops())) {
                        b.getDrops().clear();
                        b.getDrops().addAll(bukkitDrops(f.drops()));
                    }
                    if (b.getDroppedExp() != f.droppedExp()) b.setDroppedExp(f.droppedExp());
                    if ((float) b.getReviveHealth() != f.reviveHealth()) b.initReviveHealth(f.reviveHealth());
                });
    }

    /** Furnace 드롭 목록 → Bukkit 이 바꿀 수 있는 새 목록(Paper 의 getDrops 처럼 넣고 빼고 개수를 바꿀 수 있고, 이벤트 뒤 변환해 되돌려 쓴다). */
    private static java.util.List<org.bukkit.inventory.ItemStack> bukkitDrops(java.util.List<org.domi.furnace.api.item.ItemStack> drops) {
        var out = new java.util.ArrayList<org.bukkit.inventory.ItemStack>(drops.size());
        for (var d : drops) out.add(CompatItems.copyOf(d));
        return out;
    }

    /** Bukkit 드롭 목록 → Furnace 값(null·공기·개수 0 이하는 빠진다 — Paper 도 그런 칸은 던지지 않는다). */
    private static java.util.List<org.domi.furnace.api.item.ItemStack> furnaceDrops(java.util.List<org.bukkit.inventory.ItemStack> drops) {
        var out = new java.util.ArrayList<org.domi.furnace.api.item.ItemStack>(drops.size());
        for (var d : drops) {
            var f = CompatItems.toFurnace(d);
            if (!f.isEmpty()) out.add(f);
        }
        return out;
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

    // ------------------------------------------------------------------ 블록 클릭·단축 칸·블록 갱신·화로 (FAWE 묶음)

    private static org.bukkit.block.BlockFace face(String f) {
        return f == null ? null : org.bukkit.block.BlockFace.valueOf(f.toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * PlayerInteractEvent 의 두 결과(블록 쓰기·아이템 쓰기)를 Furnace 의 취소 하나로 합친다. CraftBukkit 이 결과를 쓰는 방식을 따른다:
     * 공기 클릭은 블록이 없어 아이템 쓰기만 본다(블록 쓰기는 처음부터 DENY 다), 왼쪽 블록 클릭은 블록 쓰기 DENY 거나 창조 모드가 아닌데
     * 아이템 쓰기 DENY 면 막는다(캐기 시작), PHYSICAL 은 블록 쓰기만 본다,
     * 오른쪽 블록 클릭은 둘 중 하나라도 DENY 면 막는다 — Furnace 는 "블록만 막고 아이템은 쓰기"를 나눠 하지 못해 더 막는 쪽을 골랐다(보호 플러그인이 새지 않게).
     */
    static boolean interactCancelled(org.bukkit.event.player.PlayerInteractEvent b) {
        var deny = org.bukkit.event.Event.Result.DENY;
        return switch (b.getAction()) {
            case LEFT_CLICK_AIR, RIGHT_CLICK_AIR -> b.useItemInHand() == deny;
            // CraftBukkit 은 PHYSICAL(밭 밟기·발판)에서 isCancelled() = 블록 쓰기 DENY 만 본다
            case PHYSICAL -> b.useInteractedBlock() == deny;
            // CraftBukkit 캐기 시작: 블록 DENY 면 멈춤 → 창조 모드는 즉시 부숨 → 아이템 DENY 면 멈춤
            case LEFT_CLICK_BLOCK -> b.useInteractedBlock() == deny
                    || (b.useItemInHand() == deny && (b.getPlayer() == null || b.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE));
            case RIGHT_CLICK_BLOCK -> b.useInteractedBlock() == deny || b.useItemInHand() == deny;
        };
    }

    /** Furnace 쪽 취소를 Bukkit 이벤트에 보인다. 풀 때는 DENY 였던 결과만 DEFAULT 로(공기 클릭의 블록 쓰기 DENY 는 그대로 둔다 — Bukkit 원래 상태). */
    static void showInteractCancelled(org.bukkit.event.player.PlayerInteractEvent b, boolean cancelled) {
        var deny = org.bukkit.event.Event.Result.DENY;
        if (cancelled) {
            b.setUseItemInHand(deny);
            if (b.hasBlock()) b.setUseInteractedBlock(deny);
            return;
        }
        if (b.useItemInHand() == deny) b.setUseItemInHand(org.bukkit.event.Event.Result.DEFAULT);
        if (b.hasBlock() && b.useInteractedBlock() == deny) b.setUseInteractedBlock(org.bukkit.event.Event.Result.DEFAULT);
    }

    private static void registerInteractAndBlocks() {
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerInteractEvent.class, org.bukkit.event.player.PlayerInteractEvent.class,
                f -> {
                    var action = org.bukkit.event.block.Action.valueOf(f.action().name());
                    var item = f.item() == null || f.item().isEmpty() ? null : CompatItems.copyOf(f.item());   // CraftBukkit: 빈 손이면 null
                    var clicked = f.clickedBlock();
                    org.bukkit.block.Block block = clicked == null ? null : Compat.block(clicked.world(), clicked.pos());
                    var bf = clicked == null ? org.bukkit.block.BlockFace.SOUTH : face(f.blockFace());   // CraftBukkit: 공기 클릭의 면은 SOUTH
                    var hand = f.isMainHand() ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND;
                    return new org.bukkit.event.player.PlayerInteractEvent(p(f.player()), action, item, block, bf, hand);
                },
                (f, b) -> {}, (f, b) -> {}, Mappings::interactCancelled, Mappings::showInteractCancelled);
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerItemHeldEvent.class, org.bukkit.event.player.PlayerItemHeldEvent.class,
                f -> new org.bukkit.event.player.PlayerItemHeldEvent(p(f.player()), f.previousSlot(), f.newSlot()), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockPhysicsEvent.class, org.bukkit.event.block.BlockPhysicsEvent.class,
                f -> new org.bukkit.event.block.BlockPhysicsEvent(Compat.block(f.world(), f.pos()), CompatBlockData.of(f.block()),
                        Compat.block(f.world(), f.sourcePos())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockFromToEvent.class, org.bukkit.event.block.BlockFromToEvent.class,
                // CraftBukkit 흐름처럼 (출발 칸, 방향) — 도착 칸은 출발 칸의 그 방향 이웃이다. 방향이 없으면 (출발, 도착) 꼴(면 SELF)
                f -> f.face() == null
                        ? new org.bukkit.event.block.BlockFromToEvent(Compat.block(f.world(), f.pos()), Compat.block(f.world(), f.toPos()))
                        : new org.bukkit.event.block.BlockFromToEvent(Compat.block(f.world(), f.pos()), face(f.face())),
                (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockBurnEvent.class, org.bukkit.event.block.BlockBurnEvent.class,
                f -> new org.bukkit.event.block.BlockBurnEvent(Compat.block(f.world(), f.pos()),
                        f.ignitingPos() == null ? null : Compat.block(f.world(), f.ignitingPos())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockIgniteEvent.class, org.bukkit.event.block.BlockIgniteEvent.class,
                f -> new org.bukkit.event.block.BlockIgniteEvent(Compat.block(f.world(), f.pos()),
                        org.bukkit.event.block.BlockIgniteEvent.IgniteCause.valueOf(f.cause().name()),
                        f.player() == null ? null : p(f.player()),
                        f.ignitingPos() == null ? null : Compat.block(f.world(), f.ignitingPos())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockRedstoneEvent.class, org.bukkit.event.block.BlockRedstoneEvent.class,
                f -> new org.bukkit.event.block.BlockRedstoneEvent(Compat.block(f.world(), f.pos()), f.oldCurrent(), f.newCurrent()),
                (f, b) -> {
                    if (b.getNewCurrent() != f.newCurrent()) f.setNewCurrent(b.getNewCurrent());
                }, (f, b) -> {
                    if (b.getNewCurrent() != f.newCurrent()) b.setNewCurrent(f.newCurrent());
                });
        EventBridge.reg(org.domi.furnace.api.event.block.LeavesDecayEvent.class, org.bukkit.event.block.LeavesDecayEvent.class,
                f -> new org.bukkit.event.block.LeavesDecayEvent(Compat.block(f.world(), f.pos())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.inventory.FurnaceBurnEvent.class, org.bukkit.event.inventory.FurnaceBurnEvent.class,
                f -> new org.bukkit.event.inventory.FurnaceBurnEvent(Compat.block(f.world(), f.pos()), CompatItems.copyOf(f.fuel()), f.burnTime()),
                (f, b) -> {
                    if (b.getBurnTime() != f.burnTime()) f.setBurnTime(b.getBurnTime());
                    if (b.isBurning() != f.isBurning()) f.setBurning(b.isBurning());
                }, (f, b) -> {
                    if (b.getBurnTime() != f.burnTime()) b.setBurnTime(f.burnTime());
                    if (b.isBurning() != f.isBurning()) b.setBurning(f.isBurning());
                });
        // 화로 제련은 Bukkit 에서 BlockCookEvent 처리기 목록으로 간다(FurnaceSmeltEvent 는 하위 클래스). 모닥불·훈연기의 BlockCookEvent 는 아직 없다
        EventBridge.reg(org.domi.furnace.api.event.inventory.FurnaceSmeltEvent.class, org.bukkit.event.block.BlockCookEvent.class,
                f -> new org.bukkit.event.inventory.FurnaceSmeltEvent(Compat.block(f.world(), f.pos()), CompatItems.copyOf(f.source()), CompatItems.copyOf(f.result())),
                (f, b) -> {
                    var r = b.getResult() == null ? org.domi.furnace.api.item.ItemStack.EMPTY : CompatItems.toFurnace(b.getResult());
                    if (!Objects.equals(r, f.result())) f.setResult(r);
                }, (f, b) -> {
                    var r = b.getResult() == null ? org.domi.furnace.api.item.ItemStack.EMPTY : CompatItems.toFurnace(b.getResult());
                    if (!Objects.equals(r, f.result())) b.setResult(CompatItems.copyOf(f.result()));
                });
    }

    /** 때린 엔티티가 있는 피해의 DamageCause — Paper 처럼 폭발은 점화된 TNT 면 BLOCK_EXPLOSION, 다른 엔티티면 ENTITY_EXPLOSION. 그 밖은 피해 종류 키로. */
    static DamageCause byEntityCause(String type, org.domi.furnace.api.entity.Entity damager) {
        String k = type == null ? "" : type.substring(type.indexOf(':') + 1);
        if (k.equals("explosion") || k.equals("player_explosion")) {
            return "minecraft:tnt".equals(damager.type()) ? DamageCause.BLOCK_EXPLOSION : DamageCause.ENTITY_EXPLOSION;
        }
        return cause(type);
    }

    // ------------------------------------------------------------------ 접속·RCON (LuckPerms 등 권한 플러그인이 쓰는 이벤트)

    /** "호스트:포트" — CraftBukkit 이 핸드셰이크에서 남기는 Connection.hostname 과 같은 모양. */
    private static String hostname(org.domi.furnace.api.net.PlayerConnection c) {
        return c == null ? "" : c.hostname() + ":" + c.hostPort();
    }

    private static java.net.InetAddress address(org.domi.furnace.api.net.PlayerConnection c) {
        return c == null || c.address() == null ? null : c.address().getAddress();
    }

    private static void registerLogin() {
        EventBridge.reg(org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent.class, org.bukkit.event.player.AsyncPlayerPreLoginEvent.class,
                f -> new org.bukkit.event.player.AsyncPlayerPreLoginEvent(f.name(), f.address(), f.address(), f.uniqueId(),
                        f.connection() != null && f.connection().isTransferred(), hostname(f.connection())),
                (f, b) -> {
                    var r = org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent.Result.valueOf(b.getLoginResult().name());
                    if (r == org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                        if (f.result() != r) f.allow();
                    } else if (r != f.result() || !Objects.equals(b.getKickMessage(), Compat.legacy(f.kickMessage()))) {
                        f.disallow(r, Compat.text(b.getKickMessage() == null ? "" : b.getKickMessage()));
                    }
                },
                (f, b) -> {
                    var r = org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.valueOf(f.result().name());
                    // 둘 다 허용이면 손대지 않는다 — 허용 상태에서 앞 처리기가 미리 넣은 사유(setKickMessage)를 지우지 않게
                    if (r == org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED && b.getLoginResult() == r) return;
                    String m = Compat.legacy(f.kickMessage());
                    if (b.getLoginResult() != r || !Objects.equals(b.getKickMessage(), m)) b.disallow(r, m);
                });
        // Bukkit PlayerLoginEvent 는 Player 객체가 필요하다 — Furnace 에서 그것이 처음 생기는 PlayerCreateEvent 에 잇는다(compat-luckperms 문서)
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerCreateEvent.class, org.bukkit.event.player.PlayerLoginEvent.class,
                f -> {
                    var a = address(f.connection());
                    return new org.bukkit.event.player.PlayerLoginEvent(p(f.player()), hostname(f.connection()), a, a);
                },
                (f, b) -> {
                    if (b.getResult() == org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED) {
                        if (!f.isAllowed()) f.allow();
                    } else if (f.isAllowed() || !Objects.equals(b.getKickMessage(), Compat.legacy(f.kickMessage()))) {
                        f.disallow(Compat.text(b.getKickMessage() == null ? "" : b.getKickMessage()));
                    }
                },
                (f, b) -> {
                    boolean bAllowed = b.getResult() == org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED;
                    if (f.isAllowed() && !bAllowed) b.allow();
                    else if (!f.isAllowed() && (bAllowed || !Objects.equals(b.getKickMessage(), Compat.legacy(f.kickMessage())))) {
                        b.disallow(bAllowed ? org.bukkit.event.player.PlayerLoginEvent.Result.KICK_OTHER : b.getResult(), Compat.legacy(f.kickMessage()));
                    }
                });
        EventBridge.reg(org.domi.furnace.api.event.server.RemoteServerCommandEvent.class, org.bukkit.event.server.RemoteServerCommandEvent.class,
                f -> new org.bukkit.event.server.RemoteServerCommandEvent(Compat.sender(f.sender()), f.command()),
                (f, b) -> {
                    if (!Objects.equals(b.getCommand(), f.command())) f.command(b.getCommand());
                }, (f, b) -> {
                    if (!Objects.equals(b.getCommand(), f.command())) b.setCommand(f.command());
                });
    }
}
