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
                    List<Block> blocks = new ArrayList<>(f.blocks().size());
                    for (BlockPos pos : f.blocks()) blocks.add(Compat.block(f.world(), pos));
                    return new org.bukkit.event.block.BlockExplodeEvent(Compat.block(f.world(), f.pos()),
                            CompatBlockState.of(f.world(), f.pos(), type.get().defaultData()), blocks, f.yield(),
                            org.bukkit.ExplosionResult.valueOf(f.result()));
                },
                // 처리기가 blockList() 를 고쳤으면 서버가 부술 칸 목록을 그것으로(Paper 와 같다 — 넣은 칸도 부서진다)
                (f, b) -> {
                    var now = positions(b.blockList());
                    if (!now.equals(f.blocks())) {
                        f.blocks().clear();
                        f.blocks().addAll(now);
                    }
                }, (f, b) -> {   // 앞선 Furnace 처리기가 목록을 바꿨다 — 같은 목록으로 맞춘다
                    if (!positions(b.blockList()).equals(f.blocks())) {
                        b.blockList().clear();
                        for (BlockPos pos : f.blocks()) b.blockList().add(Compat.block(f.world(), pos));
                    }
                });
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

    private static void registerEntities() {
        // 처리기 목록은 EntitySpawnEvent 하나(ItemSpawnEvent·CreatureSpawnEvent 는 그 목록을 같이 쓴다). 서버의 하위 이벤트(아이템·살아 있는 것·투사체)도
        // EntitySpawnEvent 구독자에게 오므로 하나로 받아 알맞은 Bukkit 종류를 만든다. 플레이어는 Bukkit 도 이 이벤트를 내지 않는다.
        EventBridge.reg(org.domi.furnace.api.event.entity.EntitySpawnEvent.class, org.bukkit.event.entity.EntitySpawnEvent.class,
                f -> {
                    var e = Compat.entity(f.entity());
                    if (e == null || e instanceof org.bukkit.entity.Player) return null;
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
