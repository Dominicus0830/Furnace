package net.minecraft.world.entity.player;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/** 플레이어 shim(Mojang 이름, H6 최소 표면). Furnace 에서 사람 모양 엔티티는 접속한 플레이어뿐이라 실제 객체는 늘 ServerPlayer 다. */
public abstract class Player extends LivingEntity {

    /**
     * 바닐라처럼 플레이어마다 하나인 authlib 프로필(N-lib-2). 접속 때 받은 속성(스킨 textures 등)으로 만든다.
     * 플러그인이 속성을 바꿔도 서버(탭 목록·스킨)에는 반영되지 않는다 — 가정·남은 구멍(compat-nms-nlib-loader 문서).
     * authlib 이 없으면(Mojang 라이브러리 꺼짐·내려받기 실패) null 로 두고 부를 때 다시 만들어 원인 오류를 그대로 낸다.
     */
    private volatile GameProfile gameProfile;

    protected Player(CraftHumanEntity bukkit) {
        super(bukkit);
        GameProfile p;
        try {
            p = Lib.profile(bukkit);
        } catch (LinkageError e) {
            p = null;
        }
        this.gameProfile = p;
    }

    @Override
    public CraftHumanEntity getBukkitEntity() { return (CraftHumanEntity) super.getBukkitEntity(); }

    /** 바닐라: 스코어보드에서 쓰는 이름 = 플레이어 이름. */
    public String getScoreboardName() { return getBukkitEntity().getName(); }

    /** 바닐라 getGameProfile: 늘 같은 객체. */
    public GameProfile getGameProfile() {
        GameProfile p = gameProfile;
        if (p != null) return p;
        synchronized (this) {
            if (gameProfile == null) gameProfile = Lib.profile(getBukkitEntity());
            return gameProfile;
        }
    }

    /** authlib 타입을 본문에 쓰는 도우미 — 이 클래스가 링크될 때 authlib 을 끌어오지 않게 뗀다(CompatLinkTest). */
    static final class Lib {
        private Lib() {}

        static GameProfile profile(CraftHumanEntity bukkit) {
            GameProfile p = new GameProfile(bukkit.getUniqueId(), bukkit.getName());
            if (bukkit instanceof CraftPlayer cp) {
                for (org.domi.furnace.api.entity.ProfileProperty pr : cp.furnaceHandle().profileProperties()) {
                    p.getProperties().put(pr.name(), new Property(pr.name(), pr.value(), pr.signature()));
                }
            }
            return p;
        }
    }
}
