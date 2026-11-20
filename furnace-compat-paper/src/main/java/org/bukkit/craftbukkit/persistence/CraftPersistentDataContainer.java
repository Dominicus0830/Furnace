package org.bukkit.craftbukkit.persistence;

import org.domi.furnace.compat.paper.LivePdc;

/**
 * CraftBukkit 영속 데이터 묶음(Paper 이름): 엔티티·블록 엔티티의 {@code PersistentDataContainer} 가 모두 이것이다. 플러그인이 반환형을
 * 이 클래스로 적어 둔 호출({@code CraftPlayer#getPersistentDataContainer})이 링크되도록 하는 얇은 뼈대 — 읽기·쓰기 규칙은 {@link LivePdc} 그대로다.
 */
public abstract class CraftPersistentDataContainer extends LivePdc {}
