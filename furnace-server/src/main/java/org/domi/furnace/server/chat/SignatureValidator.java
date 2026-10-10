// 바닐라 SignatureValidator — 서명 페이로드를 만들어 검증한다.
package org.domi.furnace.server.chat;

import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.List;

@FunctionalInterface
public interface SignatureValidator {

    @FunctionalInterface
    interface Updater {
        void update(Signature output) throws SignatureException;
    }

    boolean validate(Updater updater, byte[] signature);

    default boolean validate(byte[] payload, byte[] signature) { return validate(out -> out.update(payload), signature); }

    static SignatureValidator from(PublicKey key, String algorithm) {
        return (updater, signature) -> {
            try {
                Signature s = Signature.getInstance(algorithm);
                s.initVerify(key);
                updater.update(s);
                return s.verify(signature);
            } catch (Exception e) {
                return false;
            }
        };
    }

    /** 여러 키 중 하나라도 맞으면 참(Mojang 서비스 키 집합). */
    static SignatureValidator anyOf(List<PublicKey> keys, String algorithm) {
        return (updater, signature) -> {
            for (PublicKey k : keys) if (from(k, algorithm).validate(updater, signature)) return true;
            return false;
        };
    }
}
