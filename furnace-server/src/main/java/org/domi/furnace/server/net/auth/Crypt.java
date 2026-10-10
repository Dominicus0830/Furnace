// 바닐라 Crypt — 서버 RSA 키, 공유 비밀(AES) 복호화, AES/CFB8 스트림 암호, 세션 서버용 serverId 해시.
package org.domi.furnace.server.net.auth;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class Crypt {

    private Crypt() {}

    /** 서버 키 쌍(RSA 1024) — 바닐라 Crypt.generateKeyPair. */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(1024);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key pair generation failed", e);
        }
    }

    /** RSA(PKCS#1) 로 암호화된 바이트 복호화(공유 비밀·검증 토큰). */
    public static byte[] decryptUsingKey(Key key, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(key.getAlgorithm());
        c.init(Cipher.DECRYPT_MODE, key);
        return c.doFinal(data);
    }

    public static byte[] encryptUsingKey(Key key, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(key.getAlgorithm());
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(data);
    }

    public static SecretKey decryptByteToSecretKey(PrivateKey key, byte[] encrypted) throws GeneralSecurityException {
        return new SecretKeySpec(decryptUsingKey(key, encrypted), "AES");
    }

    /** 패킷 스트림 암호 — AES/CFB8, IV = 키. mode 는 Cipher.ENCRYPT_MODE / DECRYPT_MODE. */
    public static Cipher getCipher(int mode, Key key) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/CFB8/NoPadding");
        c.init(mode, key, new IvParameterSpec(key.getEncoded()));
        return c;
    }

    /** SHA-1(serverId, 공유 비밀, 공개키) — 세션 서버 hasJoined 의 serverId 는 이것을 부호 있는 16진수로 쓴 값. */
    public static byte[] digestData(String serverId, PublicKey publicKey, SecretKey secretKey) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-1");
            d.update(serverId.getBytes(StandardCharsets.ISO_8859_1));
            d.update(secretKey.getEncoded());
            d.update(publicKey.getEncoded());
            return d.digest();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String serverIdHash(String serverId, PublicKey publicKey, SecretKey secretKey) {
        return new BigInteger(digestData(serverId, publicKey, secretKey)).toString(16);
    }
}
