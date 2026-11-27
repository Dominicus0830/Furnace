package net.minecraft.world.entity;

import org.bukkit.craftbukkit.entity.CraftLivingEntity;

/** 살아 있는 엔티티 shim(Mojang 이름, H6 최소 표면). 체력 읽기·쓰기는 Furnace 엔티티 API 로 간다. */
class LivingEntity {

    @Override
    public CraftLivingEntity getBukkitEntity() { return (CraftLivingEntity) super.getBukkitEntity(); }

    public float getHealth() { return (float) getBukkitEntity().getHealth(); }

    public float getMaxHealth() { return (float) getBukkitEntity().getMaxHealth(); }

    /** 바닐라처럼 0 과 최대 체력 사이로 자른다. */
    public void setHealth(float health) {
        float max = getMaxHealth();
        getBukkitEntity().setHealth(Math.max(0f, Math.min(health, max)));
    }

    /**
     * 바닐라 {@code LivingEntity#getAttribute(Holder)}: 이 엔티티의 그 속성(가지지 않으면 null). 서버의 그 엔티티 속성을 보는 shim 이다 —
     * 주인 스레드면 곧바로 읽고 쓰고, 다른 스레드의 쓰기는 주인 스레드로 넘긴다(NMS shim 3번 묶음, {@link net.minecraft.world.entity.ai.attributes.AttributeInstance}).
     */
    public net.minecraft.world.entity.ai.attributes.AttributeInstance getAttribute(
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return net.minecraft.world.entity.ai.attributes.AttributeInstance.furnaceOf(
                (org.domi.furnace.api.entity.LivingEntity) getBukkitEntity().furnaceHandle(), attribute);
    }

    /** 바닐라 getAttributes: 이 엔티티의 속성 모음(서버의 그 엔티티 속성을 보는 보기). */
    public net.minecraft.world.entity.ai.attributes.AttributeMap getAttributes() {
        return net.minecraft.world.entity.ai.attributes.AttributeMap.furnaceOf(furnaceLiving());
    }

