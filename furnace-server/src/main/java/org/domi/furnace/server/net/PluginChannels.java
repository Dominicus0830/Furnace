// 플러그인 채널 등록(minecraft:register / minecraft:unregister) — 바닐라에는 없고 Bukkit 이 만든 관례다.
// 본문은 채널 이름을 NUL(0x00)로 이어 붙인 UTF-8 이다(끝에 NUL 이 있어도 되고 빈 이름은 건너뛴다).
// 한 연결이 클라이언트 쪽에서 듣겠다고 알린 채널 집합을 들고 있다. 어느 스레드에서 읽어도 된다.
package org.domi.furnace.server.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.data.ResourceId;

public final class PluginChannels {

    public static final String REGISTER = "minecraft:register";
    public static final String UNREGISTER = "minecraft:unregister";
    /** 연결 하나가 등록할 수 있는 채널 수(CraftBukkit CraftPlayer.addChannel 의 128 과 같게 — 가정: Paper 소스로 확인하지 않았다). 넘는 것은 버린다. */
    public static final int MAX_CHANNELS = 128;

    private final Set<String> channels = ConcurrentHashMap.newKeySet();

    /** 등록 본문을 반영한다. 새로 더해진 채널 수를 돌려준다. */
    public synchronized int register(byte[] payload) {
        int added = 0;
        for (String c : parse(payload)) {
            if (channels.contains(c)) continue;
            if (channels.size() >= MAX_CHANNELS) break;
            channels.add(c);
            added++;
        }
        return added;
    }

    /** 해제 본문을 반영한다. 빠진 채널 수를 돌려준다. */
    public synchronized int unregister(byte[] payload) {
        int removed = 0;
        for (String c : parse(payload)) if (channels.remove(c)) removed++;
        return removed;
    }

    public boolean listening(String channel) { return channels.contains(channel); }

    /** 지금 등록된 채널의 사본. */
    public Set<String> snapshot() { return Set.copyOf(channels); }

    /** NUL 로 나눈 이름 가운데 올바른 자원 이름("네임스페이스:경로")만, 정규화해서 순서대로. */
    public static List<String> parse(byte[] payload) {
        List<String> out = new ArrayList<>();
        String all = new String(payload, StandardCharsets.UTF_8);
        for (String part : all.split("\0")) {
            if (part.isEmpty()) continue;
            ResourceId id = ResourceId.tryParse(part);
            if (id != null) out.add(id.toString());
        }
        return out;
    }

    /** 채널 이름들을 NUL 로 이어 붙인다(마지막 뒤에는 붙이지 않는다 — CraftBukkit sendSupportedChannels 와 같다). */
    public static byte[] encode(Collection<String> names) {
        return String.join("\0", names).getBytes(StandardCharsets.UTF_8);
    }
}
