// 블록 동작 차분 비교용 봇(수동 실행: -Dfurnace.scn.port=<포트> -Dfurnace.scn.script=<시나리오 파일>).
// 같은 시나리오를 순정 서버와 Furnace 에 돌린 뒤 WorldDump 로 저장된 블록을 비교한다(tools/block-oracle).
//
// 시나리오 줄(# 주석):
//   tp <x> <y> <z> [yaw pitch]       /tp @s 로 옮기고 그 자리·방향을 이동 패킷으로도 보낸다
//   look <yaw> <pitch>               바라보는 방향(move_player_rot)
//   item <hotbar 0..8> <아이템> [개수]  크리에이티브 슬롯 채우기
//   hold <hotbar 0..8>               손에 든 칸
//   sneak on|off                     웅크리기(player_command)
//   use <x> <y> <z> <면 down|up|north|south|west|east> [cx cy cz] [off] [inside]   use_item_on(커서 기본 0.5)
//   useitem [off]                    use_item(허공에 사용)
//   break <x> <y> <z> <면>           player_action 시작(크리에이티브 즉시 파괴)
//   cmd <명령…>                       채팅 명령(앞의 / 없이)
//   wait <틱>                         기다림(틱 = 50ms)
package org.domi.furnace.server.oracle;

import io.netty.buffer.ByteBuf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.net.TestClient;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ScenarioBot {

    private static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");

    @Test
    void run() throws Exception {
        String port = System.getProperty("furnace.scn.port");
        String script = System.getProperty("furnace.scn.script");
        Assumptions.assumeTrue(port != null && script != null);
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        TestClient c = TestClient.join(Integer.parseInt(port), System.getProperty("furnace.scn.name", "Oracle"));
        AtomicInteger seq = new AtomicInteger(1);
        double[] pos = new double[3];
        float[] rot = new float[2];
        Object lock = new Object();
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    ByteBuf f = c.read();
                    int id = Types.readVarInt(f);
                    switch (id) {
                        case 0x26 -> { long v = f.readLong(); c.write(0x18, b -> b.writeLong(v)); }          // keep_alive
                        case 0x0C -> c.write(0x08, b -> b.writeFloat(64f));                                     // chunk_batch_finished
                        case 0x40 -> {                                                                          // player_position
                            double x = f.readDouble(), y = f.readDouble(), z = f.readDouble();
                            float yaw = f.readFloat(), pitch = f.readFloat();
                            byte flags = f.readByte();
                            int tid = Types.readVarInt(f);
                            synchronized (lock) {
                                pos[0] = (flags & 1) != 0 ? pos[0] + x : x;
                                pos[1] = (flags & 2) != 0 ? pos[1] + y : y;
                                pos[2] = (flags & 4) != 0 ? pos[2] + z : z;
                                rot[0] = (flags & 8) != 0 ? rot[0] + yaw : yaw;
                                rot[1] = (flags & 16) != 0 ? rot[1] + pitch : pitch;
                            }
                            c.write(0x00, b -> Types.writeVarInt(b, tid));
                            synchronized (lock) {
                                double px = pos[0], py = pos[1], pz = pos[2];
                                float yw = rot[0], pt = rot[1];
                                c.write(0x1B, b -> { b.writeDouble(px); b.writeDouble(py); b.writeDouble(pz); b.writeFloat(yw); b.writeFloat(pt); b.writeBoolean(true); });
                            }
                        }
                        default -> {}
                    }
                }
            } catch (Exception ignored) {
                // 연결 종료
            }
        }, "scenario-reader");
        reader.setDaemon(true);
        reader.start();
        Thread.sleep(1500);
        int lineNo = 0;
        for (String raw : Files.readAllLines(Path.of(script))) {
            lineNo++;
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')).trim() : raw.trim();
            if (line.isEmpty()) continue;
            String[] a = line.split("\\s+");
            switch (a[0]) {
                case "tp" -> {
                    double x = Double.parseDouble(a[1]), y = Double.parseDouble(a[2]), z = Double.parseDouble(a[3]);
                    float yaw = a.length > 4 ? Float.parseFloat(a[4]) : 0f, pitch = a.length > 5 ? Float.parseFloat(a[5]) : 0f;
                    command(c, "tp @s " + x + " " + y + " " + z + " " + yaw + " " + pitch);
                    Thread.sleep(300);
                    synchronized (lock) {
                        pos[0] = x; pos[1] = y; pos[2] = z; rot[0] = yaw; rot[1] = pitch;
                    }
                    c.write(0x1B, b -> { b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeFloat(yaw); b.writeFloat(pitch); b.writeBoolean(true); });
                }
                case "look" -> {
                    float yaw = Float.parseFloat(a[1]), pitch = Float.parseFloat(a[2]);
                    synchronized (lock) { rot[0] = yaw; rot[1] = pitch; }
                    c.write(0x1C, b -> { b.writeFloat(yaw); b.writeFloat(pitch); b.writeBoolean(true); });
                }
                case "item" -> {
                    int slot = 36 + Integer.parseInt(a[1]);
                    int id = items.idOf(a[2].contains(":") ? a[2] : "minecraft:" + a[2]);
                    if (id < 0) throw new IllegalArgumentException(lineNo + ": 모르는 아이템 " + a[2]);
                    int count = a.length > 3 ? Integer.parseInt(a[3]) : 1;
                    c.write(0x32, b -> { b.writeShort(slot); Types.writeVarInt(b, count); Types.writeVarInt(b, id); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); });
                }
                case "hold" -> {
                    int slot = Integer.parseInt(a[1]);
                    c.write(0x2F, b -> b.writeShort(slot));
                }
                case "sneak" -> {
                    int action = a[1].equals("on") ? 0 : 1;
                    c.write(0x25, b -> { Types.writeVarInt(b, 0); Types.writeVarInt(b, action); Types.writeVarInt(b, 0); });
                }
                case "use" -> {
                    int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]);
                    int face = FACES.indexOf(a[4]);
                    float cx = 0.5f, cy = 0.5f, cz = 0.5f;
                    int i = 5;
                    if (a.length > 7 && isNum(a[5])) {
                        cx = Float.parseFloat(a[5]); cy = Float.parseFloat(a[6]); cz = Float.parseFloat(a[7]);
                        i = 8;
                    }
                    boolean off = false, inside = false;
                    for (; i < a.length; i++) {
                        if (a[i].equals("off")) off = true;
                        if (a[i].equals("inside")) inside = true;
                    }
                    int hand = off ? 1 : 0, s = seq.getAndIncrement();
                    float fx = cx, fy = cy, fz = cz;
                    boolean in = inside;
                    c.write(0x38, b -> {
                        Types.writeVarInt(b, hand);
                        b.writeLong(((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF);
                        Types.writeVarInt(b, face);
                        b.writeFloat(fx); b.writeFloat(fy); b.writeFloat(fz);
                        b.writeBoolean(in);
                        Types.writeVarInt(b, s);
                    });
                    c.write(0x36, b -> Types.writeVarInt(b, hand));   // swing(바닐라 클라이언트처럼)
                }
                case "useitem" -> {
                    int hand = a.length > 1 && a[1].equals("off") ? 1 : 0, s = seq.getAndIncrement();
                    float yw, pt;
                    synchronized (lock) { yw = rot[0]; pt = rot[1]; }
                    c.write(0x39, b -> { Types.writeVarInt(b, hand); Types.writeVarInt(b, s); b.writeFloat(yw); b.writeFloat(pt); });
                }
                case "break" -> {
                    int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]);
                    int face = FACES.indexOf(a[4]), s = seq.getAndIncrement();
                    c.write(0x24, b -> {
                        Types.writeVarInt(b, 0);
                        b.writeLong(((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF);
                        b.writeByte(face);
                        Types.writeVarInt(b, s);
                    });
                }
                case "cmd" -> command(c, line.substring(4));
                case "wait" -> Thread.sleep(50L * Integer.parseInt(a[1]));
                default -> throw new IllegalArgumentException(lineNo + ": 모르는 명령 " + a[0]);
            }
            Thread.sleep(Long.getLong("furnace.scn.stepMs", 120L));
        }
        Thread.sleep(1500);
        command(c, "save-all flush");
        Thread.sleep(3000);
        c.close();
    }

    private static boolean isNum(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void command(TestClient c, String cmd) throws Exception {
        c.write(0x04, b -> Types.writeString(b, cmd));
    }
}
