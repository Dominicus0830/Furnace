package net.minecraft.core.registries;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.domi.furnace.compat.nms.NmsBlocks;
import org.domi.furnace.compat.nms.NmsItems;

/**
 * 내장 레지스트리(Mojang 이름 shim, N0). 지금은 블록·아이템만 — 번호·이름·기본값(minecraft:air) 규칙은 바닐라 DefaultedMappedRegistry 와 같다.
 * 처음 읽을 때 서버 블록 표에서 만들므로 서버가 떠 있어야 한다. 다른 레지스트리는 없는 필드다(호출 위치에서 실패).
 */
public final class BuiltInRegistries {

    private BuiltInRegistries() {}

    public static final DefaultedRegistry<Block> BLOCK = NmsBlocks.registry();
    public static final DefaultedRegistry<Item> ITEM = NmsItems.registry();
}
