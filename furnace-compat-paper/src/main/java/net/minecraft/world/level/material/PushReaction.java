package net.minecraft.world.level.material;

/** 피스톤에 밀릴 때의 반응(Mojang 이름 shim). 순서는 바닐라 1.21.1 {@code epj} 와 같다. */
public enum PushReaction {
    NORMAL,
    DESTROY,
    BLOCK,
    IGNORE,
    PUSH_ONLY
}
