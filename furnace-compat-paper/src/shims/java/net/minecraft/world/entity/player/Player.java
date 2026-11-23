package net.minecraft.world.entity.player;

import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;

/** 플레이어 shim(Mojang 이름, H6 최소 표면). Furnace 에서 사람 모양 엔티티는 접속한 플레이어뿐이라 실제 객체는 늘 ServerPlayer 다. */
class Player {

    /**
     * 바닐라처럼 GameProfile 필드(ProtocolLib WrappedGameProfile.fromPlayer·WrappedServerPing 이 타입으로 찾는다). uuid·이름·속성(스킨)은
     * 지금 프로필 값이다 — setPlayerProfile 뒤에는 getGameProfile 이 다시 맞춘다(필드를 직접 읽는 쪽은 그 뒤에 새 값을 본다).
     * authlib 이 서버에 없으면 null(그때는 그것을 읽는 플러그인도 authlib 을 쓰지 않는다).
     */
    private volatile com.mojang.authlib.GameProfile gameProfile;
    /** gameProfile 을 만든 때의 Furnace 프로필(setPlayerProfile 로 바뀌면 다시 만든다). */
    private volatile org.domi.furnace.api.entity.ProfileData gameProfileOf;

    /** 생성기 전용 생성자(T-232). 재료는 이 플레이어의 Bukkit 객체다(ServerPlayer 가 싣는다). */
    protected Player(org.domi.furnace.compat.nms.ShimCtor tag) {
        super(tag);
        if (tag.payload() instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) {
            this.gameProfileOf = cp.furnaceHandle().profile();
            this.gameProfile = org.domi.furnace.compat.nms.Profiles.of(cp.furnaceHandle());
        }
    }

    /**
     * 바닐라처럼 공개 필드: 지금 열린 창(없으면 {@link #inventoryMenu}). 접속한 플레이어에서는 서버 창을 비춘 보기이고, {@code CraftPlayer#getHandle()} 이
     * 불릴 때마다 서버의 창과 맞춘다 — 그 사이에 열리고 닫힌 창은 다음 {@code getHandle()} 때 보인다. 플러그인이 직접 만든 창을 넣어도 서버는 모른다.
     */
    public net.minecraft.world.inventory.AbstractContainerMenu containerMenu;

    /** 바닐라 공개 필드: 인벤토리 창(번호 0). 호환 모듈이 처음 맞출 때 채운다(바닐라는 final 이지만 생성 때 서버 창이 아직 없다). */
    public net.minecraft.world.inventory.InventoryMenu inventoryMenu;

    private volatile Inventory furnaceInventory;

    /** 바닐라 getInventory: 접속한 플레이어는 서버 인벤토리를 비춘 {@link Inventory}, 월드에 없는 플레이어는 제 칸의 인벤토리. 플레이어마다 같은 객체. */
    public Inventory getInventory() {
        Inventory i = furnaceInventory;
        if (i == null) {
            synchronized (this) {
                if (furnaceInventory == null) furnaceInventory = furnaceCreateInventory();
                i = furnaceInventory;
            }
        }
        i.furnaceSync();   // selected 필드를 서버의 고른 칸에 맞춘다
        return i;
    }

    /** 호환 모듈 전용 — 이 플레이어의 인벤토리를 만든다(서버 하위 클래스가 고른다). */
    protected Inventory furnaceCreateInventory() { return new Inventory(this); }

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
        org.domi.furnace.compat.nms.NmsEntityOwner.read(this, "Player#isSwimming");
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
