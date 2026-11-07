// 시험 서버 명령 한 번의 결과: 성공 여부, 결과 값(바닐라 명령 결과 수), 그 명령이 보낸 출력(차례대로).
package org.domi.furnace.test;

import java.util.List;
import java.util.stream.Collectors;
import org.domi.furnace.api.text.Text;

public record CommandOutput(boolean success, int result, List<Text> messages) {

    /** 출력을 글자만(번역 키는 영어 기본 문장으로) 한 줄씩. */
    public List<String> lines() {
        return messages.stream().map(Text::plainText).toList();
    }

    /** 출력 전체를 줄바꿈으로 이은 글자. */
    public String text() {
        return messages.stream().map(Text::plainText).collect(Collectors.joining("\n"));
    }
}
