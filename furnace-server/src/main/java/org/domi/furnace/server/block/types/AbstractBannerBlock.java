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
}
