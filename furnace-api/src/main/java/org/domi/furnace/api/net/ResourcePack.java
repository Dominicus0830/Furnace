// 클라이언트에 내려받게 할 리소스 팩 한 개. id 는 나중에 같은 팩을 빼거나 응답을 가를 때 쓴다.
package org.domi.furnace.api.net;

import java.util.Objects;
import java.util.UUID;
import org.domi.furnace.api.text.Text;

/**
 * @param id       팩 번호(같은 번호를 다시 보내면 클라이언트가 그 팩을 바꾼다)
 * @param url      내려받을 주소
 * @param hash     SHA-1 해시(소문자 16진수 40자). 비워 두면 클라이언트가 해시 검사를 하지 않는다. 40자를 넘길 수 없다.
 * @param required 필수 팩인가. 거절(DECLINED)하면 서버가 클라이언트를 내보낸다.
 * @param prompt   안내 문구(없으면 null)
 */
public record ResourcePack(UUID id, String url, String hash, boolean required, Text prompt) {

    public ResourcePack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        hash = hash == null ? "" : hash;
        if (hash.length() > 40) throw new IllegalArgumentException("hash 는 40자 이하여야 한다: " + hash.length());
    }

    public ResourcePack(UUID id, String url, String hash, boolean required) {
        this(id, url, hash, required, null);
    }
}
