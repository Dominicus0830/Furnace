// 기동 때 한 번 찍는 보안 경고 모음: RCON 한도와 프록시(BungeeCord 레거시·Velocity·PROXY 프로토콜) 설정.
// 값을 바꾸는 일은 하지 않는다 — 사용자가 고른 값은 그대로 두고, 위험하거나 막힐 수 있는 조합만 알린다.
package org.domi.furnace.server.config;

import org.domi.furnace.server.i18n.Lang;
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
            w.add(Lang.t("warn.rcon-weak-password", WEAK_RCON_PASSWORD_LENGTH));
        }
        if (l.maxClientsPerIp() > RconLimits.DEFAULT_MAX_CLIENTS_PER_IP) {
            w.add(Lang.t("warn.rcon-per-ip-high", l.maxClientsPerIp(), RconLimits.DEFAULT_MAX_CLIENTS_PER_IP));
        }
        if (l.maxClientsPerIp() >= l.maxClients()) {
            w.add(Lang.t("warn.rcon-per-ip-ge-max", l.maxClientsPerIp(), l.maxClients()));
        }
        if (l.maxClientsPerIp() <= 2) {
            w.add(Lang.t("warn.rcon-per-ip-low", l.maxClientsPerIp(), l.maxClientsPerIp() + 1));
        }
        if (l.maxFailsPerIp() > RconLimits.DEFAULT_MAX_FAILS_PER_IP || l.maxFailsPerConnection() > RconLimits.DEFAULT_MAX_FAILS_PER_CONNECTION) {
            w.add(Lang.t("warn.rcon-fails-high", RconLimits.DEFAULT_MAX_FAILS_PER_CONNECTION, RconLimits.DEFAULT_MAX_FAILS_PER_IP));
        }
        if (l.maxFailsPerIp() <= 3 || l.maxFailsPerConnection() <= 1) {
            w.add(Lang.t("warn.rcon-fails-low", l.maxFailsPerConnection(), l.maxFailsPerIp()));
        }
        return w;
    }

    /** 프록시 포워딩(BungeeCord 레거시·Velocity)과 PROXY 프로토콜. 두 포워딩의 동시 사용은 설정 로드에서 이미 기동 실패다. */
    public static List<String> proxy(boolean onlineMode, boolean bungeecord, String velocitySecret, boolean proxyProtocol, boolean proxyTrustedEmpty) {
        List<String> w = new ArrayList<>();
        boolean velocity = velocitySecret != null && !velocitySecret.isEmpty();
        if (bungeecord) {
            w.add(Lang.t("warn.bungee-unsigned"));
        }
        if (velocity) {
            if (velocitySecret.length() < WEAK_VELOCITY_SECRET_LENGTH) {
                w.add(Lang.t("warn.velocity-weak-secret", WEAK_VELOCITY_SECRET_LENGTH));
            }
            if (onlineMode) {
                w.add(Lang.t("warn.velocity-online-mode"));
            }
        }
        if (proxyProtocol && (bungeecord || velocity)) {
            w.add(Lang.t("warn.proxy-and-forwarding", bungeecord ? "BungeeCord" : "Velocity"));
        }
        if (proxyProtocol && proxyTrustedEmpty && (bungeecord || velocity)) {
            w.add(Lang.t("warn.trusted-empty"));
        }
        return w;
    }
}
