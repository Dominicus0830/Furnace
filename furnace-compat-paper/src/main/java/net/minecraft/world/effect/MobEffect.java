package net.minecraft.world.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 상태 효과(Mojang 이름 shim, easy-nms E4c-1b). 읽기 멤버만 — 분류·색·즉시 여부·번역 키·이름. 값은 {@link MobEffects}·{@code BuiltInRegistries.MOB_EFFECT}
 * 의 같은 객체다. 틱 동작(applyEffectTick 등)·속성 수정자는 서버가 하므로 두지 않았다(부르면 없는 멤버). 플러그인이 새 효과를 만들어도 레지스트리에
 * 넣을 수 없는 것은 바닐라와 같다(얼어 있다).
 */
public class MobEffect {

    private final MobEffectCategory category;
    private final int color;
    private String descriptionId;

    protected MobEffect(MobEffectCategory category, int color) {
        this.category = category;
        this.color = color;
    }

    /** 즉시 효과인가. 바닐라처럼 기본 false — 즉시 치유·피해·포화는 {@link InstantenousMobEffect}. */
    public boolean isInstantenous() { return false; }

    public MobEffectCategory getCategory() { return category; }

    public boolean isBeneficial() { return category == MobEffectCategory.BENEFICIAL; }

    public int getColor() { return color; }

    /** 바닐라 Util.makeDescriptionId("effect", 레지스트리 이름) — 등록되지 않은 효과면 "effect.unregistered_sadface". */
    protected String getOrCreateDescriptionId() {
        if (descriptionId == null) {
            ResourceLocation k = BuiltInRegistries.MOB_EFFECT.getKey(this);
            descriptionId = k == null ? "effect.unregistered_sadface" : "effect." + k.getNamespace() + "." + k.getPath().replace('/', '.');
        }
        return descriptionId;
    }

    public String getDescriptionId() { return getOrCreateDescriptionId(); }

    public Component getDisplayName() { return Component.translatable(getDescriptionId()); }
}
