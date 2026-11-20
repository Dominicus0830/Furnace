package net.minecraft.world.entity.player;

import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;

/** 플레이어 shim(Mojang 이름, H6 최소 표면). Furnace 에서 사람 모양 엔티티는 접속한 플레이어뿐이라 실제 객체는 늘 ServerPlayer 다. */
public abstract class Player extends LivingEntity {

    /**
     * 바닐라처럼 GameProfile 필드(ProtocolLib WrappedGameProfile.fromPlayer·WrappedServerPing 이 타입으로 찾는다). uuid·이름·속성(스킨)은
     * 지금 프로필 값이다 — setPlayerProfile 뒤에는 getGameProfile 이 다시 맞춘다(필드를 직접 읽는 쪽은 그 뒤에 새 값을 본다).
     * authlib 이 서버에 없으면 null(그때는 그것을 읽는 플러그인도 authlib 을 쓰지 않는다).
     */
    private volatile com.mojang.authlib.GameProfile gameProfile;
    /** gameProfile 을 만든 때의 Furnace 프로필(setPlayerProfile 로 바뀌면 다시 만든다). */
    private volatile org.domi.furnace.api.entity.ProfileData gameProfileOf;

    protected Player(CraftHumanEntity bukkit) {
        super(bukkit);
        if (bukkit instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) {
            this.gameProfileOf = cp.furnaceHandle().profile();
            this.gameProfile = org.domi.furnace.compat.nms.Profiles.of(cp.furnaceHandle());
        }
    }

    @Override
    public CraftHumanEntity getBukkitEntity() { return (CraftHumanEntity) super.getBukkitEntity(); }

    /** 바닐라 Player#isSecondaryUseActive: 웅크리고 있는가(isShiftKeyDown — 서버에서는 Bukkit isSneaking 과 같은 값). */
    public boolean isSecondaryUseActive() {
        return getBukkitEntity() instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp && cp.isSneaking();
    }

    /** 바닐라 {@code Player#isCreative}: 크리에이티브 모드인가(스펙테이터는 아니다). */
    public boolean isCreative() { return getBukkitEntity().getGameMode() == org.bukkit.GameMode.CREATIVE; }

    /** 바닐라: 관리자 블록(명령 블록 등)을 쓸 수 있는가 — 즉시 짓기(크리에이티브)이고 권한 수준 2 이상. */
    public boolean canUseGameMasterBlocks() {
        boolean instabuild = this instanceof net.minecraft.server.level.ServerPlayer sp ? sp.getAbilities().instabuild : isCreative();
        return instabuild && hasPermissions(2);
    }

    /** 바닐라 {@code Player#getFoodData}: 이 플레이어의 허기·포화·소모(서버 값을 보는 보기). */
    public net.minecraft.world.food.FoodData getFoodData() { return new net.minecraft.world.food.FoodData(this); }

    /** 바닐라: 스코어보드에서 쓰는 이름 = 플레이어 이름. */
    public String getScoreboardName() { return getBukkitEntity().getName(); }

    /** 바닐라 Player#getName: 게임 프로필 이름 글자. */
    public net.minecraft.network.chat.Component getName() { return net.minecraft.network.chat.Component.literal(getGameProfile().getName()); }

    /** 바닐라 Player#isSwimming: 날거나 관전 중이 아닐 때만 공유 플래그 4. */
    @Override
    public boolean isSwimming() {
        var l = furnaceLook("Player#isSwimming");
        return !l.flying() && !l.spectator() && (l.sharedFlags() & 1 << 4) != 0;
    }

    /** 바닐라: 플레이어 프로필(authlib 이 없으면 null). */
    public com.mojang.authlib.GameProfile getGameProfile() {
        if (getBukkitEntity() instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp && gameProfile != null) {
            var now = cp.furnaceHandle().profile();   // ServerPlayer.gameProfile 은 setPlayerProfile 이 바꾼다 — 바뀌었으면 필드도 새 값으로
            if (!now.equals(gameProfileOf)) {
                gameProfile = org.domi.furnace.compat.nms.Profiles.of(cp.furnaceHandle());
                gameProfileOf = now;
            }
        }
        return gameProfile;
    }
}
