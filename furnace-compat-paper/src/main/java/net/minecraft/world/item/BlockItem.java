package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 블록을 놓는 아이템(NMS shim 5d — Axiom 블록 놓기가 부르는 정적 도우미). 아직 아이템 종류별 클래스 사슬이 없어 아이템 객체는 모두 {@link Item} 이다
 * (블록 아이템도 {@code instanceof BlockItem} 이 거짓 — 아이템 종류 계층은 생성기 작업, 묶음 문서 5d-3).
 */
public class BlockItem extends Item {

    private BlockItem() { super("minecraft:air"); }

    /**
     * 바닐라(Paper): 아이템의 block_entity_data 를 그 칸 블록 엔티티에 싣는다. 관리자만 쓸 수 있는 종류(명령 블록 등)는 player 가 관리자 블록을 쓸 수 있거나
     * (크리에이티브 + 권한 2), Paper 처럼 즉시 짓기이고 {@code minecraft.nbt.place} 권한이 있을 때만. 실었으면 참. 서버의 같은 코드를 그 칸 주인 스레드에서
     * 부른다({@link org.domi.furnace.compat.nms.NmsOwner} 규칙). 로드되지 않은 칸은 IllegalStateException.
     */
    public static boolean updateCustomBlockEntityTag(Level world, Player player, BlockPos pos, ItemStack stack) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel)) return false;   // 바닐라: getServer() 가 null 이면 거짓
        boolean op = player != null && (player.canUseGameMasterBlocks() || (instabuild(player) && player.getBukkitEntity().hasPermission("minecraft.nbt.place")));
        return org.domi.furnace.compat.nms.NmsEffects.place(world, pos, org.domi.furnace.api.internal.ChunkInternals.PLACE_CUSTOM_DATA, 0, null,
                (stack == null ? ItemStack.EMPTY : stack).furnaceValue(), op, "BlockItem#updateCustomBlockEntityTag") == 1;
    }

    private static boolean instabuild(Player player) {
        return player instanceof net.minecraft.server.level.ServerPlayer sp ? sp.getAbilities().instabuild : player.isCreative();
    }
}
