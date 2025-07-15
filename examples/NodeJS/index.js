const express = require('express');
const crypto = require('crypto');
const NodeRSA = require('node-rsa');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = 3000;

// 配置 - 必须与插件配置保持一致
const CONFIG = {
    salt: 'your_custom_salt_here_change_this', // 必须与插件中的配置一致
    privateKeyPath: './keys/private.key', // RSA私钥文件路径
};

let privateKey = null;

// 中间件
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

/**
 * 初始化RSA私钥
 */
function initializeRSA() {
    try {
        const keyPath = path.resolve(CONFIG.privateKeyPath);

        if (!fs.existsSync(keyPath)) {
            console.error(`私钥文件不存在: ${keyPath}`);
            console.error('请从插件的 plugins/AuthLinker/keys/private.key 复制私钥文件');
            return false;
        }

        // 读取Base64编码的私钥
        const keyData = fs.readFileSync(keyPath, 'utf8');
        const keyBuffer = Buffer.from(keyData, 'base64');

        // 创建RSA实例
        privateKey = new NodeRSA();
        privateKey.importKey(keyBuffer, 'pkcs8-private-der');

        console.log('RSA私钥加载成功');
        return true;
    } catch (error) {
        console.error('RSA私钥加载失败:', error.message);
        return false;
    }
}

/**
 * RSA解密数据
 */
function decryptData(encryptedData) {
    if (!privateKey) {
        throw new Error('RSA私钥未初始化');
    }

    try {
        const decryptedData = privateKey.decrypt(encryptedData, 'utf8');
        return JSON.parse(decryptedData);
    } catch (error) {
        throw new Error('RSA解密失败: ' + error.message);
    }
}

/**
 * 计算哈希值（与插件逻辑保持一致）
 */
function calculateHash(base64Data, token) {
    const input = base64Data + token + CONFIG.salt;
    return crypto.createHash('sha256').update(input, 'utf8').digest('hex');
}

/**
 * 从解密数据重建Base64（用于哈希验证）
 */
function rebuildBase64ForHash(decryptedJson) {
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
 */
function verifyRequest(data, token, hash) {
    try {
        // 1. RSA解密
        const decryptedData = decryptData(data);

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

        // 4. 重建Base64并验证哈希
        const base64Data = rebuildBase64ForHash(decryptedData);
        const expectedHash = calculateHash(base64Data, token);

        if (expectedHash !== hash) {
            return { valid: false, error: '哈希验证失败' };
        }

        return { valid: true, decryptedData };

    } catch (error) {
        return { valid: false, error: error.message };
    }
}

// 验证端点
app.get('/verify', (req, res) => {
    const { data, token, hash } = req.query;

    if (!data || !token || !hash) {
        return res.status(400).json({
            success: false,
            error: '缺少必要参数'
        });
    }

    const result = verifyRequest(data, token, hash);

    if (!result.valid) {
        return res.status(400).json({
            success: false,
            error: result.error
        });
    }

    // 根据操作类型处理
    const { decryptedData } = result;
    let message = '';

    switch (decryptedData.action) {
        case 'login':
            message = '登录验证成功';
            break;
        case 'suffix':
            message = '后缀设置验证成功';
            break;
        default:
            return res.status(400).json({
                success: false,
                error: '不支持的操作类型'
            });
    }

    res.json({
        success: true,
        message: message,
        data: {
            action: decryptedData.action,
            player_uuid: decryptedData.player_uuid,
            timestamp: new Date().toISOString()
        }
    });
});

// 启动服务器
if (initializeRSA()) {
    app.listen(PORT, () => {
        console.log(`AuthLinker 验证服务器运行在端口 ${PORT}`);
        console.log(`验证端点: http://localhost:${PORT}/verify`);
        console.log('配置说明：请确保盐值与插件配置一致');
    });
} else {
    console.error('服务器启动失败：RSA初始化失败');
    process.exit(1);
}
