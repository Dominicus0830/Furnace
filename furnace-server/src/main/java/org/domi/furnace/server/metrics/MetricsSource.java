package org.domi.furnace.server.metrics;

import java.util.List;

/** 지금 값을 모아 주는 쪽. 어느 스레드에서 불러도 안전해야 한다(HTTP·JMX 스레드가 부른다). */
@FunctionalInterface
public interface MetricsSource {
    List<Sample> collect();
}
