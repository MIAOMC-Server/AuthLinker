const express = require('express');
const AuthLinkerValidator = require('./utils/AuthLinkerValidator');
const MySQLDatabase = require('./utils/MySQLDatabase');

const app = express();
const PORT = 3000;

// 配置 - 必须与插件配置保持一致
const CONFIG = {
    salt: 'abc123', // 必须与插件中的配置一致
    privateKeyPath: './keys/private.key', // RSA私钥文件路径

    // MySQL数据库配置 - 必须与插件配置保持一致
    database: {
        host: 'localhost',
        port: 3306,
        user: 'root',
        password: 'your_password_here',
        database: 'authlinker',
        tableName: 'auth_records' // 与插件配置的表名一致
    }
};

// 初始化验证器
let validator;
try {
    validator = new AuthLinkerValidator(CONFIG);
} catch (error) {
    console.error('验证器初始化失败:', error.message);
    process.exit(1);
}

// 初始化MySQL数据库连接
let database;
try {
    database = new MySQLDatabase(CONFIG.database);
} catch (error) {
    console.error('数据库初始化失败:', error.message);
    process.exit(1);
}

// 中间件
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 验证端点（使用真实MySQL数据库）
app.get('/verify', async (req, res) => {
    const { data, hash } = req.query;

    if (!data || !hash) {
        return res.status(400).json({
            success: false,
            error: '缺少必要参数: data, hash'
        });
    }

    try {
        // 使用MySQL数据库进行验证
        const result = await validator.verifyRequest(data, hash, database);

        if (!result.valid) {
            return res.status(400).json({
                success: false,
                error: result.error
            });
        }

        const { decryptedData, dbRecord } = result;

        // 标记记录为已使用（更新MySQL数据库）
        const marked = await database.markAsUsed(decryptedData.uuid);
        if (!marked) {
            console.warn(`无法标记记录 ${decryptedData.uuid} 为已使用`);
        }

        // 根据操作类型处理
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
                    error: '不支持的操作类型: ' + decryptedData.action
                });
        }

        res.json({
            success: true,
            message: message,
            data: {
                action: decryptedData.action,
                player_uuid: decryptedData.player_uuid,
                record_uuid: decryptedData.uuid,
                timestamp: new Date().toISOString()
            }
        });

    } catch (error) {
        console.error('验证过程出错:', error);
        res.status(500).json({
            success: false,
            error: '服务器内部错误'
        });
    }
});

// 查询玩家记录端点（调试用）
app.get('/debug/player/:playerUuid', async (req, res) => {
    const { playerUuid } = req.params;
    const { action } = req.query;

    try {
        const records = await database.getRecordsByPlayer(playerUuid, action);
        res.json({
            success: true,
            data: {
                player_uuid: playerUuid,
                records: records
            }
        });
    } catch (error) {
        console.error('查询玩家记录失败:', error);
        res.status(500).json({
            success: false,
            error: '查询失败'
        });
    }
});

// 清理过期记录端点
app.post('/admin/cleanup', async (req, res) => {
    try {
        const deletedCount = await database.cleanupExpiredRecords();
        res.json({
            success: true,
            message: `清理了 ${deletedCount} 条过期记录`
        });
    } catch (error) {
        console.error('清理过期记录失败:', error);
        res.status(500).json({
            success: false,
            error: '清理失败'
        });
    }
});

// 健康检查端点（包含数据库连接状态）
app.get('/health', async (req, res) => {
    try {
        const dbConnected = await database.testConnection();
        res.json({
            success: true,
            status: 'running',
            rsa_loaded: validator.isKeysLoaded(),
            database_connected: dbConnected,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            status: 'error',
            rsa_loaded: validator.isKeysLoaded(),
            database_connected: false,
            error: error.message,
            timestamp: new Date().toISOString()
        });
    }
});

// 优雅关闭处理
process.on('SIGINT', async () => {
    console.log('正在关闭服务器...');
    await database.close();
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('正在关闭服务器...');
    await database.close();
    process.exit(0);
});

// 启动服务器
app.listen(PORT, async () => {
    console.log(`AuthLinker 验证服务器运行在端口 ${PORT}`);
    console.log(`验证端点: http://localhost:${PORT}/verify`);
    console.log(`健康检查: http://localhost:${PORT}/health`);
    console.log(`调试端点: http://localhost:${PORT}/debug/player/{playerUuid}`);
    console.log('');

    // 测试数据库连接
    const dbConnected = await database.testConnection();
    if (!dbConnected) {
        console.error('⚠️  数据库连接失败，请检查配置');
    }

    console.log('配置提醒:');
    console.log('1. 确保已从插件复制私钥文件到 ./keys/private.key');
    console.log('2. 确保 salt 配置与插件中的配置一致');
    console.log('3. 确保 MySQL 数据库配置正确并可连接');
    console.log('4. 确保表名与插件配置一致');
    console.log('5. Token现在通过MySQL数据库查询获取（更安全）');
});
