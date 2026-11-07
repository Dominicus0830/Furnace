// 기동 때 한 번 찍는 보안 경고 모음: RCON 한도와 프록시(BungeeCord 레거시·Velocity·PROXY 프로토콜) 설정.
// 값을 바꾸는 일은 하지 않는다 — 사용자가 고른 값은 그대로 두고, 위험하거나 막힐 수 있는 조합만 알린다.
package org.domi.furnace.server.config;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.remote.RconLimits;

public final class StartupWarnings {

    /** Velocity 서명 비밀이 이보다 짧으면 약하다고 본다(Velocity 가 만드는 기본 비밀은 12자 이상 영숫자). */
    public static final int WEAK_VELOCITY_SECRET_LENGTH = 12;
    public static final int WEAK_RCON_PASSWORD_LENGTH = 8;

    private StartupWarnings() {}

    /** RCON 이 켜져 있을 때만 의미가 있다. 기본 한도(16/4/5/10)면 한도 경고는 없다. */
    public static List<String> rcon(boolean enableRcon, String password, RconLimits l) {
        List<String> w = new ArrayList<>();
        if (!enableRcon) return w;
        if (password != null && !password.isEmpty() && password.length() < WEAK_RCON_PASSWORD_LENGTH) {
            w.add("rcon.password 가 " + WEAK_RCON_PASSWORD_LENGTH + "자 미만입니다. RCON 은 평문 프로토콜이라 짧은 비밀번호는 무차별 대입에 약합니다");
        }
        if (l.maxClientsPerIp() > RconLimits.DEFAULT_MAX_CLIENTS_PER_IP) {
            w.add("rcon.max-clients-per-ip=" + l.maxClientsPerIp() + " 는 기본값 " + RconLimits.DEFAULT_MAX_CLIENTS_PER_IP
                    + " 보다 높습니다: 한 주소가 인증 전 연결까지 더 많이 붙잡을 수 있습니다");
        }
        if (l.maxClientsPerIp() >= l.maxClients()) {
            w.add("rcon.max-clients-per-ip(" + l.maxClientsPerIp() + ") 가 rcon.max-clients(" + l.maxClients()
                    + ") 이상입니다: 한 주소가 연결을 전부 차지해 관리 도구가 붙지 못할 수 있습니다");
        }
        if (l.maxClientsPerIp() <= 2) {
            w.add("rcon.max-clients-per-ip=" + l.maxClientsPerIp() + ": 같은 공인 주소(NAT·프록시 뒤) 에서 관리 도구를 "
                    + (l.maxClientsPerIp() + 1) + "개 이상 쓰면 막힙니다");
        }
        if (l.maxFailsPerIp() > RconLimits.DEFAULT_MAX_FAILS_PER_IP || l.maxFailsPerConnection() > RconLimits.DEFAULT_MAX_FAILS_PER_CONNECTION) {
            w.add("RCON 인증 실패 한도를 기본값(연결당 " + RconLimits.DEFAULT_MAX_FAILS_PER_CONNECTION + "회, IP당 60초 "
                    + RconLimits.DEFAULT_MAX_FAILS_PER_IP + "회)보다 올렸습니다: 비밀번호 무차별 대입에 더 약해집니다");
        }
        if (l.maxFailsPerIp() <= 3 || l.maxFailsPerConnection() <= 1) {
            w.add("RCON 인증 실패 한도가 매우 낮습니다(연결당 " + l.maxFailsPerConnection() + "회, IP당 60초 " + l.maxFailsPerIp()
                    + "회): 비밀번호를 한 번 틀리면 NAT 뒤 다른 관리자까지 잠시 막힐 수 있습니다");
        }
        return w;
    }

    /** 프록시 포워딩(BungeeCord 레거시·Velocity)과 PROXY 프로토콜. 두 포워딩의 동시 사용은 설정 로드에서 이미 기동 실패다. */
    public static List<String> proxy(boolean onlineMode, boolean bungeecord, String velocitySecret, boolean proxyProtocol, boolean proxyTrustedEmpty) {
        List<String> w = new ArrayList<>();
        boolean velocity = velocitySecret != null && !velocitySecret.isEmpty();
        if (bungeecord) {
            w.add("BungeeCord 레거시 포워딩(bungeecord=true)에는 서명이 없습니다: 방화벽이나 PROXY 프로토콜 신뢰 목록으로 프록시 밖의 직접 접속을 막지 않으면 "
                    + "누구든 임의의 이름·UUID·IP 로 접속할 수 있습니다. 서명이 필요하면 Velocity 포워딩(velocity-secret)을 쓰세요");
        }
        if (velocity) {
            if (velocitySecret.length() < WEAK_VELOCITY_SECRET_LENGTH) {
                w.add("velocity-secret 이 " + WEAK_VELOCITY_SECRET_LENGTH + "자 미만입니다: 비밀을 알아내면 프록시를 거치지 않고 임의의 신원으로 접속할 수 있습니다. 더 긴 무작위 값으로 바꾸세요");
            }
            if (onlineMode) {
                w.add("velocity-secret 이 켜져 있어 server.properties 의 online-mode=true 는 쓰이지 않습니다(정품 인증은 Velocity 가 합니다). 혼동을 피하려면 online-mode=false 로 두세요");
            }
        }
        if (proxyProtocol && (bungeecord || velocity)) {
            w.add("PROXY 프로토콜(proxy-protocol=true)과 " + (bungeecord ? "BungeeCord" : "Velocity")
                    + " 포워딩이 함께 켜져 있습니다: 앞단 로드밸런서가 PROXY 헤더를 보내고 그 뒤 프록시가 포워딩 정보를 보내야 모든 접속이 통과합니다");
        }
        if (proxyProtocol && proxyTrustedEmpty && (bungeecord || velocity)) {
            w.add("proxy-protocol.trusted 가 비어 있습니다: 프록시 포워딩을 쓰는 서버에서는 신뢰할 앞단 주소를 지정해 헤더 위조를 막으세요");
        }
        return w;
    }
}
