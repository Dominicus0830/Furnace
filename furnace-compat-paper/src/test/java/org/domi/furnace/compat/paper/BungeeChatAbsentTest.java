package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * bungeecord-chat 은 쓰는 플러그인이 있을 때만 내려받는 서버 라이브러리다. 그것이 없는 서버에서도 플레이어·엔티티 감싸개와
 * spigot() 사슬이 클래스 로딩에서 깨지지 않아야 한다 — 시험 클래스패스에서 그 jar 만 뺀 로더로 초기화해 본다.
 * (Player.Spigot·BungeeChat 을 반사로 훑으면 서명 전체를 풀어 NoClassDefFoundError 가 난다 — 호환 계층 안에 그런 곳은 없다.)
 */
class BungeeChatAbsentTest {

    @Test
    void wrappersLoadWithoutBungeeChat() throws Exception {
        List<URL> urls = new ArrayList<>();
        boolean dropped = false;
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (new File(e).getName().startsWith("bungeecord-chat")) {
                dropped = true;
                continue;
            }
            urls.add(new File(e).toURI().toURL());
        }
        assertTrue(dropped, "시험 클래스패스에 bungeecord-chat 이 있어야 뺄 수 있다");
        try (URLClassLoader cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName("net.md_5.bungee.api.chat.BaseComponent", false, cl));
            for (String n : List.of("org.bukkit.craftbukkit.entity.CraftEntity", "org.bukkit.craftbukkit.entity.CraftPlayer",
                    "org.bukkit.command.CommandSender$Spigot", "org.bukkit.entity.Player$Spigot", "org.domi.furnace.compat.paper.BungeeChat")) {
                assertDoesNotThrow(() -> Class.forName(n, true, cl), n);
            }
            Class<?> chat = Class.forName("org.domi.furnace.compat.paper.BungeeChat", true, cl);
            Class<?> sender = Class.forName("org.bukkit.command.CommandSender", false, cl);
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer", false, cl);
            // getMethod 는 클래스의 공개 메서드 서명을 모두 풀어 BaseComponent 를 찾는다 — 한 메서드만 푸는 MethodHandle 로 부른다(호환 계층 호출과 같은 링크)
            var lookup = java.lang.invoke.MethodHandles.publicLookup();
            Class<?> senderSpigot = Class.forName("org.bukkit.command.CommandSender$Spigot", false, cl);
            Class<?> playerSpigot = Class.forName("org.bukkit.entity.Player$Spigot", false, cl);
            try {
                assertNotNull(lookup.findStatic(chat, "legacySpigot", java.lang.invoke.MethodType.methodType(senderSpigot, sender)).invoke((Object) null));
                assertNotNull(lookup.findStatic(chat, "playerSpigot", java.lang.invoke.MethodType.methodType(playerSpigot, craftPlayer)).invoke((Object) null));
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
            assertTrue(craftPlayer.getDeclaredMethods().length > 0);
        }
    }
}
