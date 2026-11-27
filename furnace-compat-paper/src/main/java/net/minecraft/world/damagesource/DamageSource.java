package net.minecraft.world.damagesource;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 피해의 출처(Paper 1.21.1 DamageSource 와 같은 필드·규칙). 형식 손잡이 + 원인 엔티티(causing, 책임자) + 직접 엔티티(direct, 실제로 맞힌 것) + 명시 위치,
 * 그리고 Paper 가 더한 이벤트용 표시(sweep·melting·poison·critical, 직접 블록, 이벤트 가해자). 값 객체라 아무 스레드에서 만들고 읽어도 된다.
 * 엔티티·아이템 쪽을 읽는 {@code getWeaponItem}·{@code getLocalizedDeathMessage} 는 아직 손 구현이 없다(껍데기가 던진다).
 */
public class DamageSource {

    private final Holder<DamageType> type;
    private final Entity causingEntity;
    private final Entity directEntity;
    private final Vec3 damageSourcePosition;
    // Paper 이벤트용 표시
    private org.bukkit.block.Block directBlock;
    private org.bukkit.block.BlockState directBlockState;
    private boolean sweep = false;
    private boolean melting = false;
    private boolean poison = false;
    private Entity customEventDamager = null;
    private boolean critical;

    public DamageSource(Holder<DamageType> type, Entity directEntity, Entity causingEntity, Vec3 position) {
        this.type = type;
        this.causingEntity = causingEntity;
        this.directEntity = directEntity;
        this.damageSourcePosition = position;
    }

    public DamageSource(Holder<DamageType> type, Entity directEntity, Entity causingEntity) {
        this(type, directEntity, causingEntity, null);
    }

    public DamageSource(Holder<DamageType> type, Vec3 position) {
        this(type, null, null, position);
    }

    public DamageSource(Holder<DamageType> type, Entity entity) {
        this(type, entity, entity);
    }

    public DamageSource(Holder<DamageType> type) {
        this(type, null, null, null);
    }

    public DamageSource sweep() {
        this.sweep = true;
        return this;
    }

    public boolean isSweep() { return sweep; }

    public DamageSource melting() {
        this.melting = true;
        return this;
    }

    public boolean isMelting() { return melting; }

    public DamageSource poison() {
        this.poison = true;
        return this;
    }

    public boolean isPoison() { return poison; }

    public Entity getCustomEventDamager() { return customEventDamager != null ? customEventDamager : directEntity; }

    public DamageSource customEventDamager(Entity entity) {
        if (directEntity != null) throw new IllegalStateException("Cannot set custom event damager when direct entity is already set (report a bug to Paper)");
        DamageSource copy = cloneInstance();
        copy.customEventDamager = entity;
        return copy;
    }

    public org.bukkit.block.Block getDirectBlock() { return directBlock; }

    public DamageSource directBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (pos == null || level == null) return this;
        return directBlock(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos));
    }

    public DamageSource directBlock(org.bukkit.block.Block block) {
        if (block == null) return this;
        DamageSource copy = cloneInstance();
        copy.directBlock = block;
        return copy;
    }

    public org.bukkit.block.BlockState getDirectBlockState() { return directBlockState; }

    public DamageSource directBlockState(org.bukkit.block.BlockState state) {
        if (state == null) return this;
        DamageSource copy = cloneInstance();
        copy.directBlockState = state;
        return copy;
    }

    /** Paper: 형식·엔티티·위치와 직접 블록·sweep·poison·melting 만 옮긴다(critical·이벤트 가해자는 옮기지 않는다). */
    private DamageSource cloneInstance() {
        DamageSource copy = new DamageSource(type, directEntity, causingEntity, damageSourcePosition);
        copy.directBlock = getDirectBlock();
        copy.directBlockState = getDirectBlockState();
        copy.sweep = isSweep();
        copy.poison = isPoison();
        copy.melting = isMelting();
        return copy;
    }

    @Override
    public String toString() { return "DamageSource (" + type().msgId() + ")"; }

    public float getFoodExhaustion() { return type().exhaustion(); }

    public boolean isDirect() { return causingEntity == directEntity; }

    public Entity getDirectEntity() { return directEntity; }

    public Entity getEntity() { return causingEntity; }

    public String getMsgId() { return type().msgId(); }

    public boolean scalesWithDifficulty() {
        return switch (type().scaling()) {
            case NEVER -> false;
            case WHEN_CAUSED_BY_LIVING_NON_PLAYER -> causingEntity instanceof LivingEntity && !(causingEntity instanceof Player);
            case ALWAYS -> true;
        };
    }

    public boolean isCreativePlayer() {
        // 호환 계층의 Player 는 늘 ServerPlayer 다(능력치는 그 shim 에 있다)
        return getEntity() instanceof net.minecraft.server.level.ServerPlayer p && p.getAbilities().instabuild;
    }

    public Vec3 getSourcePosition() {
        if (damageSourcePosition != null) return damageSourcePosition;
        return directEntity != null ? directEntity.position() : null;
    }

    public Vec3 sourcePositionRaw() { return damageSourcePosition; }

    public boolean is(TagKey<DamageType> tag) { return type.is(tag); }

    public boolean is(ResourceKey<DamageType> key) { return type.is(key); }

    public DamageType type() { return type.value(); }

    public Holder<DamageType> typeHolder() { return type; }

    public boolean isCritical() { return critical; }

    public DamageSource critical() { return critical(true); }

    public DamageSource critical(boolean critical) {
        this.critical = critical;
        return this;
    }
}
