package org.domi.furnace.compat.nms;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 시험용 가짜 대응표(H7d). 이름 모양만 실제 형식(ProGuard server.txt·BuildData csrg)을 따르고 난독화 이름은 지어낸 것이다 —
 * 실제 Mojang·Spigot 자료가 아니다. Mojang 쪽 이름은 이 모듈의 shim 에 실제로 있는 것만 쓴다(상속 사슬 시험).
 */
public final class SpigotMappingsFixture {

    private SpigotMappingsFixture() {}

    public static final String MOJANG = """
            # {"fileName":"server.txt","id":"sourceFile"}
            net.minecraft.core.Vec3i -> kb:
                int x -> a
                38:38:int getX() -> u
                40:40:int getY() -> v
                50:50:net.minecraft.core.Vec3i offset(int,int,int) -> c
            net.minecraft.core.BlockPos -> jh:
                20:20:void <init>(int,int,int) -> <init>
                73:73:net.minecraft.core.BlockPos offset(int,int,int) -> c
            net.minecraft.core.BlockPos$MutableBlockPos -> jh$a:
                165:165:net.minecraft.core.BlockPos offset(int,int,int) -> c
            net.minecraft.network.chat.Component -> xo:
                net.minecraft.network.chat.MutableComponent literal(java.lang.String) -> b
                java.lang.String getString() -> getString
            net.minecraft.network.chat.MutableComponent -> xp:
            net.minecraft.network.protocol.Packet -> zw:
            net.minecraft.network.protocol.game.ClientboundSystemChatPacket -> aew:
                1:1:void <init>(net.minecraft.network.chat.Component,boolean) -> <init>
            net.minecraft.server.network.ServerCommonPacketListenerImpl -> atu:
                26:30:void send(net.minecraft.network.protocol.Packet) -> b
            net.minecraft.server.network.ServerGamePacketListenerImpl -> aub:
                net.minecraft.server.level.ServerPlayer player -> f
            net.minecraft.world.entity.Entity -> bsr:
                int tickCount -> af
                1:5:void tick():10:12 -> l
            net.minecraft.world.entity.LivingEntity -> btn:
                1:5:void tick() -> l
            net.minecraft.world.entity.player.Player -> cmx:
                1:5:void tick() -> l
            net.minecraft.server.level.ServerPlayer -> aqv:
                net.minecraft.server.network.ServerGamePacketListenerImpl connection -> c
                1:5:void tick() -> l
            net.minecraft.world.level.Level -> dcw:
                1:1:void doThing() -> p
            net.minecraft.server.level.ServerLevel -> aqu:
                1:1:void otherThing() -> p
            """;

    public static final String CLASSES = """
            # BuildData 클래스 매핑(가짜)
            kb net/minecraft/core/BaseBlockPosition
            jh net/minecraft/core/BlockPosition
            jh$a net/minecraft/core/BlockPosition$MutableBlockPosition
            xo net/minecraft/network/chat/IChatBaseComponent
            xp net/minecraft/network/chat/IChatMutableComponent
            zw net/minecraft/network/protocol/Packet
            aew net/minecraft/network/protocol/game/ClientboundSystemChatPacket
            atu net/minecraft/server/network/ServerCommonPacketListenerImpl
            aub net/minecraft/server/network/PlayerConnection
            bsr net/minecraft/world/entity/Entity
            btn net/minecraft/world/entity/EntityLiving
            cmx net/minecraft/world/entity/player/EntityHuman
            aqv net/minecraft/server/level/EntityPlayer
            dcw net/minecraft/world/level/World
            aqu net/minecraft/server/level/WorldServer
            """;

    public static final String MEMBERS = """
            # BuildData 멤버 매핑(가짜)
            net/minecraft/core/BaseBlockPosition v ()I getY
            net/minecraft/world/entity/Entity af ticksLived
            """;

    public static SpigotMappings parse() throws IOException {
        return SpigotMappings.parse(new StringReader(MOJANG), new StringReader(CLASSES), new StringReader(MEMBERS));
    }

    /** 세 파일을 dir 에 쓰고 경로들을 돌려 준다(Mojang, 클래스, 멤버). */
    public static Path[] write(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path a = Files.writeString(dir.resolve("server.txt"), MOJANG, StandardCharsets.UTF_8);
        Path b = Files.writeString(dir.resolve("cl.csrg"), CLASSES, StandardCharsets.UTF_8);
        Path c = Files.writeString(dir.resolve("members.csrg"), MEMBERS, StandardCharsets.UTF_8);
        return new Path[] {a, b, c};
    }
}
