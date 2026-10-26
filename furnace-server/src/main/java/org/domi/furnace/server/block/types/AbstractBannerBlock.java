// 바닐라 AbstractBannerBlock: 깃발 바탕(색은 생성 인자 color, 무늬는 블록 엔티티).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Blocks;

public abstract class AbstractBannerBlock extends BaseEntityBlock {

    protected final String color;

    protected AbstractBannerBlock(int block) {
        super(block);
        this.color = Blocks.get().params(block).get("color").getAsString();
    }

    public String color() { return color; }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(org.domi.furnace.server.block.Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.BannerBlockEntity(pos, state);
    }

    /** 아이템의 custom_name 을 블록 엔티티 이름으로. 무늬(banner_patterns)는 아직 옮기지 않는다 — TODO(깃발). */
    @Override
    public void setPlacedBy(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos, int state,
            org.domi.furnace.server.block.BlockActor placer, org.domi.furnace.server.entity.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.BannerBlockEntity be
                && org.domi.furnace.server.item.ItemTools.component(stack, "minecraft:custom_name") instanceof String name) be.customName = name;
    }
}
