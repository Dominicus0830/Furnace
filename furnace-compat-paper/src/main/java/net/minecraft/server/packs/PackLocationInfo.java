package net.minecraft.server.packs;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;

/** 팩의 이름표(바닐라 record): id·제목·출처·알려진 팩 정보. 채팅 링크(createChatLink)는 팩 저장소와 함께 없다(호출 위치에서 실패). */
public record PackLocationInfo(String id, Component title, PackSource source, Optional<KnownPack> knownPackInfo) {}
