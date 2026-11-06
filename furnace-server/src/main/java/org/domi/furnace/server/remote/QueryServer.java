// UDP 질의 서버(enable-query / query.port) — 바닐라 QueryThreadGs4. 수신은 전용 IO 스레드(데몬) 하나, 응답 내용은 QueryHandler 가 만든다.
// Info 는 전역/region 상태를 읽기 전용 스냅숏(플레이어 수·이름)으로만 본다.
package org.domi.furnace.server.remote;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QueryServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(QueryServer.class);

    private final DatagramSocket socket;
    private final QueryHandler handler;
    private final Thread thread;
    private volatile boolean running = true;

    private QueryServer(DatagramSocket socket, QueryHandler handler, String name) {
        this.socket = socket;
        this.handler = handler;
        this.thread = new Thread(this::loop, name);
        this.thread.setDaemon(true);
    }

    /** 바닐라 QueryThreadGs4.create: 포트가 범위 밖이면 경고하고 null, 바인딩 실패도 null. */
    public static QueryServer create(String ip, int port, QueryHandler.Info info) {
        if (port <= 0 || port > 65535) {
            log.warn("Invalid query port {} found in server.properties (queries disabled)", port);
            return null;
        }
        String bind = ip == null || ip.isEmpty() ? "0.0.0.0" : ip;
        try {
            DatagramSocket s = new DatagramSocket(port, InetAddress.getByName(bind));
            QueryServer q = open(s, info);
            log.info("Query running on {}:{}", bind, port);
            return q;
        } catch (Exception e) {
            log.warn("Unable to initialise query system on {}:{}", bind, port, e);
            return null;
        }
    }

    /** 이미 연 소켓으로 시작한다(시험 — 포트 0). */
    public static QueryServer open(DatagramSocket socket, QueryHandler.Info info) {
        SecureRandom rnd = new SecureRandom();
        QueryHandler h = new QueryHandler(info, System::currentTimeMillis, () -> rnd.nextInt(1 << 24));
        return open(socket, h);
    }

    public static QueryServer open(DatagramSocket socket, QueryHandler handler) {
        try {
            socket.setSoTimeout(500);
        } catch (java.net.SocketException e) {
            throw new IllegalStateException(e);
        }
        QueryServer q = new QueryServer(socket, handler, "Query Listener");
        q.thread.start();
        return q;
    }

    public int port() { return socket.getLocalPort(); }

    private void loop() {
        byte[] buf = new byte[1460];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        try {
            while (running) {
                try {
                    p.setLength(buf.length);
                    socket.receive(p);
                    handler.prune();
                    byte[] reply = handler.handle(p.getSocketAddress(), buf, p.getLength());
                    if (reply != null) socket.send(new DatagramPacket(reply, reply.length, p.getSocketAddress()));
                } catch (SocketTimeoutException e) {
                    handler.prune();
                } catch (PortUnreachableException ignored) {
                    // 상대가 이미 닫음
                } catch (IOException e) {
                    if (running) log.warn("Unexpected exception", e);
                    if (socket.isClosed()) return;
                }
            }
        } finally {
            socket.close();
        }
    }

    @Override
    public void close() {
        running = false;
        socket.close();
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
