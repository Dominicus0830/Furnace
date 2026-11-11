package org.bukkit.event.player;

import java.net.InetAddress;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 인증이 끝난 뒤 접속을 받기 전(비동기 스레드). Bukkit·Paper 1.21.1 과 같은 이름·기본값(ALLOWED, 빈 사유).
 * Paper 의 Adventure(kickMessage(Component))·PlayerProfile 멤버는 없다 — 부르면 NoSuchMethodError.
 */
public class AsyncPlayerPreLoginEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private Result result;
    private String message;
    private final String name;
    private final InetAddress ipAddress;
    private final InetAddress rawAddress;
    private final String hostname;
    private final UUID uniqueId;
    private final boolean transferred;

    @Deprecated
    public AsyncPlayerPreLoginEvent(String name, InetAddress ipAddress) { this(name, ipAddress, null); }

    @Deprecated
    public AsyncPlayerPreLoginEvent(String name, InetAddress ipAddress, UUID uniqueId) { this(name, ipAddress, uniqueId, false); }

    public AsyncPlayerPreLoginEvent(String name, InetAddress ipAddress, UUID uniqueId, boolean transferred) {
        this(name, ipAddress, ipAddress, uniqueId, transferred, "");
    }

    public AsyncPlayerPreLoginEvent(String name, InetAddress ipAddress, InetAddress rawAddress, UUID uniqueId, boolean transferred, String hostname) {
        super(true);
        this.result = Result.ALLOWED;
        this.message = "";
        this.name = name;
        this.ipAddress = ipAddress;
        this.rawAddress = rawAddress;
        this.uniqueId = uniqueId;
        this.transferred = transferred;
        this.hostname = hostname;
    }

    public Result getLoginResult() { return result; }

    public void setLoginResult(Result result) { this.result = result; }

    public String getKickMessage() { return message; }

    public void setKickMessage(String message) { this.message = message; }

    public void allow() {
        result = Result.ALLOWED;
        message = "";
    }

    public void disallow(Result result, String message) {
        this.result = result;
        this.message = message;
    }

    public String getName() { return name; }

    public InetAddress getAddress() { return ipAddress; }

    /** 프록시 포워딩 전 실제 소켓 주소(Paper). Furnace 는 포워딩한 주소와 같은 값을 준다. */
    public InetAddress getRawAddress() { return rawAddress; }

    /** 클라이언트가 접속에 쓴 주소 "호스트:포트"(Paper). */
    public String getHostname() { return hostname; }

    public UUID getUniqueId() { return uniqueId; }

    public boolean isTransferred() { return transferred; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    public enum Result { ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER }
}
