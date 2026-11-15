package net.minecraft.world.entity.player;

import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;

/** 플레이어 shim(Mojang 이름, H6 최소 표면). Furnace 에서 사람 모양 엔티티는 접속한 플레이어뿐이라 실제 객체는 늘 ServerPlayer 다. */
public abstract class Player extends LivingEntity {

    /**
     * 바닐라처럼 GameProfile 필드(ProtocolLib WrappedGameProfile.fromPlayer·WrappedServerPing 이 타입으로 찾는다). uuid·이름·속성(스킨)은
     * 이 shim 을 만들 때의 플레이어 값이다. authlib 이 서버에 없으면 null(그때는 그것을 읽는 플러그인도 authlib 을 쓰지 않는다).
     */
    private final com.mojang.authlib.GameProfile gameProfile;

    protected Player(CraftHumanEntity bukkit) {
        super(bukkit);
        this.gameProfile = bukkit instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp ? org.domi.furnace.compat.nms.Profiles.of(cp.furnaceHandle()) : null;
    }

    @Override
    public CraftHumanEntity getBukkitEntity() { return (CraftHumanEntity) super.getBukkitEntity(); }

    /** 바닐라: 스코어보드에서 쓰는 이름 = 플레이어 이름. */
    public String getScoreboardName() { return getBukkitEntity().getName(); }

    /** 바닐라: 플레이어 프로필(authlib 이 없으면 null). */
    public com.mojang.authlib.GameProfile getGameProfile() { return gameProfile; }
}
