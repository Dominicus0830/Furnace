package org.domi.furnace.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.player.AsyncChatEvent;
import org.domi.furnace.api.event.player.PlayerKickEvent;
import org.domi.furnace.api.text.Text;
import org.junit.jupiter.api.Test;

/** AsyncChatEvent#recipients(): 처음엔 접속자 전원, 늘고 줄 수 있고, 같은 UUID 의 다른 핸들 객체로도 빠진다. 건드리지 않으면 사본을 만들지 않는다. */
class AsyncChatEventRecipientsTest {

    private static Player player(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, m, args) -> switch (m.getName()) {
            case "uniqueId" -> id;
            case "name" -> name;
            case "displayName" -> Text.text(name);
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> name;
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }

    @Test
    void startsAsEveryoneOnlineAndIsMutable() {
        Player a = player("A"), b = player("B"), c = player("C");
        var ev = new AsyncChatEvent(a, "hi", Text.text("hi"), null, () -> List.of(a, b, c));
        assertFalse(ev.recipientsTouched());
        assertEquals(3, ev.recipients().size());
        assertTrue(ev.recipientsTouched());
        assertTrue(ev.recipients().remove(b));
        assertFalse(ev.recipients().contains(b));
        assertEquals(2, ev.recipients().size());
        assertTrue(ev.recipients().add(b));
        assertFalse(ev.recipients().add(b), "이미 있으면 false");
        ev.recipients().clear();
        assertTrue(ev.recipients().isEmpty());
    }

    @Test
    void anotherHandleOfTheSamePlayerIsTheSameRecipient() {
        Player a = player("A");
        Player aAgain = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, m, args) -> m.getName().equals("uniqueId") ? a.uniqueId() : m.getName().equals("hashCode") ? 1 : m.getName().equals("equals") ? proxy == args[0] : null);
        var ev = new AsyncChatEvent(a, "hi", Text.text("hi"), null, () -> List.of(a));
        assertTrue(ev.recipients().contains(aAgain));
        assertTrue(ev.recipients().remove(aAgain));
        assertTrue(ev.recipients().isEmpty());
    }

    @Test
    void snapshotIsReadOnceAndOnlyWhenTouched() {
        AtomicInteger reads = new AtomicInteger();
        Player a = player("A");
        var ev = new AsyncChatEvent(a, "hi", Text.text("hi"), null, () -> {
            reads.incrementAndGet();
            return List.of(a);
        });
        assertEquals(0, reads.get(), "건드리지 않으면 읽지 않는다");
        var set = ev.recipients();
        assertSame(set, ev.recipients());
        assertEquals(1, reads.get());
    }

    @Test
    void kickLeaveMessageDefaultsToVanillaAndCanBeReplacedOrRemoved() {
        Player a = player("A");
        var ev = new PlayerKickEvent(a, Text.text("bye"));
        assertFalse(ev.hasLeaveMessage());
        assertTrue(ev.leaveMessage().toString().contains("multiplayer.player.left"), "바닐라 기본 알림: " + ev.leaveMessage());
        ev.leaveMessage(Text.text("custom"));
        assertTrue(ev.hasLeaveMessage());
        assertEquals("custom", ev.leaveMessage().plainText());
        ev.leaveMessage(null);
        assertTrue(ev.hasLeaveMessage());
        assertEquals(null, ev.leaveMessage());
    }
}
