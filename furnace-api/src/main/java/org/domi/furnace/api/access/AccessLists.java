// 서버 전체의 OP·밴·IP 밴·화이트리스트 목록. 한 플레이어 단위는 OfflinePlayer 의 메서드가 같은 일을 한다.
// 모두 서버 목록 파일(ops.json·banned-players.json·banned-ips.json·whitelist.json)이 정본이고, 바꾸면 바닐라 명령처럼 바로 파일에 쓴다.
package org.domi.furnace.api.access;

import java.time.Instant;
import java.util.List;
import org.domi.furnace.api.entity.OfflinePlayer;

public interface AccessLists {

    /** server.properties 의 white-list 가 켜져 있는가. */
    boolean isWhitelistEnabled();

    /** /whitelist on|off — server.properties 도 갱신한다. 켜면서 enforce-whitelist 면 목록에 없는 접속자를 내보낸다. */
    void setWhitelistEnabled(boolean enabled);

    /** whitelist.json 을 다시 읽는다(/whitelist reload). 파일을 손으로 고친 뒤에. */
    void reloadWhitelist();

    List<OfflinePlayer> whitelisted();

    List<OfflinePlayer> operators();

    List<BanEntry> bannedPlayers();

    List<IpBanEntry> bannedIps();

    boolean isIpBanned(String ip);

    /**
     * IP 를 밴한다(/ban-ip). 그 IP 로 접속 중인 플레이어는 내보낸다. 이미 밴이면 항목을 바꿔 다시 쓴다.
     * ip 는 IPv4/IPv6 문자열 그대로(이름이 아니다).
     */
    void banIp(String ip, String reason, Instant expires, String source);

    default void banIp(String ip, String reason) { banIp(ip, reason, null, null); }

    /** /pardon-ip. 밴이 아니었으면 false. */
    boolean unbanIp(String ip);
}
