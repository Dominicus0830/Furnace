// Spigot 설정(spigot.yml)의 정적 값 자리. 플러그인은 이 클래스가 있는지로 "Spigot 계열 서버인가"를 판별하고(ProtocolLib Util.isUsingSpigot·
// 업데이트 확인, 여러 플러그인의 BungeeCord 감지), SpigotConfig.bungee 로 BungeeCord 포워딩 여부를 읽는다.
// Furnace 는 spigot.yml 을 읽지 않는다 — 같은 뜻의 Furnace 설정(furnace.properties bungeecord)에서 채운 값만 둔다.
package org.spigotmc;

import org.bukkit.configuration.file.YamlConfiguration;

public class SpigotConfig {

    /** settings.bungeecord — Furnace 의 furnace.properties bungeecord. */
    public static boolean bungee;

    /**
     * spigot.yml 내용 보기. Furnace 가 아는 키(settings.bungeecord)만 들어 있다 — 다른 키는 비어 있으니 기본값을 쓰는 읽기만 맞다.
     * 이 객체에 쓴 값은 서버에 반영되지 않는다.
     */
    public static YamlConfiguration config;

    static {
        org.domi.furnace.api.Furnace f = org.domi.furnace.api.Furnace.serverOrNull();
        bungee = f != null && f.bungeeCordForwarding();
        config = new YamlConfiguration();
        config.set("settings.bungeecord", bungee);
    }

    private SpigotConfig() {}
}
