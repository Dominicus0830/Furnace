// Bukkit 메신저(StandardMessenger)를 Furnace 플러그인 채널에 잇는다.
//
// - 받는 채널 등록 → Furnace registerPluginChannel(그 플러그인 껍데기 이름으로): 접속자·새 접속자에게 minecraft:register 로 알린다
//   (CraftBukkit sendSupportedChannels). 그 플러그인의 마지막 리스너가 빠지면 Furnace 쪽 등록도 푼다.
// - 받은 메시지: 껍데기마다 Furnace PluginMessageEvent 를 한 번 구독하고(연결 스레드), 그 플러그인의 등록이 있는 채널이면
//   전역 스레드("주 스레드")로 넘겨 리스너를 부른다 — Paper 는 custom_payload 를 주 스레드에서 처리한다(ensureRunningOnSameThread).
// - 플러그인이 꺼지면 받는·보내는 등록을 모두 푼다(Bukkit disablePlugin).
package org.domi.furnace.compat.paper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.messaging.PluginMessageListenerRegistration;
import org.bukkit.plugin.messaging.StandardMessenger;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.event.player.PluginMessageEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.compat.thread.PrimaryThread;

public final class CompatMessenger extends StandardMessenger {

    private final Furnace furnace;
    /** 수명을 묶은 껍데기(꺼지면 빠진다 — 다시 켜지면 다시 묶는다). */
    private final Set<BukkitPluginAdapter> hooked = ConcurrentHashMap.newKeySet();
    /** PluginMessageEvent 를 구독한 껍데기. */
    private final Set<BukkitPluginAdapter> listening = ConcurrentHashMap.newKeySet();

    CompatMessenger(Furnace furnace) { this.furnace = furnace; }

    // ------------------------------------------------------------------ 등록

    // 등록·해제와 Furnace 쪽 알림을 한 잠금으로 묶는다 — 같은 채널을 두 스레드가 동시에 등록·해제해도 두 표가 엇갈리지 않게

    @Override
    public synchronized void registerOutgoingPluginChannel(Plugin plugin, String channel) {
        super.registerOutgoingPluginChannel(plugin, channel);
        hook(plugin);
    }

    @Override
    public synchronized PluginMessageListenerRegistration registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        PluginMessageListenerRegistration r = super.registerIncomingPluginChannel(plugin, channel, listener);
        BukkitPluginAdapter a = hook(plugin);
        if (a != null) {
            if (listening.add(a)) furnace.events().subscribe(a, PluginMessageEvent.class, e -> received(a, e));
            announce(a, r.getChannel());
        }
        return r;
    }

    @Override
    public synchronized void unregisterIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        Set<String> before = plugin == null ? Set.of() : getIncomingChannels(plugin);
        super.unregisterIncomingPluginChannel(plugin, channel, listener);
        withdraw(plugin, before);
    }

    @Override
    public synchronized void unregisterIncomingPluginChannel(Plugin plugin, String channel) {
        Set<String> before = plugin == null ? Set.of() : getIncomingChannels(plugin);
        super.unregisterIncomingPluginChannel(plugin, channel);
        withdraw(plugin, before);
    }

    @Override
    public synchronized void unregisterIncomingPluginChannel(Plugin plugin) {
        Set<String> before = plugin == null ? Set.of() : getIncomingChannels(plugin);
        super.unregisterIncomingPluginChannel(plugin);
        withdraw(plugin, before);
    }

    /** Furnace 가 관리하는 Bukkit 플러그인이면 그 껍데기에 수명을 한 번 묶는다. 아니면(시험용 가짜 Plugin 등) null. */
    private BukkitPluginAdapter hook(Plugin plugin) {
        BukkitPluginAdapter a = adapter(plugin);
        if (a == null || !hooked.add(a)) return a;
        a.own(() -> {
            // onDisable 뒤: 코어가 구독·Furnace 채널 등록은 이미 지웠다. Bukkit 표만 비운다.
            hooked.remove(a);
            listening.remove(a);
            JavaPlugin jp = a.javaPlugin();
            super.unregisterIncomingPluginChannel(jp);
            super.unregisterOutgoingPluginChannel(jp);
        });
        return a;
    }

    private static BukkitPluginAdapter adapter(Plugin plugin) {
        if (!(plugin instanceof JavaPlugin jp)) return null;
        FurnacePlugin owner = Furnace.server().plugins().plugin(jp.getName());
        return owner instanceof BukkitPluginAdapter a && a.javaPlugin() == jp ? a : null;
    }

    /** Furnace 쪽 채널 등록(클라이언트에 minecraft:register). Furnace 가 받지 않는 이름(minecraft:brand 등)은 Bukkit 표에만 둔다. */
    private void announce(BukkitPluginAdapter a, String channel) {
        try {
            furnace.registerPluginChannel(a, channel);
        } catch (IllegalArgumentException e) {
            a.logger().log(Level.FINE, "채널 " + channel + " 은 클라이언트에 알리지 않는다: " + e.getMessage());
        }
    }

    /** 리스너가 모두 빠진 채널은 Furnace 쪽 등록도 푼다. */
    private void withdraw(Plugin plugin, Set<String> before) {
        BukkitPluginAdapter a = adapter(plugin);
        if (a == null || before.isEmpty()) return;
        Set<String> now = getIncomingChannels(plugin);
        for (String c : before) if (!now.contains(c)) furnace.unregisterPluginChannel(a, c);
    }

    // ------------------------------------------------------------------ 받기

    /** 연결 스레드: 이 플러그인이 받는 채널이면 전역 스레드로 넘긴다. */
    private void received(BukkitPluginAdapter a, PluginMessageEvent e) {
        JavaPlugin jp = a.javaPlugin();
        String channel;
        try {
            channel = validateAndCorrectChannel(e.channel());   // 리스너가 받는 이름("bungeecord:main" → "BungeeCord")
            if (getIncomingChannelRegistrations(jp, channel).isEmpty()) return;
        } catch (RuntimeException invalid) {
            return;   // Bukkit 이 받지 않는 이름(MAX_CHANNEL_SIZE 초과 — Paper 기본 32767 이라 선에서는 나오지 않는다)
        }
        byte[] data = e.data();
        org.domi.furnace.api.entity.Player fp = e.player();
        furnace.scheduler().global().execute(() -> {
            if (!fp.isOnline() || !jp.isEnabled()) return;   // 그 사이 나갔다(Paper 도 끊긴 연결의 패킷은 버린다)
            Player source = Compat.player(fp);
            try (PrimaryThread.Scope ignored = PrimaryThread.enter()) {
                for (PluginMessageListenerRegistration r : getIncomingChannelRegistrations(jp, channel)) deliver(r, channel, source, data);
            }
        });
    }

    /** 클라이언트 쪽 이름 → Bukkit 이 보이는 이름. Bukkit 이 받지 않는 이름(MAX_CHANNEL_SIZE 초과)은 null. */
    public static String bukkitName(String wire) {
        try {
            return validateAndCorrectChannel(wire);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
