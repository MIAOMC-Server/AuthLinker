package com.miaomc.authLinker.utils;

import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;

public class RSAEncryptor {
    private final JavaPlugin plugin;
    private final File keyDir;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    public RSAEncryptor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyDir = new File(plugin.getDataFolder(), "keys");
        if (!keyDir.exists()) {
            keyDir.mkdirs();
        }
        loadKeys();
    }

    /**
     * 生成RSA密钥对并保存到文件
     */
    public boolean generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();

            // 保存公钥
            File publicKeyFile = new File(keyDir, "public.key");
            try (FileOutputStream fos = new FileOutputStream(publicKeyFile)) {
                fos.write(Base64.getEncoder().encode(publicKey.getEncoded()));
            }

            // 保存私钥
            File privateKeyFile = new File(keyDir, "private.key");
            try (FileOutputStream fos = new FileOutputStream(privateKeyFile)) {
                fos.write(Base64.getEncoder().encode(privateKey.getEncoded()));
            }

            plugin.getLogger().info("RSA密钥对生成成功并保存到: " + keyDir.getAbsolutePath());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "生成RSA密钥对失败", e);
            return false;
        }
    }

    /**
     * 从文件加载密钥
     */
    private void loadKeys() {
        File publicKeyFile = new File(keyDir, "public.key");
        File privateKeyFile = new File(keyDir, "private.key");

        if (!publicKeyFile.exists() || !privateKeyFile.exists()) {
            plugin.getLogger().warning("密钥文件不存在，请使用命令生成密钥对");
            return;
        }

        try {
            // 加载公钥
            byte[] publicKeyBytes = Base64.getDecoder().decode(Files.readAllBytes(publicKeyFile.toPath()));
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.publicKey = keyFactory.generatePublic(publicKeySpec);

            // 加载私钥
            byte[] privateKeyBytes = Base64.getDecoder().decode(Files.readAllBytes(privateKeyFile.toPath()));
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);

            plugin.getLogger().info("RSA密钥加载成功");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载RSA密钥失败", e);
        }
    }

    /**
     * 使用公钥加密数据
     */
    public String encrypt(String data) {
        if (publicKey == null) {
            throw new IllegalStateException("公钥未加载，请先生成密钥对");
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "RSA加密失败", e);
            throw new RuntimeException("RSA加密失败", e);
        }
    }

    /**
     * 使用私钥解密数据（仅用于测试）
     */
    public String decrypt(String encryptedData) {
        if (privateKey == null) {
            throw new IllegalStateException("私钥未加载，请先生成密钥对");
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "RSA解密失败", e);
            throw new RuntimeException("RSA解密失败", e);
        }
    }

    /**
     * 获取公钥的Base64编码字符串（用于外部使用）
     */
    public String getPublicKeyBase64() {
        if (publicKey == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 检查密钥是否已加载
     */
    public boolean isKeysLoaded() {
        return publicKey != null && privateKey != null;
    }
}
