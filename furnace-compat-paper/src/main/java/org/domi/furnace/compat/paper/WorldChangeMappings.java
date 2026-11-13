// 블록 설치·자람·사라짐·폭발, 엔티티 스폰, 플레이어 아이템 버리기 — 서버가 이미 내는 Furnace 이벤트를 Bukkit 이벤트로 잇는다(O12 H1).
// 보호 플러그인(WorldGuard·GriefPrevention·CoreProtect 류)이 설치·환경 변화·스폰·버리기를 막고 기록하는 길이다. 규칙(CraftBukkit/Paper 1.21.1 기준):
//  - 취소는 Furnace 이벤트의 취소와 같다. BlockPlaceEvent 만 "취소 또는 canBuild 거짓"이 막는 것이다(CraftEventFactory.callBlockPlaceEvent).
//  - CraftBukkit 은 블록을 놓은 뒤에 BlockPlaceEvent 를 부른다. Furnace 는 놓기 전에 부르므로 getBlockPlaced() 가 "놓일 상태"를 보이게 했다.
//  - 새 상태(BlockGrow/Fade 의 getNewState)·터진 블록 상태·교체된 상태는 만든 순간의 스냅숏이다.
//  - 폭발 yield 는 읽을 수 있고 setYield 는 서버가 적용하지 않는다. blockList() 는 고칠 수 있어 서버가 따른다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.domi.furnace.api.world.BlockPos;

final class WorldChangeMappings {

    private WorldChangeMappings() {}

    static void register() {
        registerBlocks();
        registerEntities();
        registerProtection();
    }

    private static org.bukkit.entity.Player p(org.domi.furnace.api.entity.Player f) { return Compat.player(f); }

