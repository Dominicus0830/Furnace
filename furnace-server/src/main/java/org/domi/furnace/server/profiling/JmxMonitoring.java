// server.properties enable-jmx-monitoring: 바닐라 MinecraftServerStatistics 와 같은 MBean 을 플랫폼 MBeanServer 에 등록한다.
// 이름 net.minecraft.server:type=Server, 속성 tickTimes(long[] — 최근 틱 100 개의 소요 ns, 틱 번호 % 100 자리)와
// averageTickTime(float — 지수 평활한 틱 소요 ms, 바닐라 smoothedTickTimeMillis = 이전×0.8 + 새 값×0.2). 기존 모니터링 도구가 바닐라와 같은 이름으로 읽을 수 있다.
// Furnace 는 region 마다 따로 틱하므로 여기서 "틱"은 전역 박동(ServerCore.scheduledTick) 하나다. 평활값은 읽을 때 최근 표본으로 계산한다(틱 경로에 일을 더하지 않는다).
package org.domi.furnace.server.profiling;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JmxMonitoring implements DynamicMBean, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JmxMonitoring.class);
    public static final String OBJECT_NAME = "net.minecraft.server:type=Server";
    /** 바닐라 MinecraftServer.tickTimesNanos 길이. */
    public static final int SAMPLES = 100;

    /** 최근 틱 소요(ns)를 틱 번호 % 100 자리에 담은 사본과 지금까지 기록한 틱 수. */
    public record Snapshot(long[] byTickNumber, long count) {}

    private final Supplier<Snapshot> tickTimes;
    private final MBeanServer server;
    private ObjectName name;

    JmxMonitoring(MBeanServer server, Supplier<Snapshot> tickTimes) {
        this.server = server;
        this.tickTimes = tickTimes;
    }

    /** 플랫폼 MBeanServer 에 등록한다. 이미 있으면 바꿔 단다. 실패하면 경고하고 null(바닐라: "Failed to initialise server as JMX bean"). */
    public static JmxMonitoring register(Supplier<Snapshot> tickTimes) {
        return register(ManagementFactory.getPlatformMBeanServer(), tickTimes);
    }

    public static JmxMonitoring register(MBeanServer server, Supplier<Snapshot> tickTimes) {
        try {
            JmxMonitoring bean = new JmxMonitoring(server, tickTimes);
            bean.name = new ObjectName(OBJECT_NAME);
            if (server.isRegistered(bean.name)) server.unregisterMBean(bean.name);
            server.registerMBean(bean, bean.name);
            return bean;
        } catch (Exception e) {
            log.warn("Failed to initialise server as JMX bean", e);
            return null;
        }
    }

    @Override
    public void close() {
        try {
            if (name != null && server.isRegistered(name)) server.unregisterMBean(name);
        } catch (Exception e) {
            log.warn("JMX 서버 MBean 해제 실패: {}", e.toString());
        }
    }

    /** 바닐라 MinecraftServer.tickServer: smoothed = smoothed×0.8 + tickMs×0.19999999 — 틱 번호 순서로 되풀이한 값. count 는 기록된 틱 수. */
    static float smoothedMillis(long[] byTickNumber, long count) {
        int n = byTickNumber.length;
        long from = Math.max(0, count - n);
        float smoothed = 0.0F;
        for (long k = from; k < count; k++) {
            float ms = (float) byTickNumber[(int) (k % n)] / 1_000_000.0F;
            smoothed = smoothed * 0.8F + ms * 0.19999999F;
        }
        return smoothed;
    }

    private float averageTickTime() {
        Snapshot s = tickTimes.get();
        return smoothedMillis(s.byTickNumber(), s.count());
    }

    // ------------------------------------------------------------------ DynamicMBean

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException {
        return switch (attribute) {
            case "tickTimes" -> tickTimes.get().byTickNumber();
            case "averageTickTime" -> averageTickTime();
            default -> throw new AttributeNotFoundException("No such attribute: " + attribute);
        };
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException {
        throw new AttributeNotFoundException("Attribute is read-only: " + attribute.getName());
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        AttributeList list = new AttributeList();
        for (String a : attributes) {
            try {
                list.add(new Attribute(a, getAttribute(a)));
            } catch (AttributeNotFoundException ignored) { }
        }
        return list;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) { return new AttributeList(); }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) {
        return null;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        List<MBeanAttributeInfo> attrs = new ArrayList<>();
        attrs.add(new MBeanAttributeInfo("tickTimes", long[].class.getName(), "Historical tick times (ms)", true, false, false));
        attrs.add(new MBeanAttributeInfo("averageTickTime", float.class.getName(), "Current average tick time (ms)", true, false, false));
        return new MBeanInfo(JmxMonitoring.class.getName(), "Information about the server", attrs.toArray(new MBeanAttributeInfo[0]), null, null, null);
    }
}
