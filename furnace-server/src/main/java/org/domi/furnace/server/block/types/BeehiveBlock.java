// 바닐라 BeehiveBlock: 벌집·벌통(honey_level 0..5). 가득 차면 가위로 벌집 조각 3개, 유리병으로 꿀병을 얻고 비운다.
// 연기(아래 모닥불)가 없으면 안의 벌을 모두 내보내고 근처 벌이 플레이어를 노린다. 부수면(실크 터치 계열 제외) 벌이 화나서 나온다.
package org.domi.furnace.server.block.types;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.entity.BeehiveBlockEntity;
import org.domi.furnace.server.block.entity.BeehiveBlockEntity.BeeReleaseStatus;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Bee;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;

public class BeehiveBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), HONEY_LEVEL = B.prop("honey_level");
    public static final int MAX_HONEY_LEVELS = 5;

    public BeehiveBlock(int block) { super(block); }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) { return new BeehiveBlockEntity(pos, state); }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return HONEY_LEVEL.intValue(state); }

    /** playerDestroy: #prevents_bee_spawns_when_mining(실크 터치) 인챈트가 없으면 안의 벌을 비상으로 내보내고 근처 벌이 부순 사람을 노린다. */
    @Override
    public void playerDestroy(BlockLevel level, BlockActor player, Pos pos, int state, Object blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (blockEntity instanceof BeehiveBlockEntity hive) {
            if (!org.domi.furnace.server.item.ItemTools.hasEnchantmentTag(tool, "minecraft:prevents_bee_spawns_when_mining")) {
                hive.emptyAllLivingFromHive(level, player != null && player.handle() instanceof Player p ? p : null, state, BeeReleaseStatus.EMERGENCY);
                level.updateNeighbourForOutputSignal(pos, block());
                angerNearbyBees(level, pos);
            }
            if (player != null && player.handle() instanceof Player sp) {   // 바닐라: 내보낸 뒤의 남은 벌 수로 bee_nest_destroyed
                var block = org.domi.furnace.server.ServerCore.lootBlockState(state);
                var toolSnap = org.domi.furnace.server.ServerCore.lootStackOf(tool);
                int bees = hive.getOccupantCount();
                sp.triggerAdvancement(a -> a.triggerBeeNest(block, toolSnap, bees));
            }
        }
    }

    /** angerNearbyBees: 둘레(8,6,8) 안 대상 없는 벌마다 그 안의 플레이어 하나(레벨 난수)를 노린다. */
    private static void angerNearbyBees(BlockLevel level, Pos pos) {
        if (!(level instanceof ChunkLevel cl)) return;
        AABB box = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1).inflate(8.0, 6.0, 8.0);
        List<Bee> bees = cl.getEntitiesOfClass(Bee.class, box, b -> true);
        if (bees.isEmpty()) return;
        List<Player> players = new ArrayList<>();
        cl.forEachPlayerIn(box, players::add);
        if (players.isEmpty()) return;
        for (Bee bee : bees) {
            if (bee.getTarget() == null) bee.setTarget(players.get(level.random().nextInt(players.size())));
        }
    }

    /** dropHoneycomb: 벌집 조각 3개. */
    public static void dropHoneycomb(BlockLevel level, Pos pos) { level.popResource(pos, B.stack("honeycomb", 3)); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        boolean used = false;
        if (HONEY_LEVEL.intValue(state) >= MAX_HONEY_LEVELS) {
            if (B.holds(stack, "shears")) {
                level.playSound(player, player.x(), player.y(), player.z(), "minecraft:block.beehive.shear", "blocks", 1.0F, 1.0F);
                dropHoneycomb(level, pos);
                org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);
                used = true;
                level.gameEvent("minecraft:shear", pos, player, -1);
            } else if (B.holds(stack, "glass_bottle")) {
                ItemStack rest = stack.withCount(stack.count() - 1);
                level.playSound(player, player.x(), player.y(), player.z(), "minecraft:item.bottle.fill", "blocks", 1.0F, 1.0F);
                ItemStack honey = B.stack("honey_bottle", 1);
                if (rest.isEmpty()) {
                    player.setItemInHand(hand, honey);
                } else {
                    player.setItemInHand(hand, rest);
                    if (!player.addToInventory(honey)) player.drop(honey);
                }
                used = true;
                level.gameEvent("minecraft:fluid_pickup", pos, player, -1);
            }
            if (used) player.awardStat("minecraft:used:" + org.domi.furnace.server.item.ItemTools.itemName(stack));
        }
        if (!used) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!CampfireBlock.isSmokeyPos(level, pos)) {
            if (hiveContainsBees(level, pos)) angerNearbyBees(level, pos);
            releaseBeesAndResetHoneyLevel(level, state, pos, player != null && player.handle() instanceof Player p ? p : null, BeeReleaseStatus.EMERGENCY);
        } else {
            resetHoneyLevel(level, state, pos);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    private static boolean hiveContainsBees(BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive && !hive.isEmpty();
    }

    /** releaseBeesAndResetHoneyLevel: 꿀을 비우고(원래 상태로) 안의 벌을 모두 내보낸다. */
    public void releaseBeesAndResetHoneyLevel(BlockLevel level, int state, Pos pos, Player player, BeeReleaseStatus status) {
        resetHoneyLevel(level, state, pos);
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) hive.emptyAllLivingFromHive(level, player, state, status);
    }

    public void resetHoneyLevel(BlockLevel level, int state, Pos pos) { level.setBlock(pos, HONEY_LEVEL.withInt(state, 0), 3); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    /** updateShape: 이웃 칸이 불이면 벌을 모두 비상으로 내보낸다. */
    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (B.is(level.getBlockState(neighborPos), "minecraft:fire") && level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
            hive.emptyAllLivingFromHive(level, null, state, BeeReleaseStatus.EMERGENCY);
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** getDrops 의 폭발 쪽: TNT·크리퍼·위더 해골·위더·TNT 수레가 부수면 벌을 비상으로 내보낸다(Explosion 이 부른다). */
    public static void onExplodedBy(Object source, Object blockEntity, int state, BlockLevel level) {
        if (blockEntity instanceof BeehiveBlockEntity hive && (source instanceof org.domi.furnace.server.entity.PrimedTnt
                || source instanceof org.domi.furnace.server.entity.Creeper || source instanceof org.domi.furnace.server.entity.WitherSkull
                || source instanceof org.domi.furnace.server.entity.WitherBoss || source instanceof org.domi.furnace.server.entity.MinecartTNT)) {
            hive.emptyAllLivingFromHive(level, null, state, BeeReleaseStatus.EMERGENCY);
        }
    }

    /**
     * playerWillDestroy: 크리에이티브 플레이어가 doTileDrops 일 때 부수면, 벌이 있거나 꿀이 차 있으면 블록 아이템에 블록 엔티티 컴포넌트(bees)와
     * block_state{honey_level} 을 실어 칸 모서리(정수 좌표)에 떨군다(기본 줍기 지연). 실크 터치 드롭은 전리품 표의 copy_components 가 한다.
     */
    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (level instanceof ChunkLevel cl && player.isCreative() && level.gameRule("doTileDrops")
                && level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
            int honey = HONEY_LEVEL.intValue(state);
            boolean hasBees = !hive.isEmpty();
            if (hasBees || honey > 0) {
                ItemStack stack = B.stack(B.BLOCKS.nameOfState(state), 1);
                for (var e : hive.collectComponents().entrySet()) stack = org.domi.furnace.server.item.ItemTools.with(stack, e.getKey(), e.getValue());
                stack = org.domi.furnace.server.item.ItemTools.with(stack, "minecraft:block_state", java.util.Map.of("honey_level", Integer.toString(honey)));
                var ie = new org.domi.furnace.server.entity.ItemEntity(cl, cl.nextEntityId(), pos.x(), pos.y(), pos.z(), stack);
                ie.setDefaultPickUpDelay();
                cl.addFreshEntity(ie);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
