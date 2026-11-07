package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * 서버 월드 shim(Mojang 이름, H6 최소 표면): {@code ((CraftWorld) w).getHandle()}. 같은 CraftWorld 는 같은 shim 이다.
 * 블록 읽기·쓰기({@code getBlockState}·{@code setBlock})는 값 shim(BlockPos·BlockState) 뒤 H7 이 더한다.
 */
public class ServerLevel extends Level {

    /** 호환 모듈 전용(CraftWorld 가 만든다). */
    public ServerLevel(CraftWorld world) { super(world); }

    public long getSeed() { return getWorld().getSeed(); }

    public MinecraftServer getServer() { return MinecraftServer.getServer(); }

    /** 이 월드에 있는 접속 플레이어. */
    public List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (var p : getWorld().getPlayers()) out.add(((CraftPlayer) p).getHandle());
        return out;
    }
}
