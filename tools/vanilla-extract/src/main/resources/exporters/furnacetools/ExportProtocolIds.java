package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;

/**
 * 프로토콜 패킷 종류·번호 표. 읽는 것: <상태>PacketTypes 의 PacketType 필드(선언 순서) 와 <상태>Protocols 의 방향별 등록부(등록 순서 = 번호).
 * 서버를 띄우지 않고 클래스만 읽는다. 출력 한 줄: type <상태> <방향> <와이어 이름> / id <프로토콜> <방향> <번호> <holder>:<와이어 이름>
 * (holder = 그 패킷 종류가 선언된 PacketTypes 클래스의 상태 이름). 인자: <출력>.
 */
public class ExportProtocolIds {
    static final String P = "net.minecraft.network.protocol.";
    /** 상태 이름 → PacketTypes 클래스. */
    static final Map<String, String> TYPES = new TreeMap<>(Map.of(
            "common", P + "common.CommonPacketTypes", "configuration", P + "configuration.ConfigurationPacketTypes", "cookie", P + "cookie.CookiePacketTypes",
            "game", P + "game.GamePacketTypes", "handshake", P + "handshake.HandshakePacketTypes", "login", P + "login.LoginPacketTypes",
            "ping", P + "ping.PingPacketTypes", "status", P + "status.StatusPacketTypes"));
    /** 프로토콜 이름 → Protocols 클래스. */
    static final Map<String, String> PROTOCOLS = new TreeMap<>(Map.of(
            "configuration", P + "configuration.ConfigurationProtocols", "game", P + "game.GameProtocols", "handshake", P + "handshake.HandshakeProtocols",
            "login", P + "login.LoginProtocols", "status", P + "status.StatusProtocols"));

    static String flow(PacketFlow f) {
        return f == PacketFlow.SERVERBOUND ? "serverbound" : "clientbound";
    }

    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        int protocol = SharedConstants.getProtocolVersion();
        IdentityHashMap<PacketType<?>, String> holderOf = new IdentityHashMap<>();
        try (PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8")) {
            out.println("# 프로토콜 " + protocol + "(Minecraft " + Vanilla.mc() + ") 패킷 종류·번호 표 — tools/compat/protocol-ids-extract.py 로 바닐라 클래스에서 뽑은 사실(번호·이름)만 담았다.");
            out.println("# type <PacketTypes 상태> <방향> <와이어 이름>   : 그 <상태>PacketTypes 클래스가 선언한 종류(선언 순서)");
            out.println("# id <프로토콜> <방향> <번호> <PacketTypes 상태>:<와이어 이름> : 프로토콜 등록부의 번호(등록 순서). bundle 종류는 번호가 없다");
            for (var e : TYPES.entrySet()) {
                Class<?> c = Class.forName(e.getValue());
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || f.getType() != PacketType.class) continue;
                    f.setAccessible(true);
                    PacketType<?> t = (PacketType<?>) f.get(null);
                    holderOf.put(t, e.getKey());
                    out.println("type " + e.getKey() + " " + flow(t.flow()) + " " + t.id().getPath());
                }
            }
            for (var e : PROTOCOLS.entrySet()) {
                Class<?> c = Class.forName(e.getValue());
                // 방향마다 한 개의 등록부(ProtocolInfo.Unbound). 클라이언트 쪽 → 서버 쪽 순서가 아니라 "첫 패킷의 방향"으로 정렬한다(clientbound 먼저).
                Map<String, List<String>> byFlow = new LinkedHashMap<>();
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || f.getType() != ProtocolInfo.Unbound.class) continue;
                    f.setAccessible(true);
                    Object unbound = f.get(null);
                    List<PacketType<?>> order = listPackets(unbound);
                    if (order.isEmpty()) continue;
                    String flow = flow(order.get(0).flow());
                    List<String> lines = new ArrayList<>();
                    for (int i = 0; i < order.size(); i++) {
                        PacketType<?> t = order.get(i);
                        lines.add("id " + e.getKey() + " " + flow + " " + i + " " + holderOf.get(t) + ":" + t.id().getPath());
                    }
                    byFlow.put(flow, lines);
                }
                for (String flow : new String[] {"clientbound", "serverbound"}) for (String l : byFlow.getOrDefault(flow, List.of())) out.println(l);
            }
        }
        System.out.println("protocol " + protocol);
    }

    /** ProtocolInfo.Unbound.listPackets(visitor) 가 방문하는 패킷 종류를 등록 순서로(방문자의 인자 구성은 판마다 달라 프록시로 받는다). */
    static List<PacketType<?>> listPackets(Object unbound) throws Exception {
        List<PacketType<?>> res = new ArrayList<>();
        Method m = null;
        for (Method x : ProtocolInfo.Unbound.class.getMethods()) if (x.getName().equals("listPackets")) m = x;
        if (m == null) throw new IllegalStateException("ProtocolInfo.Unbound.listPackets 가 없다 — 이 판은 도구를 새로 맞춰야 한다");
        Class<?> visitor = m.getParameterTypes()[0];
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getName().equals("accept") && args != null && args.length > 0 && args[0] instanceof PacketType<?> t) res.add(t);
            return null;
        };
        m.invoke(unbound, Proxy.newProxyInstance(ExportProtocolIds.class.getClassLoader(), new Class<?>[] {visitor}, h));
        return res;
    }
}
