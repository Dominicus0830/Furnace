// 바닐라 EnchantingTableBlock: 마법 부여대. 누르면 마법 부여 화면(둘레 책장 수는 메뉴가 센다).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class EnchantingTableBlock extends BaseEntityBlock {

    /** BOOKSHELF_OFFSETS: 둘레 5×5 고리(높이 0..1). */
    public static final java.util.List<Pos> BOOKSHELF_OFFSETS;
    static {
        java.util.List<Pos> l = new java.util.ArrayList<>();
        for (int y = 0; y <= 1; y++) for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
            if (Math.abs(x) == 2 || Math.abs(z) == 2) l.add(new Pos(x, y, z));
        }
        BOOKSHELF_OFFSETS = java.util.List.copyOf(l);
    }

    public EnchantingTableBlock(int block) { super(block); }

    /** isValidBookShelf: 그 자리가 #enchantment_power_provider 이고 사이 칸이 #enchantment_power_transmitter. */
    public static boolean isValidBookShelf(BlockLevel level, Pos pos, Pos offset) {
        return B.isTag(level.getBlockState(pos.offset(offset.x(), offset.y(), offset.z())), "minecraft:enchantment_power_provider")
                && B.isTag(level.getBlockState(pos.offset(offset.x() / 2, offset.y(), offset.z() / 2)), "minecraft:enchantment_power_transmitter");
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        player.openEnchanting(pos);   // TODO(블록 엔티티): 화면 이름은 블록 엔티티의 사용자 이름
        return InteractionResult.CONSUME;
    }
}
