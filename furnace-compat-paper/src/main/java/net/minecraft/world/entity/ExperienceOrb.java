package net.minecraft.world.entity;

import org.bukkit.craftbukkit.entity.CraftEntity;

/** 경험치 구슬(Mojang 이름 shim, NMS shim 5번 묶음). {@code CraftExperienceOrb#getHandle()} 이 준다 — 종류 판별과 {@link Entity} 의 자리 읽기·{@code moveTo} 용이다. */
public class ExperienceOrb extends Entity {

    /** 호환 모듈 전용(CraftExperienceOrb 가 만든다). */
    public ExperienceOrb(CraftEntity bukkit) { super(bukkit); }
}
