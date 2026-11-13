package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.projectiles.ProjectileSource;

/**
 * 투사체(화살·눈덩이·불덩이·물약·낚시찌 등). 쏜 쪽은 Furnace 의 원인 사슬(SHOOT)에서 읽는다 — 플레이어는 늘, 그 밖의 엔티티는
 * 이 어댑터가 이미 감싼 적이 있는 경우만 돌려준다(없으면 null). 서브 인터페이스(Arrow·Snowball 등)는 아직 없다 — 종류는 getType() 으로. 플레이어가 아닌 쏜 쪽은 그 엔티티를 이미 감싼 적이 있을 때만 돌려준다(약한 참조 캐시 — 실행 이력에 따라 null 일 수 있다). 디스펜서가 쏜 것은 Paper 의 BlockProjectileSource 대신 null.
 */
public interface Projectile extends Entity {

    /** 쏜 쪽(모르면 null). */
    ProjectileSource getShooter();

    /** 쏜 쪽을 바꾸는 길은 Furnace 에 없다 — UnsupportedFeature. */
    void setShooter(ProjectileSource source);

    /** 쏜 쪽의 UUID(Paper). 모르면 null. */
    UUID getOwnerUniqueId();

    /** Paper: 쏜 쪽에서 벗어났는가. Furnace 핸들에 읽는 길이 없어 UnsupportedFeature. */
    boolean hasLeftShooter();

    void setHasLeftShooter(boolean leftShooter);

    boolean hasBeenShot();

    void setHasBeenShot(boolean beenShot);

    boolean canHitEntity(Entity entity);

    void hitEntity(Entity entity);

    void hitEntity(Entity entity, org.bukkit.util.Vector vector);

    /** @deprecated Bukkit 도 쓰지 않는 옛 값 — 늘 false. */
    @Deprecated
    boolean doesBounce();

    /** @deprecated 아무 일도 하지 않는다(Bukkit 과 같다). */
    @Deprecated
    void setBounce(boolean doesBounce);
}
