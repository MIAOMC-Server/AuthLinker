const crypto = require('crypto');
const NodeRSA = require('node-rsa');
const fs = require('fs');
const path = require('path');

/**
 * AuthLinker 验证工具类
 * 处理RSA解密和数据验证逻辑
 */
class AuthLinkerValidator {
    constructor(config) {
        this.config = config;
        this.privateKey = null;
        this.initializeRSA();
    }

    /**
     * 初始化RSA私钥
     */
    initializeRSA() {
        try {
            const keyPath = path.resolve(this.config.privateKeyPath);

            if (!fs.existsSync(keyPath)) {
                throw new Error(`私钥文件不存在: ${keyPath}，请从插件的 plugins/AuthLinker/keys/private.key 复制私钥文件`);
            }

            // 读取Base64编码的私钥
            const keyData = fs.readFileSync(keyPath, 'utf8');
            const keyBuffer = Buffer.from(keyData, 'base64');

            // 创建RSA实例
            this.privateKey = new NodeRSA();
            this.privateKey.importKey(keyBuffer, 'pkcs8-private-der');

            console.log('RSA私钥加载成功');
            return true;
        } catch (error) {
            console.error('RSA私钥加载失败:', error.message);
            throw error;
        }
    }

    /**
     * RSA解密数据
     */
    decryptData(encryptedData) {
        if (!this.privateKey) {
            throw new Error('RSA私钥未初始化');
        }

        try {
            const decryptedData = this.privateKey.decrypt(encryptedData, 'utf8');
            return JSON.parse(decryptedData);
        } catch (error) {
            throw new Error('RSA解密失败: ' + error.message);
        }
    }

    /**
     * 计算哈希值（与插件逻辑保持一致）
     */
    calculateHash(base64Data, token) {
        const input = base64Data + token + this.config.salt;
        return crypto.createHash('sha256').update(input, 'utf8').digest('hex');
    }

    /**
     * 从解密数据重建Base64（用于哈希验证）
     */
    rebuildBase64ForHash(decryptedJson) {
        const jsonString = JSON.stringify({
            uuid: decryptedJson.uuid,
            action: decryptedJson.action,
            player_uuid: decryptedJson.player_uuid,
            expires_time: decryptedJson.expires_time
        });
        return Buffer.from(jsonString, 'utf8').toString('base64');
    }

    /**
     * 验证认证请求
     * 新的验证流程：不依赖URL中的token，从数据库查询token
     */
    async verifyRequest(data, hash, database) {
        try {
            // 1. RSA解密
            const decryptedData = this.decryptData(data);

            // 2. 检查数据完整性
            if (!decryptedData.uuid || !decryptedData.action ||
                !decryptedData.player_uuid || !decryptedData.expires_time) {
                return { valid: false, error: '解密数据格式不正确' };
            }

            // 3. 检查过期时间
            const currentTime = Date.now();
            if (currentTime > decryptedData.expires_time) {
                return { valid: false, error: '验证链接已过期' };
            }

            // 4. 从数据库查询token（这里需要连接到真实数据库）
            const dbRecord = await database.getAuthRecord(decryptedData.uuid);
            if (!dbRecord) {
                return { valid: false, error: '验证记录不存在' };
            }

            if (dbRecord.is_used) {
                return { valid: false, error: '验证链接已被使用' };
            }

            // 5. 重建Base64并验证哈希
            const base64Data = this.rebuildBase64ForHash(decryptedData);
            const expectedHash = this.calculateHash(base64Data, dbRecord.token);

            if (expectedHash !== hash) {
                return { valid: false, error: '哈希验证失败' };
            }

            return {
                valid: true,
                decryptedData,
                dbRecord
            };

        } catch (error) {
            return { valid: false, error: error.message };
        }
    }

    /**
     * 检查RSA密钥是否已加载
     */
    isKeysLoaded() {
        return this.privateKey !== null;
    }
}

module.exports = AuthLinkerValidator;
