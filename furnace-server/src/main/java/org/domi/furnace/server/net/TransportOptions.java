// 전송 방식 옵션(furnace.properties) — io-uring: true 면 리눅스에서 io_uring 으로 접속을 받는다(기본 꺼짐 = 바닐라와 같은 epoll/NIO 선택).
// io_uring 을 쓸 수 없으면(커널 5.9 미만·seccomp/컨테이너가 막음·리눅스 아님) 경고하고 epoll/NIO 로 되돌아간다.
package org.domi.furnace.server.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record TransportOptions(boolean ioUring) {

    public static final TransportOptions DEFAULT = new TransportOptions(false);

    public static TransportOptions load(Path path) {
        if (!Files.exists(path)) return DEFAULT;
        Properties props = new Properties();
        try (var in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("설정 로드 실패: " + path, e);
        }
        return from(props);
    }

    static TransportOptions from(Properties props) {
        String v = props.getProperty("io-uring");
        if (v == null) return DEFAULT;
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("설정 'io-uring' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
        }
        return new TransportOptions(Boolean.parseBoolean(v));
    }
}
