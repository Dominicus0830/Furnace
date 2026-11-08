// Bukkit 이벤트 ↔ Furnace 이벤트 짝(1차 24종). 새 짝을 더할 때는 Bukkit 이벤트 클래스를 org.bukkit.event 아래에 두고 여기에 reg 한 줄을 더한다.
// 원칙: Furnace 이벤트가 알리지 않는 값은 지어내지 않는다 — 읽기는 정직한 기본값(예: TeleportCause.UNKNOWN) 또는 UnsupportedFeature,
// 쓰기는 UnsupportedFeature. 처리기가 바꾼 값은 Furnace 이벤트에 되돌려 쓴다(바뀌지 않았으면 손대지 않아 서식이 깎이지 않는다).
package org.domi.furnace.compat.paper;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
        // 두 Bukkit 하위 이벤트가 PlayerChannelEvent 의 처리기 목록을 나눠 쓰므로 짝도 하나다. Bukkit 이 받지 않는 64자 초과 이름은 건너뛴다.
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
                f -> new org.bukkit.event.player.PlayerKickEvent(p(f.player()), Compat.legacy(f.reason()), "§e" + f.player().name() + " left the game"),
                (f, b) -> pushText(b.getReason(), f.reason(), t -> f.setReason(t == null ? Text.empty() : t)),
                (f, b) -> pullText(f.reason(), b.getReason(), b::setReason));
        EventBridge.reg(org.domi.furnace.api.event.player.AsyncChatEvent.class, org.bukkit.event.player.AsyncPlayerChatEvent.class,
                f -> new org.bukkit.event.player.AsyncPlayerChatEvent(f.isAsync(), p(f.player()), Compat.legacy(f.message()), new ReadOnlyPlayers(onlinePlayers())),
                (f, b) -> {
                    pushText(b.getMessage(), f.message(), t -> f.message(t == null ? Text.empty() : t));
                    String fmt = b.getFormat();
                    if (!Objects.equals(fmt, FORMAT_BASE.get(b))) {
                        f.renderer((source, name, message, viewer) -> Compat.text(String.format(fmt, Compat.legacy(name), Compat.legacy(message))));
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
                f -> new org.bukkit.event.player.PlayerCommandPreprocessEvent(p(f.player()), f.message()),
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
        EventBridge.reg(org.domi.furnace.api.event.entity.EntityDamageEvent.class, org.bukkit.event.entity.EntityDamageEvent.class,
                f -> new org.bukkit.event.entity.EntityDamageEvent(Compat.entity(f.entity()), cause(f.cause()), f.damage()),
                (f, b) -> {
                    if ((float) b.getDamage() != f.damage()) f.setDamage((float) b.getDamage());
                }, (f, b) -> {
                    if ((float) b.getDamage() != f.damage()) b.setDamage(f.damage());
                });
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

    private static Plugin bukkitPlugin(org.domi.furnace.api.plugin.FurnacePlugin p) {
        return p instanceof BukkitPluginAdapter a ? a.javaPlugin() : null;   // Furnace 고유 플러그인의 켜짐·꺼짐은 Bukkit 에 알리지 않는다
    }

    private static Set<Player> onlinePlayers() {
        Set<Player> out = new LinkedHashSet<>();
        for (var pl : Furnace.server().onlinePlayers()) out.add(Compat.player(pl));
        return out;
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

    /** 받는 사람 집합 — 읽기만 된다(바꾸려 하면 UnsupportedFeature). */
    private static final class ReadOnlyPlayers extends AbstractSet<Player> {
        private final Set<Player> inner;

        ReadOnlyPlayers(Set<Player> inner) { this.inner = inner; }

        @Override
        public Iterator<Player> iterator() {
            Iterator<Player> it = inner.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }

                @Override
                public Player next() { return it.next(); }

                @Override
                public void remove() { throw denied(); }
            };
        }

        @Override
        public int size() { return inner.size(); }

        @Override
        public boolean add(Player p) { throw denied(); }

        @Override
        public boolean addAll(Collection<? extends Player> c) { throw denied(); }

        @Override
        public boolean remove(Object o) { throw denied(); }

        @Override
        public void clear() { throw denied(); }

        private static UnsupportedFeature denied() {
            return new UnsupportedFeature("AsyncPlayerChatEvent#getRecipients 바꾸기",
                    "Furnace 채팅 이벤트는 받는 사람을 걸러 내는 칸이 없다 — 받는 사람마다 다른 서식은 AsyncChatEvent#renderer 를 쓴다");
        }
    }
}
