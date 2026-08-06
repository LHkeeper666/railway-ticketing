package com.lhkeeper.ticketing.railway_ticketing.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加解密工具（AES/CBC/PKCS5Padding）
 */
@Slf4j
@Component
public class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private final String secretKey;

    public AesUtil(@Value("${aes.secret-key:railway-ticketing-aes-key-2026}") String secretKey) {
        this.secretKey = secretKey;
    }

    public String encrypt(String plainText) {
        if (StringUtil.isBlank(plainText)) {
            return plainText;
        }
        try {
            byte[] keyBytes = new byte[16];
            byte[] srcBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(srcBytes, 0, keyBytes, 0, Math.min(srcBytes.length, 16));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] ivBytes = new byte[16];
            new SecureRandom().nextBytes(ivBytes);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[ivBytes.length + encrypted.length];
            System.arraycopy(ivBytes, 0, combined, 0, ivBytes.length);
            System.arraycopy(encrypted, 0, combined, ivBytes.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES 加密失败", e);
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (StringUtil.isBlank(cipherText)) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length < 16) {
                return cipherText;
            }

            byte[] ivBytes = new byte[16];
            byte[] encryptedBytes = new byte[combined.length - 16];
            System.arraycopy(combined, 0, ivBytes, 0, 16);
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.length);

            byte[] keyBytes = new byte[16];
            byte[] srcBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(srcBytes, 0, keyBytes, 0, Math.min(srcBytes.length, 16));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encryptedBytes);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("AES 解密失败，返回原文（可能是存量明文数据）: {}", cipherText);
            return cipherText;
        }
    }

    public String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        int maskLen = idCard.length() - 7;
        return idCard.substring(0, 3) + "*".repeat(maskLen) + idCard.substring(idCard.length() - 4);
    }

    public String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
