package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * PR #240 검토 R1·R2·R3: 접속한 플레이어의 처리자에서 패킷 라이브러리가 리플렉션으로 찾는 모양(ProtocolLib 5.4.0 의
 * {@code MinecraftFields.getNetworkManager}·{@code PacketListenerInvoker} 와 같은 탐색)이 있고, 채널은 활성이 아니며,
 * 플러그인이 파이프라인 입구를 읽으면 경고가 한 번 나온다.
 */
class PacketPipelineShapeTest {

    @TempDir
    Path tmp;

    @Test
    void connectedPlayerExposesTheShapeAPacketLibraryReflectsOn() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().start()) {
            server.join("Shape");
            server.tick(2);
            ServerPlayer handle = ((CraftPlayer) Bukkit.getPlayer("Shape")).getHandle();
            ServerGamePacketListenerImpl listener = handle.connection;

            // PlayerConnection(상위 클래스 포함)에서 형식이 Connection 인 필드
            Field conn = null;
            for (Class<?> c = listener.getClass(); c != null && conn == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) if (f.getType() == Connection.class) conn = f;
            }
            assertNotNull(conn, "Connection 형식 필드");
            conn.setAccessible(true);
            Connection connection = (Connection) conn.get(listener);
            assertNotNull(connection);

            // Connection 의 정확히 Channel 형식인 비정적 필드 — 활성이 아니어야 라이브러리가 조용히 건너뛴다
            Field ch = null;
            for (Field f : Connection.class.getDeclaredFields()) {
                if (f.getType() == Channel.class && !Modifier.isStatic(f.getModifiers())) ch = f;
            }
            assertNotNull(ch, "Channel 형식 필드");
            Channel channel = (Channel) ch.get(connection);
            assertNotNull(channel);
            assertFalse(channel.isActive(), "자리표시 채널은 활성이 아니다");

            // PacketListenerInvoker 가 정적 초기화에서 찾는 메서드들
            assertNotNull(Connection.class.getMethod("send", Packet.class));
            assertNotNull(Connection.class.getMethod("read", ChannelHandlerContext.class, Packet.class));
            assertNotNull(Connection.class.getMethod("disconnect", Component.class));
            Method getListener = Connection.class.getMethod("getPacketListener");
            assertSame(PacketListener.class, getListener.getReturnType());
            assertSame(listener, getListener.invoke(connection), "연결은 이 접속의 처리자를 돌려준다");
            assertNotNull(listener.getClass().getMethod("send", Packet.class));
            assertNotNull(listener.getClass().getMethod("disconnect", Component.class));
        }
    }

    private static final String YML = """
            name: PipeUser
            version: '1'
            main: pipe.PipePlugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package pipe;
            import org.bukkit.plugin.java.JavaPlugin;
            public final class PipePlugin extends JavaPlugin {
                @Override public void onEnable() {
                    net.minecraft.server.MinecraftServer.getServer().getConnection();
                    net.minecraft.server.MinecraftServer.getServer().getConnection();
                }
            }
            """;

    @Test
    void pluginTouchingThePipelineEntranceIsWarnedOnce() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            Path jar = JarCompiler.jar(tmp.resolve("pipe.jar"), YML, Map.of("pipe.PipePlugin", PLUGIN), Set.of("net.minecraft."));
            try (FurnaceTestServer server = FurnaceTestServer.builder().pluginJar(jar).start()) {
                List<String> hits = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                        .filter(m -> m.contains("PipeUser") && m.contains("패킷 파이프라인")).toList();
                assertEquals(1, hits.size(), () -> String.join("\n", hits));
                assertTrue(hits.get(0).contains("동작하지 않는다"));
            }
        } finally {
            root.detachAppender(logs);
        }
    }
}
