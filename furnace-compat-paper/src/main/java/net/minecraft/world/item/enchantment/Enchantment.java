package net.minecraft.world.item.enchantment;

import net.minecraft.resources.ResourceLocation;

/**
 * 마법 부여 종류(Mojang 이름 shim, NMS shim 5번 묶음). Furnace 에서 마법 부여는 이름({@code minecraft:sharpness})과 레벨뿐이라 이 객체는 이름만 든다 —
 * {@link ItemEnchantments} 가 주는 {@code Holder<Enchantment>} 의 값이다. 바닐라의 효과·설명·최대 레벨 같은 멤버는 아직 없다(호출 위치에서 실패).
 */
public final class Enchantment {

    private final ResourceLocation id;

    /** 호환 모듈 전용. */
    public Enchantment(ResourceLocation id) { this.id = id; }

    /** 호환 모듈 전용 — 이 마법 부여의 이름. */
    public ResourceLocation furnaceId() { return id; }

    @Override
    public String toString() { return "Enchantment{" + id + "}"; }
}
