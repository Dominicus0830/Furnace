package org.bukkit.event.player;

import java.net.InetAddress;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 플레이어 객체가 생긴 직후, 월드에 들어가기 전(Bukkit·Paper 1.21.1 과 같은 이름·기본값). 거부하면 그 사유로 끊고 PlayerJoinEvent 는 없다.
 * <b>Furnace 차이:</b> Furnace 는 바닐라 접속 검사(밴·화이트리스트·인원)를 로그인 단계에서 먼저 끝내므로 이 이벤트는 그 검사를 통과한
 * 접속에만 온다(처음 결과는 늘 ALLOWED) — 바닐라가 막은 접속을 이 이벤트로 허용할 수는 없다(docs/feature-parity/compat-luckperms-2026-11-11.md).
 */
public class PlayerLoginEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();
    private final InetAddress address;
    private final InetAddress realAddress;
    private final String hostname;
    private Result result = Result.ALLOWED;
    private String message = "";

    public PlayerLoginEvent(Player player, String hostname, InetAddress address, InetAddress realAddress) {
        super(player);
        this.hostname = hostname;
        this.address = address;
        this.realAddress = realAddress;
    }

    public PlayerLoginEvent(Player player, String hostname, InetAddress address) { this(player, hostname, address, address); }

    public PlayerLoginEvent(Player player, String hostname, InetAddress address, Result result, String message, InetAddress realAddress) {
        this(player, hostname, address, realAddress);
        this.result = result;
        this.message = message;
    }

    /** 프록시 포워딩 전 실제 소켓 주소. Furnace 는 포워딩한 주소와 같은 값을 준다. */
    public InetAddress getRealAddress() { return realAddress; }

    public Result getResult() { return result; }

    public void setResult(Result result) { this.result = result; }

    public String getKickMessage() { return message; }

    public void setKickMessage(String message) { this.message = message; }

    /** 클라이언트가 접속에 쓴 주소 "호스트:포트". */
    public String getHostname() { return hostname; }

    public void allow() {
        result = Result.ALLOWED;
        message = "";
    }

    public void disallow(Result result, String message) {
        this.result = result;
        this.message = message;
    }

    public InetAddress getAddress() { return address; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    public enum Result { ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER }
}
