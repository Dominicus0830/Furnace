// 바닐라 Arrow: 화살. 물약 효과(색)를 실을 수 있고 맞은 생물에게 효과를 건다. 
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Arrow extends AbstractArrow {
    public static final int DATA_EFFECT_COLOR = 10;

    public Arrow(ChunkLevel level, int id) { super(EntityType.ARROW, level, id); }

    /** Arrow(level, x, y, z, pickupItem, firingWeapon): 위치에 만든다. */
    public Arrow(ChunkLevel level, int id, double x, double y, double z) {
        this(level, id);
        setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_EFFECT_COLOR, SetEntityData.SERIALIZER_INT, -1);
    }

    /** 물약(potion) 효과(맞히면 지속 시간 1/8)와 사용자 지정(custom) 효과(그대로). */
    private final java.util.List<org.domi.furnace.server.effect.MobEffectInstance> effects = new java.util.ArrayList<>(), custom = new java.util.ArrayList<>();

    /** Arrow.addEffect: 사용자 지정 효과를 더한다(색도 갱신: 첫 효과 색). */
    public void addEffect(org.domi.furnace.server.effect.MobEffectInstance e) {
        custom.add(e);
        entityData.set(DATA_EFFECT_COLOR, e.effect().color());
    }

    /** Arrow(level, owner, pickupItem, weapon): 줍는 아이템의 potion_contents(물부리 화살)를 싣고 색을 맞춘다. */
    public void setPotionFromItem(ItemStack pickup) {
        if (!(org.domi.furnace.server.item.ItemTools.component(pickup, "minecraft:potion_contents") instanceof java.util.Map<?, ?> pc)) return;
        if (pc.get("potion") instanceof String id) for (var e : org.domi.furnace.server.effect.Potions.effectsOf(id)) effects.add(e.deepCopy());
        if (pc.get("custom_effects") instanceof java.util.List<?> ce) for (Object o : ce) if (o instanceof java.util.Map<?, ?> m) custom.add(Consuming.fromMap(m));
        int color = pc.get("custom_color") instanceof Number n ? n.intValue() : Consuming.potionColor(pickup);
        entityData.set(DATA_EFFECT_COLOR, color);
    }

    /** Arrow.setPickupItemStack: 줍는 아이템을 바꾸면 효과·색을 새 스택의 potion_contents 로 다시 정한다(일반 화살이면 색 -1). */
    @Override
    public void setPickupItemStack(ItemStack stack) {
        super.setPickupItemStack(stack);
        effects.clear();
        custom.clear();
        entityData.set(DATA_EFFECT_COLOR, -1);
        setPotionFromItem(getPickupItem());
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        for (var e : effects) {
            int duration = e.isInfiniteDuration() ? e.duration() : Math.max(e.duration() / 8, 1);
            target.addEffect(new org.domi.furnace.server.effect.MobEffectInstance(e.effect(), duration, e.amplifier(), e.ambient(), e.visible()));
        }
        for (var e : custom) target.addEffect(e.deepCopy());
    }

    @Override
    protected ItemStack getDefaultPickupItem() { return new ItemStack(ItemContext.id("minecraft:item", "arrow"), 1); }
}
