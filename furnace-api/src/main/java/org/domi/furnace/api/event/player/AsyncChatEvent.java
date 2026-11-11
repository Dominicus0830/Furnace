// 채팅 — 그 플레이어의 연결 스레드에서(서버 틱과 무관하게) 불린다. 취소하면 아무에게도 가지 않는다.
// renderer 를 정하면 받는 사람마다 따로 만든 줄을 시스템 메시지로 보낸다.
// recipients() 는 받는 사람(처음엔 그 시점의 접속자 전원)이고 바꿀 수 있다 — 빼면 그 사람은 이 채팅을 받지 않는다(채널·무시·근거리 채팅 플러그인).
// 콘솔 로그는 받는 사람과 상관없이 남는다. 한 번도 건드리지 않으면 서버는 보내는 시점의 접속자 전원에게 보낸다.
// 내용을 바꾸면 바닐라처럼 "서명 없는 내용(unsigned_content)"으로 보낸다(서명은 원문 기준으로 유지된다).
package org.domi.furnace.api.event.player;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.text.Text;

public final class AsyncChatEvent extends PlayerEvent implements Cancellable {

    private final String originalMessage;
    private Text message;
    private boolean cancelled;
    private final byte[] signature;
    private ChatRenderer renderer;
    private final Supplier<? extends Collection<? extends Player>> online;
    private RecipientSet recipients;

    public AsyncChatEvent(Player player, String originalMessage, Text message) {
        this(player, originalMessage, message, null);
    }

    /** @param signature 메시지 서명(256바이트), 서명 없는 채팅이면 null */
    public AsyncChatEvent(Player player, String originalMessage, Text message, byte[] signature) {
        this(player, originalMessage, message, signature, () -> org.domi.furnace.api.Furnace.server().onlinePlayers());
    }

    /**
     * @param signature 메시지 서명(256바이트), 서명 없는 채팅이면 null
     * @param online 처음 받는 사람 — recipients() 를 처음 부를 때 한 번 읽는다(건드리지 않는 채팅은 사본을 만들지 않는다)
     */
    public AsyncChatEvent(Player player, String originalMessage, Text message, byte[] signature, Supplier<? extends Collection<? extends Player>> online) {
        super(player, true);   // 연결 스레드에서 불린다 — Paper AsyncChatEvent 처럼 비동기
        this.online = online;
        this.originalMessage = originalMessage;
        this.message = message;
        this.signature = signature == null ? null : signature.clone();
    }

    /** 메시지 서명 사본(256바이트), 서명 없는 채팅이면 null. 나중에 Player.deleteChatMessage 로 지울 때 쓴다. */
    public byte[] signature() { return signature == null ? null : signature.clone(); }

    /** 플레이어가 보낸 원문(서명 대상). */
    public String originalMessage() { return originalMessage; }

    public Text message() { return message; }

    /**
     * 받는 사람마다 다른 서식을 쓰려면 정한다(null = 바닐라 서식). 정하면 서명 없는 시스템 메시지로 보낸다 —
     * 채팅 신고 대상이 되지 않고, 채팅을 숨긴 사람은 받지 않는다. 불리는 스레드는 이 이벤트와 같은 연결 스레드이므로 오래 걸리는 일은 하지 않는다.
     */
    public void renderer(ChatRenderer renderer) { this.renderer = renderer; }

    /** 정한 렌더러, 없으면 null. */
    public ChatRenderer renderer() { return renderer; }

    public void message(Text message) { this.message = message; }

    /**
     * 받는 사람(변경 가능한 집합, 플레이어는 UUID 로 구분한다). 처음 부르면 그 시점의 접속자 전원이 들어 있다.
     * 빼면 그 사람은 이 채팅을 받지 않고, 더하면(접속 중인 사람만 뜻이 있다) 받는다. 이 이벤트가 불리는 연결 스레드에서만 만진다.
     */
    public Set<Player> recipients() {
        if (recipients == null) recipients = new RecipientSet(online.get());
        return recipients;
    }

    /** 처리기가 recipients() 를 만졌는가. 만지지 않았으면 서버는 접속자 전원에게 보낸다. */
    public boolean recipientsTouched() { return recipients != null; }

    /** UUID 로 구분하는 받는 사람 집합 — 같은 플레이어의 다른 핸들 객체로 빼도 빠진다. */
    private static final class RecipientSet extends AbstractSet<Player> {
        private final Map<UUID, Player> byId = new LinkedHashMap<>();

        RecipientSet(Collection<? extends Player> initial) {
            for (Player p : initial) byId.put(p.uniqueId(), p);
        }

        @Override
        public Iterator<Player> iterator() { return byId.values().iterator(); }

        @Override
        public int size() { return byId.size(); }

        @Override
        public boolean contains(Object o) { return o instanceof Player p && byId.containsKey(p.uniqueId()); }

        @Override
        public boolean add(Player p) {
            java.util.Objects.requireNonNull(p, "player");
            return byId.put(p.uniqueId(), p) == null;
        }

        @Override
        public boolean remove(Object o) { return o instanceof Player p && byId.remove(p.uniqueId()) != null; }

        @Override
        public void clear() { byId.clear(); }
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
