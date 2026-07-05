// 스폰 게이트 해제 조건(confirm+스폰청크전송완료 AND) 단위 테스트 — 접속 직후 낙하 회귀 방지
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConnectionHandlerSpawnGateTest {

    @Test
    void confirmOnly_doesNotRelease() {
        assertFalse(ConnectionHandler.shouldReleaseSpawnGate(true, false));
    }

    @Test
    void chunksOnly_doesNotRelease() {
        assertFalse(ConnectionHandler.shouldReleaseSpawnGate(false, true));
    }

    @Test
    void neither_doesNotRelease() {
        assertFalse(ConnectionHandler.shouldReleaseSpawnGate(false, false));
    }

    @Test
    void bothConfirmAndChunksDelivered_releases() {
        assertTrue(ConnectionHandler.shouldReleaseSpawnGate(true, true));
    }
}
