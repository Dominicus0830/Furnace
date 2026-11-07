package org.bukkit.event.player;

import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class AsyncPlayerChatEvent extends PlayerEvent implements Cancellable {

    private boolean cancel;
    private String message;
    private String format = "<%1$s> %2$s";
    private final Set<Player> recipients;

    public AsyncPlayerChatEvent(boolean async, Player who, String message, Set<Player> players) {
        super(who, async);
        this.message = message;
        this.recipients = players;
    }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    /** String.format 서식 — %1$s 는 보내는 사람 표시 이름, %2$s 는 내용. 기본은 바닐라와 같은 "<%1$s> %2$s". */
    public String getFormat() { return format; }

    public void setFormat(String format) {
        try {
            String.format(format, player, message);
        } catch (RuntimeException ex) {
            throw new IllegalFormatConversionRuntime(ex);
        }
        this.format = format;
    }

    /**
     * 받는 사람(보는 시점의 접속자 사본). 읽기만 된다 — Furnace 의 채팅 이벤트는 받는 사람을 걸러 내는 칸이 없어
     * 바꾸려는 호출은 UnsupportedFeature 로 알린다(받는 사람마다 다른 서식은 Furnace 의 AsyncChatEvent#renderer).
     */
    public Set<Player> getRecipients() { return recipients; }

    @Override
    public boolean isCancelled() { return cancel; }

    @Override
    public void setCancelled(boolean cancel) { this.cancel = cancel; }

    private static final class IllegalFormatConversionRuntime extends IllegalArgumentException {
        IllegalFormatConversionRuntime(Throwable cause) { super("서식이 올바르지 않다", cause); }
    }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