    private static void registerBlocks() {
        // CraftEventFactory.callBlockPlaceEvent: 막는 것은 isCancelled() 이거나 canBuild() 가 거짓. 풀 때는 둘 다 되돌린다(앞선 처리기의 setBuild(false) 가 낡은 채 남지 않게).
        EventBridge.reg(org.domi.furnace.api.event.block.BlockPlaceEvent.class, org.bukkit.event.block.BlockPlaceEvent.class,
                f -> {
                    if (f.player() == null) return null;
                    var placed = CompatBlock.showing(f.world(), f.pos(), f.placedData());
                    // 아직 놓기 전이라 그 자리의 원래 블록이 교체될 블록이다(서버가 소유 스레드에서 읽어 알린다)
                    var replaced = f.replacedData() != null ? CompatBlockState.of(f.world(), f.pos(), f.replacedData()) : liveOrAir(f.world(), f.pos());
                    var hand = f.isMainHand() ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND;
                    return new org.bukkit.event.block.BlockPlaceEvent(placed, replaced, Compat.block(f.world(), f.against()), CompatItems.copyOf(f.item()),
                            p(f.player()), true, hand);
                },
                (f, b) -> {}, (f, b) -> {},
                b -> b.isCancelled() || !b.canBuild(),
                (b, cancelled) -> {
                    b.setCancelled(cancelled);
                    if (!cancelled) b.setBuild(true);
                });
        EventBridge.reg(org.domi.furnace.api.event.block.BlockGrowEvent.class, org.bukkit.event.block.BlockGrowEvent.class,
                f -> new org.bukkit.event.block.BlockGrowEvent(Compat.block(f.world(), f.pos()), CompatBlockState.of(f.world(), f.pos(), f.newData())),
                (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockFadeEvent.class, org.bukkit.event.block.BlockFadeEvent.class,
                f -> new org.bukkit.event.block.BlockFadeEvent(Compat.block(f.world(), f.pos()), CompatBlockState.of(f.world(), f.pos(), f.newData())),
                (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockExplodeEvent.class, org.bukkit.event.block.BlockExplodeEvent.class,
                f -> {
                    var type = Compat.furnace().blockTypes().get(f.blockType());
                    if (type.isEmpty()) return null;   // 서버가 모르는 종류 — 다리가 알 수 없는 블록은 건너뛴다
                    return new org.bukkit.event.block.BlockExplodeEvent(Compat.block(f.world(), f.pos()),
                            CompatBlockState.of(f.world(), f.pos(), type.get().defaultData()), explodeList(f, f.world(), f.blocks()), f.yield(),
                            org.bukkit.ExplosionResult.valueOf(f.result()));
                },
                (f, b) -> pushExplodeList(f, b.blockList(), f.blocks()),
                (f, b) -> pullExplodeList(f, b.blockList(), f.world(), f.blocks()));
    }

    // ------------------------------------------------------------------ 폭발 목록(blockList)
    // Paper(CraftBukkit): 서버의 toBlow 를 *뒤에서부터* 훑으며 *공기를 뺀* 목록을 이벤트에 준다. 처리기가 목록을 고쳤을 때만 서버 목록을 그 목록으로 바꾼다
    // (안 고쳤으면 서버 목록은 바닐라 순서 그대로 — Furnace 원칙). 두 목록이 오가며 어긋나지 않게 이벤트마다 마지막으로 맞춘 두 목록을 붙임 칸에 둔다.

    /** 이 폭발 이벤트에서 마지막으로 맞춘 Bukkit 쪽 목록과 Furnace 쪽 목록(위치). */
    private static final class ExplodeSync {
        List<BlockPos> bukkit;
        List<BlockPos> furnace;
    }

    private static final org.domi.furnace.api.event.EventSlot<ExplodeSync> EXPLODE_SYNC = new org.domi.furnace.api.event.EventSlot<>("explode-block-list");

    /** Bukkit 이 주는 목록: 뒤에서부터, 공기 제외(읽을 수 없는 칸은 공기가 아니라고 본다). */
    private static List<Block> bukkitBlockList(String world, List<BlockPos> blown) {
        List<Block> out = new ArrayList<>(blown.size());
        for (int i = blown.size() - 1; i >= 0; i--) {
            Block block = Compat.block(world, blown.get(i));
            boolean air;
            try {
                air = block.getType().isAir();
            } catch (RuntimeException unreadable) {
                air = false;
            }
            if (!air) out.add(block);
        }
        return out;
    }

    private static List<Block> explodeList(org.domi.furnace.api.event.Event f, String world, List<BlockPos> blown) {
        List<Block> list = bukkitBlockList(world, blown);
        ExplodeSync sync = EXPLODE_SYNC.computeIfAbsent(f, e -> new ExplodeSync());
        sync.bukkit = positions(list);
        sync.furnace = List.copyOf(blown);
        return list;
    }

    /** 처리기가 Bukkit 목록을 고쳤으면(위치·순서가 마지막으로 맞춘 것과 다르면) 서버 목록을 그 목록으로 바꾼다. */
    private static void pushExplodeList(org.domi.furnace.api.event.Event f, List<Block> bukkitList, List<BlockPos> blown) {
        ExplodeSync sync = EXPLODE_SYNC.get(f);
        if (sync == null) return;
        var now = positions(bukkitList);
        if (now.equals(sync.bukkit)) return;
        blown.clear();
        blown.addAll(now);
        sync.bukkit = now;
        sync.furnace = List.copyOf(blown);
    }

    /** 앞선 Furnace 처리기가 서버 목록을 바꿨으면 Bukkit 목록을 그것으로 다시 만든다. */
    private static void pullExplodeList(org.domi.furnace.api.event.Event f, List<Block> bukkitList, String world, List<BlockPos> blown) {
        ExplodeSync sync = EXPLODE_SYNC.get(f);
        if (sync == null || blown.equals(sync.furnace)) return;
        bukkitList.clear();
        bukkitList.addAll(bukkitBlockList(world, blown));
        sync.bukkit = positions(bukkitList);
        sync.furnace = List.copyOf(blown);
    }

    /**
     * 보호 이벤트(T-040a): 폭발(엔티티)·피스톤·블록 만들어짐·퍼짐·투사체 맞음. 취소는 Furnace 이벤트의 취소와 같다. 규칙(Paper 기준):
     *  - EntityExplodeEvent: BlockExplodeEvent 와 같다 — blockList() 를 고치면 서버가 그 목록을 부순다, yield 는 읽기만.
     *  - 피스톤: getBlocks() 는 바꿀 수 없는 목록(밀 블록 + 부술 블록). 방향은 확장이면 피스톤이 바라보는 쪽, 블록을 끄는 수축이면 그 반대(미는 방향),
     *    끌 블록이 없는 수축(일반 피스톤)은 바라보는 쪽이다 — Paper 의 PistonBaseBlock 패치가 두 자리에서 다르게 넘긴다.
     *  - BlockFormEvent/BlockSpreadEvent 는 처리기 목록이 따로다. Furnace 쪽은 BlockSpread 가 BlockForm 의 하위라 BlockForm 짝은 하위 이벤트를 건너뛴다.
     */
    private static void registerProtection() {
        EventBridge.reg(org.domi.furnace.api.event.entity.EntityExplodeEvent.class, org.bukkit.event.entity.EntityExplodeEvent.class,
                f -> {
                    var who = Compat.entity(f.entity());
                    if (who == null) return null;
                    return new org.bukkit.event.entity.EntityExplodeEvent(who, Compat.location(f.location()), explodeList(f, f.location().world(), f.blocks()),
                            f.yield(), org.bukkit.ExplosionResult.valueOf(f.result()));
                },
                (f, b) -> pushExplodeList(f, b.blockList(), f.blocks()),
                (f, b) -> pullExplodeList(f, b.blockList(), f.location().world(), f.blocks()));
        EventBridge.reg(org.domi.furnace.api.event.block.BlockPistonExtendEvent.class, org.bukkit.event.block.BlockPistonExtendEvent.class,
                f -> new org.bukkit.event.block.BlockPistonExtendEvent(Compat.block(f.world(), f.pos()), pistonBlocks(f.world(), f.blocks()), Mappings.face(f.direction())),
                (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockPistonRetractEvent.class, org.bukkit.event.block.BlockPistonRetractEvent.class,
                f -> {
                    var facing = Mappings.face(f.direction());
                    return new org.bukkit.event.block.BlockPistonRetractEvent(Compat.block(f.world(), f.pos()), pistonBlocks(f.world(), f.blocks()),
                            f.blocks().isEmpty() ? facing : facing.getOppositeFace());
                }, (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockFormEvent.class, org.bukkit.event.block.BlockFormEvent.class,
                f -> f instanceof org.domi.furnace.api.event.block.BlockSpreadEvent ? null
                        : new org.bukkit.event.block.BlockFormEvent(Compat.block(f.world(), f.pos()), CompatBlockState.of(f.world(), f.pos(), f.newData())),
                (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.block.BlockSpreadEvent.class, org.bukkit.event.block.BlockSpreadEvent.class,
                f -> new org.bukkit.event.block.BlockSpreadEvent(Compat.block(f.world(), f.pos()), Compat.block(f.world(), f.source()),
                        CompatBlockState.of(f.world(), f.pos(), f.newData())), (f, b) -> {});
        EventBridge.reg(org.domi.furnace.api.event.entity.ProjectileHitEvent.class, org.bukkit.event.entity.ProjectileHitEvent.class,
                f -> {
                    if (!(Compat.entity(f.entity()) instanceof org.bukkit.entity.Projectile projectile)) return null;   // 투사체가 아닌 종류는 Bukkit 이 이 이벤트를 내지 않는다
                    var hitEntity = f.hitEntity() == null ? null : Compat.entity(f.hitEntity());
                    var hit = f.hitBlock();
                    return new org.bukkit.event.entity.ProjectileHitEvent(projectile, hitEntity, hit == null ? null : Compat.block(hit.world(), hit.pos()),
                            hit == null ? null : Mappings.face(f.hitFace()));
                }, (f, b) -> {});
    }

    private static List<Block> pistonBlocks(String world, List<BlockPos> positions) {
        List<Block> out = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) out.add(Compat.block(world, pos));
        return java.util.Collections.unmodifiableList(out);   // CraftBukkit 의 목록처럼 바꿀 수 없다
    }

    /** 서버가 원래 블록을 알리지 않은 옛 생성자의 이벤트: 소유 스레드면 월드의 지금 블록, 아니면(읽을 수 없으면) 공기로 본다. */
    private static CompatBlockState liveOrAir(String world, BlockPos pos) {
        try {
            return CompatBlockState.of(world, pos);
        } catch (RuntimeException cannotRead) {
            return CompatBlockState.of(world, pos, org.domi.furnace.api.world.BlockData.AIR);
        }
    }

    private static List<BlockPos> positions(List<Block> blocks) {
        List<BlockPos> out = new ArrayList<>(blocks.size());
        for (Block b : blocks) out.add(new BlockPos(b.getX(), b.getY(), b.getZ()));
        return out;
    }

    /** Paper 가 EntitySpawnEvent 대신 다른 이벤트로 내는 종류(탈것·번개). 호환 CraftEntity 가 Vehicle 을 구현하지 않아 종류 이름으로 본다. */
    static boolean notEntitySpawnKind(String type) {
        return type != null && (type.endsWith("boat") || type.endsWith("raft") || type.endsWith("minecart") || type.equals("minecraft:lightning_bolt"));
    }

    private static void registerEntities() {
        // 처리기 목록은 EntitySpawnEvent 하나(ItemSpawnEvent·CreatureSpawnEvent 는 그 목록을 같이 쓴다). 서버의 하위 이벤트(아이템·살아 있는 것·투사체)도
        // EntitySpawnEvent 구독자에게 오므로 하나로 받아 알맞은 Bukkit 종류를 만든다. 플레이어는 Bukkit 도 이 이벤트를 내지 않는다.
        EventBridge.reg(org.domi.furnace.api.event.entity.EntitySpawnEvent.class, org.bukkit.event.entity.EntitySpawnEvent.class,
                f -> {
                    var e = Compat.entity(f.entity());
                    if (e == null || e instanceof org.bukkit.entity.Player) return null;
                    // Paper doEntityAddEventCalling: 투사체는 ProjectileLaunchEvent, 탈것은 VehicleCreateEvent, 번개는 LightningStrikeEvent 로 나가고
                    // EntitySpawnEvent 는 그 밖의 것만 받는다. 서버의 ProjectileLaunchEvent 는 EntitySpawnEvent 의 하위라 여기로 오므로 걸러 낸다.
                    if (f instanceof org.domi.furnace.api.event.entity.ProjectileLaunchEvent || notEntitySpawnKind(f.entity().type())) return null;
                    if (f instanceof org.domi.furnace.api.event.entity.ItemSpawnEvent && e instanceof org.bukkit.entity.Item item) {
                        return new org.bukkit.event.entity.ItemSpawnEvent(item);
                    }
                    if (f instanceof org.domi.furnace.api.event.entity.CreatureSpawnEvent c && e instanceof org.bukkit.entity.LivingEntity le) {
                        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason;
                        try {
                            reason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.valueOf(c.reason().name());
                        } catch (IllegalArgumentException unknown) {
                            reason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT;
                        }
                        return new org.bukkit.event.entity.CreatureSpawnEvent(le, reason);
                    }
                    return new org.bukkit.event.entity.EntitySpawnEvent(e);
                },
                (f, b) -> {});
        // CraftBukkit: 던질 아이템 엔티티를 만든 뒤 이벤트를 부르고, 취소하면 아이템은 인벤토리로 돌아가고 엔티티는 들어가지 않는다(서버가 처리).
        EventBridge.reg(org.domi.furnace.api.event.player.PlayerDropItemEvent.class, org.bukkit.event.player.PlayerDropItemEvent.class,
                f -> {
                    if (f.entity() == null) return null;   // 아이템 엔티티가 없는 경로는 Bukkit Item 을 만들 수 없다
                    return Compat.entity(f.entity()) instanceof org.bukkit.entity.Item item ? new org.bukkit.event.player.PlayerDropItemEvent(p(f.player()), item) : null;
                },
                (f, b) -> {});
    }
}
