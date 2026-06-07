// 커넥션의 프로토콜 단계. 패킷 id 의미는 단계마다 다르다.
package org.domi.furnace.server.net.protocol;

public enum ConnectionState {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY
}
