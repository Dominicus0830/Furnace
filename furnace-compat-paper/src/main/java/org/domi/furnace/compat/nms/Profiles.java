package org.domi.furnace.compat.nms;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.entity.ProfileProperty;

/**
 * authlib GameProfile ↔ Furnace 프로필 값(uuid·이름·속성). authlib 은 서버가 늘 싣지 않는다 — 그것을 쓰는 플러그인이 있을 때만 받는다
 * (plugins.compat.libraries.mojang). 그래서 만들기 전에 {@link #available()} 로 본다: 없으면 GameProfile 자리는 null 이고,
 * 프로필이 꼭 있어야 하는 변환(탭 목록 ADD_PLAYER)은 까닭을 밝히고 거절한다.
 */
public final class Profiles {

    private Profiles() {}

    private static volatile Boolean available;

    /** authlib 을 이 호환 계층 로더에서 쓸 수 있는가(한 번 보고 기억한다). */
    public static boolean available() {
        Boolean a = available;
        if (a == null) {
            try {
                Class.forName("com.mojang.authlib.GameProfile", false, Profiles.class.getClassLoader());
                a = true;
            } catch (ClassNotFoundException | LinkageError e) {
                a = false;
            }
            available = a;
        }
        return a;
    }

    /** 시험 전용: authlib 이 없는 서버처럼(true) 또는 원래대로(false). */
    static void simulateMissingForTest(boolean missing) { available = missing ? Boolean.FALSE : null; }

    /** authlib 이 없을 때 쓰는 까닭 글. */
    static final String MISSING = "authlib(GameProfile)이 서버에 없다 — authlib 을 쓰는 플러그인이 있으면 서버가 받는다"
            + "(furnace.properties plugins.compat.libraries.mojang=true, libraries.minecraft.net 에 닿아야 한다)";

    /** 프로필(authlib 이 없으면 null). 이름은 바닐라처럼 null 이면 빈 글. */
    public static GameProfile of(UUID id, String name, List<ProfileProperty> properties) {
        if (!available()) return null;
        GameProfile p = new GameProfile(id, name == null ? "" : name);
        for (ProfileProperty q : properties) p.getProperties().put(q.name(), new Property(q.name(), q.value(), q.signature()));
        return p;
    }

    /** 접속한 플레이어의 프로필(authlib 이 없으면 null). */
    public static GameProfile of(org.domi.furnace.api.entity.Player player) {
        if (!available()) return null;
        return of(player.uniqueId(), player.name(), player.profileProperties());
    }

    /** 프로필의 속성(스킨 textures 등). */
    public static List<ProfileProperty> properties(GameProfile p) {
        List<ProfileProperty> out = new ArrayList<>();
        for (Object o : p.getProperties().values()) {
            Property q = (Property) o;
            out.add(new ProfileProperty(q.name(), q.value(), q.signature()));
        }
        return out;
    }
}
