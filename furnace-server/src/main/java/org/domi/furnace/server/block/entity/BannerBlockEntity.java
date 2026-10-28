// 바닐라 BannerBlockEntity: 이름(CustomName, 컴포넌트 JSON 문자열)과 무늬(patterns — banner_patterns 컴포넌트 정규 값). 바탕 색은 블록에서 나온다.
package org.domi.furnace.server.block.entity;

import java.util.List;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.AbstractBannerBlock;
import org.domi.furnace.server.item.DataComponents;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class BannerBlockEntity extends BlockEntity {

    public String customName;
    /** BannerPatternLayers(정규 값: [{pattern, color}]). 비면 기본값. */
    private Object patterns = List.of();

    public BannerBlockEntity(Pos pos, int state) { super("minecraft:banner", pos, state); }

    @Override
    public boolean isValid(int state) { return BlockBehaviors.of(state) instanceof AbstractBannerBlock; }

    /** getBaseColor: 블록의 색(DyeColor 이름). */
    public String baseColor() {
        return BlockBehaviors.of(blockState) instanceof AbstractBannerBlock b ? b.color() : "white";
    }

    public Object patterns() { return patterns; }

    private static boolean empty(Object p) { return p == null || p instanceof List<?> l && l.isEmpty(); }

    @Override
    public void load(NbtCompound tag) {
        if (tag.tags().get("CustomName") instanceof String s) customName = s;
        if (tag.contains("patterns")) {
            try {
                patterns = DataComponents.BANNER_PATTERNS.persistent().normalize(tag.tags().get("patterns"));
            } catch (RuntimeException e) {
                // Failed to parse banner patterns — 그대로 둔다
            }
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (!empty(patterns)) tag.put("patterns", DataComponents.BANNER_PATTERNS.persistent().toNbt(patterns));
        if (customName != null) tag.putString("CustomName", customName);
    }

    @Override
    protected void applyImplicitComponents(ComponentInput in) {
        patterns = in.getOrDefault("minecraft:banner_patterns", List.of());
        customName = in.get("minecraft:custom_name") instanceof String n ? n : null;
    }

    @Override
    protected void collectImplicitComponents(java.util.Map<String, Object> out) {
        out.put("minecraft:banner_patterns", patterns);
        out.put("minecraft:custom_name", customName);
    }

    /** getUpdateTag = saveWithoutMetadata(컴포넌트 포함) — 클라이언트가 무늬를 그린다. */
    @Override
    public NbtCompound updateTag() { return saveWithoutMetadata(); }

    @Override
    public boolean hasUpdatePacket() { return true; }
}
