package org.bukkit.entity;

import java.util.Collection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 살아 있는 엔티티. 효과·AI·밀림은 엔티티를 가진 region 스레드에서 지금 값을 읽고 쓴다(Entity 의 스레드 규칙과 같다). */
public interface LivingEntity extends Entity {

    double getHealth();

    double getMaxHealth();

    void setHealth(double health);

    /** 효과를 건다(바닐라 addEffect — 더 센·더 긴 쪽으로 갱신). Paper 처럼 늘 true. */
    boolean addPotionEffect(PotionEffect effect);

    /** @deprecated force 는 쓰지 않는다(Paper 와 같다). */
    @Deprecated
    boolean addPotionEffect(PotionEffect effect, boolean force);

    boolean addPotionEffects(Collection<PotionEffect> effects);

    boolean hasPotionEffect(PotionEffectType type);

    /** 걸린 그 종류의 효과(없으면 null). */
    PotionEffect getPotionEffect(PotionEffectType type);

    void removePotionEffect(PotionEffectType type);

    Collection<PotionEffect> getActivePotionEffects();

    /** 모든 효과를 지운다. 지운 것이 있으면 true. */
    boolean clearActivePotionEffects();

    /** AI 를 켜고 끈다(몹만 — 다른 엔티티는 아무 일도 없다, Paper 와 같다). */
    void setAI(boolean ai);

    /** AI 가 켜져 있는가(몹이 아니면 false). */
    boolean hasAI();

    /** 다른 엔티티에 밀리고 미는가(저장되지 않는다). */
    void setCollidable(boolean collidable);

    boolean isCollidable();
}
