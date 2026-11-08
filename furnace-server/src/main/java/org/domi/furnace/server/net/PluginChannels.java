// 플러그인 채널 등록(minecraft:register / minecraft:unregister) — 바닐라에는 없고 Bukkit 이 만든 관례다.
// 본문은 채널 이름을 NUL(0x00)로 이어 붙인 UTF-8 이다(끝에 NUL 이 있어도 되고 빈 이름은 건너뛴다).
// 한 연결이 클라이언트 쪽에서 듣겠다고 알린 채널 집합을 들고 있다. 어느 스레드에서 읽어도 된다(this 로 잠근다).
package org.domi.furnace.server.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PluginChannels {

    public static final String REGISTER = "minecraft:register";
    public static final String UNREGISTER = "minecraft:unregister";
    /**
     * 연결 하나가 등록할 수 있는 채널 수(Paper 1.21.1 CraftPlayer.addChannel 의 128). 이미 128개일 때 또 등록하면(이미 있는 이름이어도)
     * Paper 처럼 {@link InvalidChannelException} → 연결 종료. {@code -Dpaper.disableChannelLimit} 이 있으면 제한이 없다(Paper 와 같은 속성).
     */
    public static final int MAX_CHANNELS = 128;
    /** 채널 이름 최대 길이 — Paper 의 Messenger.MAX_CHANNEL_SIZE (속성 paper.maxCustomChannelName, 기본 32767). */
    public static final int MAX_CHANNEL_SIZE = Integer.getInteger("paper.maxCustomChannelName", Short.MAX_VALUE);
    private static final boolean DISABLE_CHANNEL_LIMIT = System.getProperty("paper.disableChannelLimit") != null;

    /** Paper 라면 예외로 연결이 끊기는 입력(채널 128개 초과, 이름에 ':' 없음·대문자·너무 김). */
    public static final class InvalidChannelException extends RuntimeException {
        public InvalidChannelException(String message) { super(message); }
    }

    /** 알린 순서를 지킨다(play 진입 때 구성 단계 채널을 그 순서로 PlayerChannelEvent 로 알린다). */
    private final Set<String> channels = new LinkedHashSet<>();

    /** 등록 본문을 반영한다. 새로 더해진 채널 수를 돌려준다. */
    public int register(byte[] payload) { return add(payload).size(); }

    /** 해제 본문을 반영한다. 빠진 채널 수를 돌려준다. */
    public int unregister(byte[] payload) { return remove(payload).size(); }

    /** 등록 본문을 반영하고 새로 더해진 채널을 순서대로 돌려준다. */
    public synchronized List<String> add(byte[] payload) {
        List<String> added = new ArrayList<>();
        for (String c : parse(payload)) {
            // Paper CraftPlayer.addChannel: 개수 검사가 먼저(이미 있는 이름이어도), 그다음 이름 검사·추가
            if (!DISABLE_CHANNEL_LIMIT && channels.size() >= MAX_CHANNELS) throw new InvalidChannelException("Cannot register channel. Too many channels registered!");
            c = validateAndCorrect(c);
            if (channels.add(c)) added.add(c);
        }
        return added;
    }

    /** 해제 본문을 반영하고 실제로 빠진 채널을 순서대로 돌려준다. */
    public synchronized List<String> remove(byte[] payload) {
        List<String> removed = new ArrayList<>();
        for (String c : parse(payload)) {
            c = validateAndCorrect(c);
            if (channels.remove(c)) removed.add(c);
        }
        return removed;
    }

    public synchronized boolean listening(String channel) { return channels.contains(channel); }

    /** 지금 등록된 채널의 사본(알린 순서). */
    public synchronized Set<String> snapshot() { return Collections.unmodifiableSet(new LinkedHashSet<>(channels)); }

    /** NUL 로 나눈 이름들(빈 이름은 건너뛴다 — Paper readChannelIdentifier 처럼). 이름 검사는 {@link #validateAndCorrect}. */
    public static List<String> parse(byte[] payload) {
        List<String> out = new ArrayList<>();
        String all = new String(payload, StandardCharsets.UTF_8);
        for (String part : all.split("\0")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    /** Paper StandardMessenger.validateAndCorrectChannel 의 선(서버 안) 쪽 규칙: BungeeCord → bungeecord:main, 길이·':'·소문자 검사. */
    public static String validateAndCorrect(String channel) {
        if (channel.equals("BungeeCord")) return "bungeecord:main";
        if (channel.equals("bungeecord:main")) return channel;
        if (channel.length() > MAX_CHANNEL_SIZE) throw new InvalidChannelException("Channel name too long (" + channel.length() + ")");
        if (channel.indexOf(':') == -1) throw new InvalidChannelException("Channel must contain : separator");
        if (!channel.toLowerCase(java.util.Locale.ROOT).equals(channel)) throw new InvalidChannelException("Channel must be entirely lowercase");
        return channel;
    }

    /** 채널 이름들을 NUL 로 이어 붙인다(마지막 뒤에는 붙이지 않는다 — CraftBukkit sendSupportedChannels 와 같다). */
    public static byte[] encode(Collection<String> names) {
        return String.join("\0", names).getBytes(StandardCharsets.UTF_8);
    }
}
