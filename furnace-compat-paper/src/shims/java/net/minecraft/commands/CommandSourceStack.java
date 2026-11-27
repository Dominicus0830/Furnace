package net.minecraft.commands;

import net.minecraft.network.chat.Component;
import org.bukkit.command.CommandSender;

/**
 * 명령을 실행하는 쪽(Mojang 이름 shim, NMS shim 5번 묶음). Bukkit 명령 보낸이({@link CommandSender}) 하나를 감싼 것이다 — 플레이어는 그 플레이어, 그 밖은
 * 콘솔·명령 블록 같은 보낸이. 바닐라의 권한 단계·자리·시선·결과 콜백 같은 멤버는 아직 없다(호출 위치에서 실패).
 * Paper 의 Brigadier 명령 API(Commands.register)가 아직 없어 플러그인이 이것을 받는 길은 {@link #furnaceOf} 로 만든 것뿐이다.
 */
public class CommandSourceStack implements io.papermc.paper.command.brigadier.PaperCommandSourceStack {

    private final CommandSender sender;

    private CommandSourceStack(CommandSender sender) { this.sender = java.util.Objects.requireNonNull(sender, "sender"); }

    /** 호환 모듈 전용 — 그 보낸이로 실행하는 명령 출처. */
    public static CommandSourceStack furnaceOf(CommandSender sender) { return new CommandSourceStack(sender); }

    /** Paper 가 더한 메서드: 이 출처의 Bukkit 보낸이. */
    @Override
    public CommandSender getBukkitSender() { return sender; }

    @Override
    public CommandSourceStack getHandle() { return this; }

    private net.minecraft.world.entity.Entity entityOrNull() {
        return sender instanceof org.bukkit.craftbukkit.entity.CraftEntity ce ? ce.getHandle() : null;
    }

    /** 바닐라: 실행하는 엔티티(플레이어·엔티티 보낸이), 콘솔·명령 블록은 null. */
    public net.minecraft.world.entity.Entity getEntity() { return entityOrNull(); }

    /** 바닐라: 실행 위치. 엔티티는 그 위치, 명령 블록은 블록 중심, 그 밖(콘솔)은 오버월드 월드 스폰 칸의 아래 모서리(오버월드 없으면 영점). */
    public net.minecraft.world.phys.Vec3 getPosition() {
        var e = entityOrNull();
        if (e != null) return e.position();
        if (sender instanceof org.bukkit.command.BlockCommandSender b) {
            var l = b.getBlock().getLocation();
            return new net.minecraft.world.phys.Vec3(l.getBlockX() + 0.5, l.getBlockY() + 0.5, l.getBlockZ() + 0.5);
        }
        // 바닐라·Paper 콘솔 출처: 오버월드 월드 스폰 칸의 아래 모서리(오버월드가 없을 때만 영점)
        var overworld = getServer().overworld();
        return overworld == null ? net.minecraft.world.phys.Vec3.ZERO : net.minecraft.world.phys.Vec3.atLowerCornerOf(overworld.getSharedSpawnPos());
    }

    /** 바닐라: 실행 시선(x=피치, y=요). 엔티티만 값이 있고 나머지는 영. */
    public net.minecraft.world.phys.Vec2 getRotation() {
        var e = entityOrNull();
        return e == null ? net.minecraft.world.phys.Vec2.ZERO : new net.minecraft.world.phys.Vec2(e.getXRot(), e.getYRot());
    }

    /** 바닐라: 실행 월드. 엔티티·명령 블록은 그 월드, 콘솔은 오버월드. */
    public net.minecraft.server.level.ServerLevel getLevel() {
        var e = entityOrNull();
        if (e != null && e.level() instanceof net.minecraft.server.level.ServerLevel sl) return sl;
        if (sender instanceof org.bukkit.command.BlockCommandSender b) return ((org.bukkit.craftbukkit.CraftWorld) b.getBlock().getWorld()).getHandle();
        return getServer().overworld();
    }

    public net.minecraft.server.MinecraftServer getServer() { return net.minecraft.server.MinecraftServer.getServer(); }

    /**
     * 바닐라: 이 출처에 시스템 메시지를 보낸다. 플레이어는 채팅에 그대로(글 꾸밈·클릭·호버 포함), 콘솔·명령 블록은 평문(서버 언어로 번역한 글)으로 받는다.
     */
    public void sendSystemMessage(Component message) {
        if (sender instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) cp.getHandle().sendSystemMessage(message);
        else sender.sendMessage(message.getString());
    }

    @Override
    public String toString() { return "CommandSourceStack{" + sender.getName() + "}"; }
}
