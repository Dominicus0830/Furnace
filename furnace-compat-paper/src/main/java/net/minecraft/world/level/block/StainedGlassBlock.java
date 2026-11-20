package net.minecraft.world.level.block;

import net.minecraft.world.item.DyeColor;
import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.api.world.BlockTypes;

/**
 * 색유리(Mojang 이름 shim): 열여섯 색 {@code *_stained_glass} 블록의 클래스 — 플러그인이 {@code instanceof StainedGlassBlock} 으로 가른다.
 * 바닐라는 TransparentBlock·BeaconBeamBlock 도 잇지만 shim 에는 그 중간 클래스가 없다(색 {@link #getColor()} 는 있다). 색유리판은 다른 클래스다.
 */
public class StainedGlassBlock extends Block {

    private final DyeColor color;

    /** 호환 모듈 전용 — 블록 shim 표가 만든다. */
    public StainedGlassBlock(BlockType type, BlockTypes types) {
        super(type, types);
        String path = type.id().substring(type.id().indexOf(':') + 1);
        String name = path.substring(0, path.length() - "_stained_glass".length());
        DyeColor found = null;
        for (DyeColor c : DyeColor.values()) if (c.getName().equals(name)) found = c;
        this.color = java.util.Objects.requireNonNull(found, type.id());
    }

    public DyeColor getColor() { return color; }
}
