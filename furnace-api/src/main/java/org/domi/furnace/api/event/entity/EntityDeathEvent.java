// 몹(플레이어가 아닌 생물)이 죽는다 — 그 엔티티를 가진 region 스레드에서 불린다. 플레이어는 PlayerDeathEvent.
//
// 떨어뜨릴 아이템({@link #drops()})은 전리품 표·장비 드롭·안장 등 죽을 때 나올 아이템 목록이고, 바꾸면 실제 드롭에 반영된다(Paper 와 같다).
// 아이템은 이벤트가 끝난 뒤 목록 순서대로 나온다. 취소하면 죽지 않는다: 체력을 reviveHealth 로 되돌리고 드롭·경험치·사망 소리·죽는 모습이 없으며
// 장비도 그대로 남는다(Paper 와 같다 — 피글린 주머니처럼 전리품을 모으며 이미 비운 것은 돌아오지 않는다).
package org.domi.furnace.api.event.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.entity.LivingEntity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;

public final class EntityDeathEvent extends EntityEvent implements Cancellable {

    private final List<ItemStack> drops;
    private int droppedExp;
    private final float maxHealth;
    private float reviveHealth;
    private boolean cancelled;

    public EntityDeathEvent(LivingEntity entity) { this(entity, List.of(), 0, 0.0f); }

    /** @param maxHealth 죽는 순간 최대 체력 — 되살릴 체력의 기본값이자 상한. */
    public EntityDeathEvent(LivingEntity entity, List<ItemStack> drops, int droppedExp, float maxHealth) {
        super(entity);
        this.drops = new ArrayList<>(drops);
        this.droppedExp = droppedExp;
        this.maxHealth = maxHealth;
        this.reviveHealth = maxHealth;
    }

    @Override
    public LivingEntity entity() { return (LivingEntity) super.entity(); }

    /** 죽은 자리에 떨어뜨릴 아이템 — 바꿀 수 있는 목록. 빈 스택은 건너뛴다. 이벤트가 끝난 뒤 바꿔도 소용없다. */
    public List<ItemStack> drops() { return drops; }

    /** 떨어뜨릴 경험치(구슬로 나뉜다). 기본은 바닐라가 낼 양(플레이어가 잡지 않았거나 doMobLoot 가 꺼졌으면 0). 0 이하면 없다. */
    public int droppedExp() { return droppedExp; }

    public void droppedExp(int amount) { this.droppedExp = amount; }

    /** 취소했을 때 되살릴 체력. 기본은 죽는 순간 최대 체력. */
    public float reviveHealth() { return reviveHealth; }

    /** @throws IllegalArgumentException 0 이하(최대 체력이 0 이 아닐 때)이거나 최대 체력보다 크면(Paper 와 같다) */
    public void reviveHealth(float health) {
        if ((maxHealth != 0 && health <= 0) || health > maxHealth) {
            throw new IllegalArgumentException("되살릴 체력은 0 초과 " + maxHealth + " 이하여야 한다: " + health);
        }
        this.reviveHealth = health;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