    /** 바닐라: 그 속성의 값. 가지지 않으면 IllegalArgumentException("Can't find attribute " + 이름) — 바닐라 AttributeSupplier 와 같다. */
    public double getAttributeValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return need(attribute).getValue();
    }

    /** 바닐라: 그 속성의 기본값(수정자를 뺀 값). 가지지 않으면 {@link #getAttributeValue} 와 같은 예외. */
    public double getAttributeBaseValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return need(attribute).getBaseValue();
    }

    private net.minecraft.world.entity.ai.attributes.AttributeInstance need(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        var i = getAttribute(attribute);
        if (i == null) throw new IllegalArgumentException("Can't find attribute " + attribute.getRegisteredName());
        return i;
    }

    private org.domi.furnace.api.entity.LivingEntity furnaceLiving() { return (org.domi.furnace.api.entity.LivingEntity) getBukkitEntity().furnaceHandle(); }

    // ---- 상태 효과(NmsEffects: 몹은 소유 스레드, 플레이어는 PlayerVitals). 읽은 MobEffectInstance 는 사본이다.

    /** 바닐라 getEffect: 그 효과의 사본(없으면 null). 숨은 효과는 담기지 않는다. */
    public net.minecraft.world.effect.MobEffectInstance getEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return org.domi.furnace.compat.nms.NmsMobEffectOps.get(furnaceLiving(), effect);
    }

    public boolean hasEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) { return getEffect(effect) != null; }

    /** 바닐라 getActiveEffects: 걸린 효과들의 사본(걸린 순서). */
    public java.util.Collection<net.minecraft.world.effect.MobEffectInstance> getActiveEffects() { return org.domi.furnace.compat.nms.NmsMobEffectOps.list(furnaceLiving()); }

    public java.util.Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, net.minecraft.world.effect.MobEffectInstance> getActiveEffectsMap() {
        return org.domi.furnace.compat.nms.NmsMobEffectOps.map(furnaceLiving());
    }

    /** 바닐라 addEffect: 서버가 바닐라 규칙(더 센 쪽·숨은 효과)으로 건다. 소유 스레드가 아닌 쓰기는 넘기고 true 를 돌려준다. */
    public final boolean addEffect(net.minecraft.world.effect.MobEffectInstance effect) { return org.domi.furnace.compat.nms.NmsMobEffectOps.add(furnaceLiving(), effect); }

    /** 바닐라 addEffect(효과, 준 엔티티): 준 엔티티는 효과 변경 발전 과제의 출처로만 쓰여 지금은 무시한다. */
    public boolean addEffect(net.minecraft.world.effect.MobEffectInstance effect, Entity source) { return addEffect(effect); }

    public boolean removeEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return org.domi.furnace.compat.nms.NmsMobEffectOps.remove(furnaceLiving(), effect);
    }

    public boolean removeAllEffects() { return org.domi.furnace.compat.nms.NmsMobEffectOps.removeAll(furnaceLiving()); }

    /**
     * 바닐라 {@code LivingEntity#getItemInHand}: 그 손의 스택 객체. 월드의 엔티티는 아직 이 길로 읽지 않는다(UnsupportedNmsCall — Bukkit
     * {@code getEquipment()} 를 쓴다). 바닐라 생성자로 만든 월드에 없는 플레이어는 된다({@code ServerPlayer}).
     */
    public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand hand) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#getItemInHand", "reading the live hand stack of an entity in a world is not"
                + " available yet - use the Bukkit equipment API");
    }

    /** 바닐라 {@code LivingEntity#getItemBySlot}: 그 장비 칸의 스택. 지원 범위는 {@link #getItemInHand} 와 같다(플레이어는 ServerPlayer 가 덮는다). */
    public net.minecraft.world.item.ItemStack getItemBySlot(EquipmentSlot slot) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#getItemBySlot", "reading the live equipment of an entity in a world is not"
                + " available yet - use the Bukkit equipment API");
    }

    /** 바닐라 {@code LivingEntity#getMainHandItem}: 주 손의 스택({@link #getItemInHand} 와 같은 범위 — 접속한 플레이어는 손 칸의 살아 있는 보기). */
    public net.minecraft.world.item.ItemStack getMainHandItem() { return getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND); }

    /** 바닐라 {@code LivingEntity#getOffhandItem}: 보조 손의 스택({@link #getItemInHand} 와 같은 범위). */
    public net.minecraft.world.item.ItemStack getOffhandItem() { return getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND); }

    /** 바닐라 {@code LivingEntity#setItemInHand}: 그 객체를 손에 둔다. 지원 범위는 {@link #getItemInHand} 와 같다. */
    public void setItemInHand(net.minecraft.world.InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#setItemInHand", "changing the live hand stack of an entity in a world is not"
                + " available yet - use the Bukkit equipment API");
    }

    /** 바닐라 LivingEntity: 지워지지 않았고 체력이 0 보다 크다. */
    @Override
    public boolean isAlive() { return !isRemoved() && getHealth() > 0f; }

    /** 바닐라 isFallFlying: 공유 플래그 7(겉날개로 나는 중). */
    public boolean isFallFlying() { org.domi.furnace.compat.nms.NmsEntityOwner.read(this, "LivingEntity#isFallFlying"); return (furnaceLook("LivingEntity#isFallFlying").sharedFlags() & 1 << 7) != 0; }

    // ------------------------------------------------------------------ 아이템 사용·맞은 흔들림(FancyNpcs)

    /** 생물 깃발(DATA_LIVING_ENTITY_FLAGS): 월드 밖 엔티티는 재생한 칸, 월드의 엔티티는 서버 값. */
    private byte furnaceLivingFlags(String what) {
        if (furnaceNotInWorld()) return (Byte) getEntityData().get(DATA_LIVING_ENTITY_FLAGS);
        return furnaceLook(what).livingFlags();
    }

    /** 바닐라 isUsingItem: 생물 깃발 1. */
    public boolean isUsingItem() {
        org.domi.furnace.compat.nms.NmsEntityOwner.read(this, "LivingEntity#isUsingItem");
        return (furnaceLivingFlags("LivingEntity#isUsingItem") & 1) != 0;
    }

    /** 바닐라 getUsedItemHand: 생물 깃발 2 면 다른 손(쓰기를 멈춰도 바닐라처럼 남는다). */
    public net.minecraft.world.InteractionHand getUsedItemHand() {
        org.domi.furnace.compat.nms.NmsEntityOwner.read(this, "LivingEntity#getUsedItemHand");
        return (furnaceLivingFlags("LivingEntity#getUsedItemHand") & 2) != 0 ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND;
    }

    /** 바닐라 startUsingItem(hand) = Paper startUsingItem(hand, false). */
    public void startUsingItem(net.minecraft.world.InteractionHand hand) {
        org.domi.furnace.compat.nms.NmsEntityOwner.write(this, "LivingEntity#startUsingItem");
        startUsingItem(hand, false);
    }

    /** Paper startUsingItem(hand, forceUpdate): 서버 엔티티가 같은 규칙으로 쓰기 시작한다(월드 밖이면 넣을 때 차례대로 재생). */
    public void startUsingItem(net.minecraft.world.InteractionHand hand, boolean forceUpdate) {
        org.domi.furnace.compat.nms.NmsEntityOwner.write(this, "LivingEntity#startUsingItem");
        furnaceLivingEdit("LivingEntity#startUsingItem", new org.domi.furnace.api.internal.ChunkInternals.EntityEdit.StartUsingItem(
                hand == net.minecraft.world.InteractionHand.OFF_HAND ? 1 : 0, forceUpdate));
    }

    /** 바닐라 stopUsingItem. */
    public void stopUsingItem() {
        org.domi.furnace.compat.nms.NmsEntityOwner.write(this, "LivingEntity#stopUsingItem");
        furnaceLivingEdit("LivingEntity#stopUsingItem", new org.domi.furnace.api.internal.ChunkInternals.EntityEdit.StopUsingItem());
    }

    /** 바닐라 animateHurt(yaw): 서버 엔티티의 hurtDuration·hurtTime 을 10 으로(yaw 는 바닐라도 쓰지 않는다). */
    public void animateHurt(float yaw) {
        org.domi.furnace.compat.nms.NmsEntityOwner.write(this, "LivingEntity#animateHurt");
        furnaceLivingEdit("LivingEntity#animateHurt", new org.domi.furnace.api.internal.ChunkInternals.EntityEdit.AnimateHurt());
    }

    /** 월드 밖이면 조작으로 기록하고(칸을 다시 채운다), 월드의 엔티티면 주인 스레드에서 서버 엔티티에 한다. */
    private <T extends org.domi.furnace.api.internal.ChunkInternals.EntityEdit & org.domi.furnace.api.internal.ChunkInternals.EntityOp> void furnaceLivingEdit(String what, T op) {
        if (furnaceNotInWorld()) furnaceDetachedOp(what, op);
        else furnaceEditOf(what, op);
    }
}
