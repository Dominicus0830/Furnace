package net.minecraft.world.effect;

/** 즉시 효과(Mojang 이름 shim, easy-nms E4c-1b) — 즉시 치유·즉시 피해·포화. 바닐라처럼 isInstantenous 가 true 다. */
public class InstantenousMobEffect extends MobEffect {

    public InstantenousMobEffect(MobEffectCategory category, int color) { super(category, color); }

    @Override
    public boolean isInstantenous() { return true; }
}
