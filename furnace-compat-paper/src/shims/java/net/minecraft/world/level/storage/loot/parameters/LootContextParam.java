package net.minecraft.world.level.storage.loot.parameters;

import net.minecraft.resources.ResourceLocation;

/** 전리품 매개변수 키(바닐라 {@code LootContextParam} — 이름만 가진다. 비교는 객체 동일성이다). */
class LootContextParam {

    private final ResourceLocation name;

    public LootContextParam(ResourceLocation name) { this.name = name; }

    public ResourceLocation getName() { return name; }

    @Override
    public String toString() { return "<parameter " + name + ">"; }
}
