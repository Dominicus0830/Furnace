// 회귀(T-160): 새 엔티티의 스폰 패킷(add_entity)이 나갈 때 그 엔티티가 이미 번호 색인(World.entityUuidById·entityById)에 있어야 한다.
// 전에는 ChunkLevel.addFreshEntity 가 추적 시작(=스폰 묶음 보내기) 뒤에 색인에 넣어, 네트워크 스레드의 패킷 리스너(ProtocolLib getEntityFromID →
// ServerLevel#getEntity(int))가 막 생긴 엔티티를 가끔 못 찾았다(ProtocolLibBridgeTest:308 이 CI 에서 흔들린 원인, 부하 반복 13/80 재현).
// 바닐라·Paper 는 visibleEntityStorage(EntityLookup)에 넣은 뒤 onTrackingStart → ChunkMap.addEntity 로 추적을 시작한다.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.player.PlayerTrackEntityEvent;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class EntityIndexBeforeSpawnTest {

    @Test
    void newEntityIsIndexedBeforeItsSpawnPacketGoesOut() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.sync();
            var plugin = server.plugin(CounterPlugin.class);
            var world = server.api().world("minecraft:overworld");
            List<String> seen = new CopyOnWriteArrayList<>();
            // 보기 시작하는 순간(엔티티 region 스레드, 스폰 묶음 바로 전) — 같은 스레드라 순서가 결정적이다
            server.api().events().subscribe(plugin, PlayerTrackEntityEvent.class, EventPriority.MONITOR, false, e -> {
                if (e.entity().type().equals("minecraft:pig")) seen.add("track:" + world.entityUuidById(e.entity().entityId()).isPresent());
            });
            // 스폰 패킷을 쓰는 순간(연결 이벤트 루프) — ProtocolLib 리스너가 도는 자리
            server.api().packets().listen(plugin, PacketTypes.Clientbound.ADD_ENTITY, e -> {
                if (e.packet().entityType().equals("minecraft:pig")) seen.add("packet:" + world.entityUuidById(e.packet().entityId()).isPresent());
            });
            var loc = tp.player().location();
            server.execute("summon minecraft:pig " + loc.x() + " " + loc.y() + " " + (loc.z() + 3));
            server.tick(1);
            tp.awaitPacket(PacketTypes.Clientbound.ADD_ENTITY, a -> a.entityType().equals("minecraft:pig"));
            tp.sync();
            assertEquals(List.of("track:true", "packet:true"), seen, "스폰 묶음을 보내기 전에 번호 색인에 있어야 한다");
        }
    }
}
