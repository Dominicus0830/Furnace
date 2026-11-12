package net.minecraft.core.registries;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.domi.furnace.compat.nms.NmsFluids;
import org.domi.furnace.compat.nms.NmsBlocks;
import org.domi.furnace.compat.nms.NmsItems;
import org.domi.furnace.compat.nms.NmsMobEffects;
import org.domi.furnace.compat.nms.NmsSounds;

/**
 * 내장 레지스트리(Mojang 이름 shim, N0). 지금은 블록·아이템·유체(바닐라 DefaultedMappedRegistry 규칙, 기본값 minecraft:air, 유체는 minecraft:empty)와
 * 소리·상태 효과(바닐라 MappedRegistry 규칙, 기본값 없음 — easy-nms E4c-1b)만.
 * 처음 읽을 때 서버 블록 표에서 만들므로 서버가 떠 있어야 한다. 다른 레지스트리는 없는 필드다(호출 위치에서 실패).
 */
public final class BuiltInRegistries {

    private BuiltInRegistries() {}

    public static final DefaultedRegistry<Block> BLOCK = NmsBlocks.registry();
    public static final DefaultedRegistry<Item> ITEM = NmsItems.registry();
    public static final DefaultedRegistry<Fluid> FLUID = NmsFluids.registry();
    public static final Registry<SoundEvent> SOUND_EVENT = NmsSounds.registry();
    public static final Registry<MobEffect> MOB_EFFECT = NmsMobEffects.registry();
}
