package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 블록을 놓는 아이템(손 조각, T-231 — 머리·생성자는 shim 생성기가 붙인다). 아이템 객체는 종류 표대로 바닐라 클래스다(블록 아이템은 {@code BlockItem} 이거나
 * 그 하위 클래스). {@code getBlock} 은 명세 줄(블록 표의 같은 이름 블록)이고 여기는 Axiom 블록 놓기가 부르는 정적 도우미(NMS shim 5d)만 있다.
 */
class BlockItem {

    /**
     * 바닐라(Paper): 아이템의 block_entity_data 를 그 칸 블록 엔티티에 싣는다. 관리자만 쓸 수 있는 종류(명령 블록 등)는 player 가 관리자 블록을 쓸 수 있거나
     * (크리에이티브 + 권한 2), Paper 처럼 즉시 짓기이고 {@code minecraft.nbt.place} 권한이 있을 때만. 실었으면 참. 서버의 같은 코드를 그 칸 주인 스레드에서
     * 부른다({@link org.domi.furnace.compat.nms.NmsOwner} 규칙). 로드되지 않은 칸은 IllegalStateException.
     */
    public static boolean updateCustomBlockEntityTag(Level world, Player player, BlockPos pos, ItemStack stack) {
        // 검사 종류 read=any: 정적 메서드라 소유 검사를 여기서 하지 않고 NmsEffects 가 그 칸의 주인 스레드 규칙을 따른다
        if (!(world instanceof net.minecraft.server.level.ServerLevel)) return false;   // 바닐라: getServer() 가 null 이면 거짓
        boolean op = player != null && (player.canUseGameMasterBlocks() || (instabuild(player) && player.getBukkitEntity().hasPermission("minecraft.nbt.place")));
        return org.domi.furnace.compat.nms.NmsEffects.place(world, pos, org.domi.furnace.api.internal.ChunkInternals.PLACE_CUSTOM_DATA, 0, null,
                (stack == null ? ItemStack.EMPTY : stack).furnaceValue(), op, "BlockItem#updateCustomBlockEntityTag") == 1;
    }

    private static boolean instabuild(Player player) {
        return player instanceof net.minecraft.server.level.ServerPlayer sp ? sp.getAbilities().instabuild : player.isCreative();
    }
}
