package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.ResourceLocation;

/**
 * 차원 명령 인자(Mojang 이름 shim): {@code DimensionArgument.dimension()} 은 명령 라이브러리가 "이 인자는 차원이다"라는 표시로 건네는 값이다.
 * Furnace 에는 Brigadier 명령 분배기가 없어(명령은 Bukkit 식 분배) 이 인자로 문장을 읽는 길이 없다 — {@link #parse} 는 UnsupportedNmsCall 이다.
 * 제안 목록은 바닐라처럼 명령 원천이 있어야 하는데 그것도 없어 Brigadier 기본(제안 없음)을 따른다.
 */
class DimensionArgument {

    private static final DimensionArgument INSTANCE = new DimensionArgument();

    private DimensionArgument() {}

    public static DimensionArgument dimension() { return INSTANCE; }

    @Override
    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("DimensionArgument#parse", "Furnace has no Brigadier command dispatcher (commands use the Bukkit dispatcher)");
    }
}
