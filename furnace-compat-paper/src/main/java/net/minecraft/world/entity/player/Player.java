package net.minecraft.world.entity.player;

import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;

/** 플레이어 shim(Mojang 이름, H6 최소 표면). Furnace 에서 사람 모양 엔티티는 접속한 플레이어뿐이라 실제 객체는 늘 ServerPlayer 다. */
public abstract class Player extends LivingEntity {

    protected Player(CraftHumanEntity bukkit) { super(bukkit); }

    @Override
    public CraftHumanEntity getBukkitEntity() { return (CraftHumanEntity) super.getBukkitEntity(); }

    /** 바닐라: 스코어보드에서 쓰는 이름 = 플레이어 이름. */
    public String getScoreboardName() { return getBukkitEntity().getName(); }
}
