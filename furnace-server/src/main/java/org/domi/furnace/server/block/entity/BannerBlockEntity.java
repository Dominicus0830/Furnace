// 바닐라 BannerBlockEntity: 이름(CustomName, 컴포넌트 JSON 문자열)과 무늬(patterns — 형식을 몰라도 그대로 보존). 바탕 색은 블록에서 나온다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.AbstractBannerBlock;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class BannerBlockEntity extends BlockEntity {

    public String customName;
    private Object patterns;

    public BannerBlockEntity(Pos pos, int state) { super("minecraft:banner", pos, state); }

    @Override
    public boolean isValid(int state) { return BlockBehaviors.of(state) instanceof AbstractBannerBlock; }

    /** getBaseColor: 블록의 색(DyeColor 이름). */
    public String baseColor() {
        return BlockBehaviors.of(blockState) instanceof AbstractBannerBlock b ? b.color() : "white";
    }

    @Override
    public void load(NbtCompound tag) {
        customName = tag.tags().get("CustomName") instanceof String s ? s : null;
        patterns = tag.tags().get("patterns");
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (customName != null) tag.putString("CustomName", customName);
        if (patterns != null) tag.put("patterns", patterns);
    }
}
