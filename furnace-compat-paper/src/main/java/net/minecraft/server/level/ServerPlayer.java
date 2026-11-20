package net.minecraft.server.level;

import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * 접속한 플레이어 shim(Mojang 이름, H6 최소 표면): {@code ((CraftPlayer) p).getHandle()}.
 * 패킷 연결은 {@code connection.send(packet)}(H7c) — 이 모듈의 clientbound 패킷 shim 만 보낼 수 있다.
 * 옛 이름 {@code playerConnection.sendPacket}(Spigot 1.16 이하)은 없다 — 그 호출 위치에서 UnsupportedNmsCall 이 난다.
 *
 * <p>T-212 다 묶음: 플러그인이 상속해 바닐라 생성자로 "월드에 없는 플레이어"를 만들 수 있다(FAWE 가짜 플레이어 — {@link NmsInheritable}).
 */
@org.domi.furnace.compat.nms.NmsInheritable
public class ServerPlayer extends net.minecraft.world.entity.player.Player {

    /** 바닐라 생성자로 만든, 월드·접속자 목록에 없는 플레이어인가. */
    private final boolean furnaceDetached;

    /** 바닐라처럼 공개 필드(Paper 도 같다): 게임 모드·블록 부수기(NMS shim 3번 묶음). */
    public final ServerPlayerGameMode gameMode;

    /** 호환 모듈 전용(CraftPlayer 가 만든다). */
    public ServerPlayer(CraftPlayer bukkit) {
        super(bukkit);
        this.furnaceDetached = false;
        this.gameMode = new ServerPlayerGameMode(this);
        this.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(this);
    }

    /**
     * 바닐라 생성자(T-212 다 묶음). Paper 처럼 서버에 플레이어를 만들되 월드·접속자 목록에는 넣지 않는다: 그 월드의 공용 스폰 자리·각도에 서고,
     * {@code getBukkitEntity()} 는 이 객체를 {@code getHandle()} 로 주는 {@link CraftPlayer}({@code isOnline()} 거짓)이며, {@link #connection} 은
     * null 이다(바닐라도 목록에 넣을 때 정한다). 이 플레이어로 낸 이벤트의 플레이어 칸도 같은 CraftPlayer 다.
     * <p>Paper 와 다른 점: 통계·발전 과제를 들지 않고 그 UUID 의 파일을 읽거나 쓰지 않는다(결정 S3). clientInformation(언어·주 손 등)은 쓰지 않는다
     * — 바닐라는 {@code updateOptions} 로 넣는데, 이 shim 에는 그 값을 읽는 멤버가 없다.
     */
    public ServerPlayer(net.minecraft.server.MinecraftServer server, ServerLevel level, com.mojang.authlib.GameProfile profile, ClientInformation clientInformation) {
        super(detachedCraftPlayer(level, profile));
        this.furnaceDetached = true;
        this.gameMode = new ServerPlayerGameMode(this);
        getBukkitEntity().adoptHandle(this);
    }

    private static CraftPlayer detachedCraftPlayer(ServerLevel level, com.mojang.authlib.GameProfile profile) {
        java.util.Objects.requireNonNull(level, "level");
        java.util.Objects.requireNonNull(profile, "profile");
        var h = org.domi.furnace.compat.nms.NmsChunks.bridge().detachedPlayer(level.getWorld().furnaceHandle(), profile.getId(), profile.getName(),
                org.domi.furnace.compat.nms.Profiles.properties(profile));
        return org.domi.furnace.compat.paper.Compat.detachedPlayer(h);
    }

    /** 호환 모듈 전용 — 바닐라 생성자로 만든, 월드에 없는 플레이어인가. */
    public boolean furnaceDetached() { return furnaceDetached; }

    /** 월드에 없는 플레이어의 두 손(바닐라: 인벤토리의 고른 칸·보조 손 칸 — 처음엔 빈 스택). 그 객체를 그대로 든다. */
    private net.minecraft.world.item.ItemStack mainHand = net.minecraft.world.item.ItemStack.EMPTY, offHand = net.minecraft.world.item.ItemStack.EMPTY;

    /** 바닐라 {@code LivingEntity#getItemInHand}. 월드에 없는 플레이어만 된다(접속한 플레이어는 UnsupportedNmsCall — 손이 인벤토리 칸이다). */
    @Override
    public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand hand) {
        if (!furnaceDetached) return super.getItemInHand(hand);
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? mainHand : offHand;
    }

    /** 바닐라 {@code LivingEntity#setItemInHand}: 그 객체를 그 손에 둔다(복사하지 않는다). */
    @Override
    public void setItemInHand(net.minecraft.world.InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
        if (!furnaceDetached) {
            super.setItemInHand(hand, stack);
            return;
        }
        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) mainHand = java.util.Objects.requireNonNull(stack, "stack");
        else offHand = java.util.Objects.requireNonNull(stack, "stack");
    }

    /** 바닐라처럼 공개 필드(Paper 도 같다). 바닐라 생성자로 만든 플레이어는 null. */
    public net.minecraft.server.network.ServerGamePacketListenerImpl connection;

    /** 바닐라: overlay 거짓으로. */
    public void sendSystemMessage(net.minecraft.network.chat.Component message) { sendSystemMessage(message, false); }

    /** 바닐라: 채팅을 숨긴 클라이언트는 overlay 만 받는다(서버 플레이어의 같은 메서드로). */
    public void sendSystemMessage(net.minecraft.network.chat.Component message, boolean overlay) {
        org.domi.furnace.compat.nms.NmsChunks.bridge().sendSystemMessage(getBukkitEntity().furnaceHandle(),
                net.minecraft.network.chat.Component.furnace(message), overlay);
    }

    @Override
    public CraftPlayer getBukkitEntity() { return (CraftPlayer) super.getBukkitEntity(); }

    /** 바닐라 ServerPlayer#isSpectator: 게임 모드가 관전. */
    @Override
    public boolean isSpectator() { return furnaceLook("ServerPlayer#isSpectator").spectator(); }

    /** 바닐라 hasDisconnected: 접속이 끊겼는가. 바닐라 생성자로 만든 플레이어는 거짓(끊긴 적이 없다 — Paper 와 같다). */
    public boolean hasDisconnected() { return !furnaceDetached && !getBukkitEntity().furnaceHandle().isOnline(); }

    /** 이 플레이어가 있는 월드. */
    public ServerLevel serverLevel() { return (ServerLevel) level(); }

    /** 바닐라: 지워졌는가. 월드에 없는 플레이어는 지운 적이 없어 거짓이다(Paper 와 같다 — 접속이 끊긴 것으로 보이는 것과 따로). */
    @Override
    public boolean isRemoved() { return !furnaceDetached && super.isRemoved(); }

    /**
     * 바닐라 {@code Entity#absMoveTo(x, y, z, yRot, xRot)}: x·z 는 ±3000만으로 자르고, 방향은 yRot % 360, xRot 는 ±90 으로 자른 뒤 % 360.
     * 월드에 없는 플레이어에만 된다(접속한 플레이어는 Bukkit {@code teleport} 를 쓴다).
     */
    @Override
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        if (!furnaceDetached) {
            super.absMoveTo(x, y, z, yRot, xRot);
            return;
        }
        org.domi.furnace.compat.nms.NmsChunks.bridge().moveDetachedPlayer(getBukkitEntity().furnaceHandle(), Math.clamp(x, -3.0E7, 3.0E7), y,
                Math.clamp(z, -3.0E7, 3.0E7), yRot % 360.0F, Math.clamp(xRot, -90.0F, 90.0F) % 360.0F);
    }
}
