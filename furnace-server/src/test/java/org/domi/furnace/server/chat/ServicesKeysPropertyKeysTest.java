// 서비스 서버 공개키 목록(publicKeys)의 프로필 속성 서명키(profilePropertyKeys)와 채팅 키(playerCertificateKeys)를 따로 읽는다.
package org.domi.furnace.server.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ServicesKeysPropertyKeysTest {

    @Test
    void propertyKeysAndPlayerKeysAreReadSeparately() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair k = gen.generateKeyPair();
        String der = Base64.getEncoder().encodeToString(k.getPublic().getEncoded());
        String body = "{\"profilePropertyKeys\":[{\"publicKey\":\"" + der + "\"}],\"playerCertificateKeys\":[]}";
        assertEquals(1, ServicesKeys.parseKeys(body, "profilePropertyKeys").size());
        assertEquals(0, ServicesKeys.parseKeys(body, "playerCertificateKeys").size());
        assertEquals(0, ServicesKeys.parse(body).size(), "채팅 키 읽기는 그대로");
        assertEquals(0, ServicesKeys.parseKeys("{}", "profilePropertyKeys").size(), "키가 없으면 빈 목록");
    }
}
