package net.minecraft.network.chat;

import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;

/**
 * 글 내용(Mojang 이름 shim). 바닐라처럼 방문 기본값은 빈 결과, 해석(resolve) 기본값은 자기 자신을 내용으로 하는 글이다.
 * {@code type()}(코덱·종류 표)은 서버에 직렬화 코덱 shim 이 없어 두지 않았다(없는 멤버 — 호출 위치에서 실패).
 */
public interface ComponentContents {

    default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) { return Optional.empty(); }

    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) { return Optional.empty(); }

    default MutableComponent resolve(CommandSourceStack source, Entity entity, int recursionDepth) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return MutableComponent.create(this);
    }
}
