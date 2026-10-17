package com.agent.agentplatform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import com.agent.agentplatform.exception.ModelUnavailableException;

@Component
public class SecretCipher {
    private final SecretKeySpec key;
    public SecretCipher(@Value("${app.settings.encryption-key:${app.jwt.secret}}") String secret) throws Exception {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Settings encryption key must contain at least 32 bytes");
        }
        key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(
                ("agent-platform/model-settings/v1:" + secret).getBytes(StandardCharsets.UTF_8)), "AES");
    }
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (Exception exception) { throw new ModelUnavailableException("Could not protect model credentials"); }
    }
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) { return ""; }
        try {
            ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(encrypted));
            byte[] iv = new byte[12]; data.get(iv);
            byte[] body = new byte[data.remaining()]; data.get(body);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new ModelUnavailableException("Saved API key cannot be read; re-enter it in model settings");
        }
    }
}
