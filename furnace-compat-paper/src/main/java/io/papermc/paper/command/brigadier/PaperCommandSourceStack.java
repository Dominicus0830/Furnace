package io.papermc.paper.command.brigadier;

import com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

/** Paper 내부: NMS CommandSourceStack 이 구현하는 API 다리(Paper 1.21.1 javap 와 같은 기본 메서드). */
public interface PaperCommandSourceStack extends CommandSourceStack, BukkitBrigadierCommandSource {

    net.minecraft.commands.CommandSourceStack getHandle();

    @Override
    default Location getLocation() {
        Vec2 rot = getHandle().getRotation();
        Vec3 pos = getHandle().getPosition();
        net.minecraft.server.level.ServerLevel level = getHandle().getLevel();
        return new Location(level.getWorld(), pos.x, pos.y, pos.z, rot.y, rot.x);
    }

    @Override
    default CommandSender getSender() { return getHandle().getBukkitSender(); }

    @Override
    default org.bukkit.entity.Entity getExecutor() {
        Entity entity = getHandle().getEntity();
        return entity == null ? null : entity.getBukkitEntity();
    }

    @Override
    default org.bukkit.entity.Entity getBukkitEntity() { return getExecutor(); }

    @Override
    default org.bukkit.World getBukkitWorld() { return getLocation().getWorld(); }

    @Override
    default Location getBukkitLocation() { return getLocation(); }

    @Override
    default CommandSender getBukkitSender() { return getSender(); }
}
