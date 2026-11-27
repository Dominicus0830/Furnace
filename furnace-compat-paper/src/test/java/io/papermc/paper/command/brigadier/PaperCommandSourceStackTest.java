package io.papermc.paper.command.brigadier;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/** Paper API CommandSourceStack 다리: NMS 출처가 API 인터페이스를 구현하고 엔티티 없는 보낸이의 값이 바닐라(콘솔)와 같다. */
class PaperCommandSourceStackTest {

    private static CommandSender console() {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[] {CommandSender.class},
                (p, m, a) -> m.getName().equals("getName") ? "CONSOLE" : null);
    }

    @Test
    void nmsStackImplementsPaperApi() {
        CommandSender s = console();
        CommandSourceStack stack = CommandSourceStack.furnaceOf(s);
        assertTrue(stack instanceof io.papermc.paper.command.brigadier.CommandSourceStack);
        assertTrue(stack instanceof com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource);
        assertSame(s, stack.getSender());
        assertSame(s, stack.getBukkitSender());
        assertSame(stack, stack.getHandle());
        assertNull(stack.getExecutor());
        assertNull(stack.getBukkitEntity());
        assertEquals(Vec3.ZERO, stack.getPosition());
        assertSame(Vec2.ZERO, stack.getRotation());
    }

    @Test
    void vec2MatchesVanilla() {
        Vec2 v = new Vec2(3.0F, 4.0F);
        assertEquals(5.0F, v.length());
        assertEquals(25.0F, v.lengthSquared());
        assertEquals(11.0F, v.dot(new Vec2(1.0F, 2.0F)));
        assertTrue(v.negated().equals(new Vec2(-3.0F, -4.0F)));
        assertSame(Vec2.ZERO, new Vec2(0.0F, 0.00001F).normalized());
        assertTrue(v.normalized().equals(new Vec2(0.6F, 0.8F)));
        assertEquals(Float.MIN_VALUE, Vec2.MIN.x);
    }
}
