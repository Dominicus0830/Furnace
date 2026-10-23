// 바닐라 DedicatedServerSettings — server.properties 의 현재 값과 파일 경로를 쥐고, 값이 바뀌면(white-list 등) 파일을 다시 쓴다.
package org.domi.furnace.server.config;

import java.nio.file.Path;
import java.util.Properties;

public final class ServerPropertiesFile {

    private final Path source;
    private volatile ServerProperties properties;

    public ServerPropertiesFile(Path source) {
        this.source = source;
        this.properties = ServerProperties.fromFile(source);
    }

    public ServerProperties get() { return properties; }

    public Path path() { return source; }

    public void forceSave() { properties.store(source); }

    /** 바뀐 Properties 로 다시 해석하고 저장한다. 예: update(p -> p.whiteList.with(true)). */
    public synchronized ServerPropertiesFile update(java.util.function.Function<ServerProperties, Properties> op) {
        properties = new ServerProperties(op.apply(properties));
        forceSave();
        return this;
    }
}
