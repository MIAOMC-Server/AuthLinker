const mysql = require('mysql2/promise');

/**
 * MySQL数据库连接和操作类
 * 用于与AuthLinker插件使用相同的MySQL数据库进行交互
 */
class MySQLDatabase {
    constructor(config) {
        this.config = {
            host: config.host || 'localhost',
            port: config.port || 3306,
            user: config.user || 'root',
            password: config.password || '',
            database: config.database || 'authlinker',
            tableName: config.tableName || 'auth_records',
            // 连接池配置
            connectionLimit: 10,
            acquireTimeout: 60000,
            timeout: 60000,
            reconnect: true
        };

        this.pool = null;
        this.initializePool();
    }

    /**
     * 初始化MySQL连接池
     */
    initializePool() {
        try {
            this.pool = mysql.createPool({
                host: this.config.host,
                port: this.config.port,
                user: this.config.user,
                password: this.config.password,
                database: this.config.database,
                connectionLimit: this.config.connectionLimit,
                acquireTimeout: this.config.acquireTimeout,
                timeout: this.config.timeout,
                reconnect: this.config.reconnect,
                // 字符集配置
                charset: 'utf8mb4',
                // 时区配置
                timezone: '+00:00'
            });

            console.log('MySQL连接池初始化成功');
        } catch (error) {
            console.error('MySQL连接池初始化失败:', error.message);
            throw error;
        }
    }

    /**
     * 根据UUID获取认证记录
     * @param {string} uuid - 记录UUID
     * @returns {Promise<Object|null>} 认证记录或null
     */
    async getAuthRecord(uuid) {
        try {
            const sql = `
                SELECT uuid, player_uuid, action, token, status, is_used, 
                       create_at, update_at, expires_at
                FROM ?? 
                WHERE uuid = ? 
                  AND expires_at > NOW() 
                  AND is_used = FALSE
            `;

            const [rows] = await this.pool.execute(sql, [this.config.tableName, uuid]);

            if (rows.length === 0) {
                return null;
            }

            const record = rows[0];

            // 转换数据类型以匹配接口
            return {
                uuid: record.uuid,
                player_uuid: record.player_uuid,
                action: record.action,
                token: record.token,
                status: record.status,
                is_used: Boolean(record.is_used),
                create_at: record.create_at,
                update_at: record.update_at,
                expires_at: new Date(record.expires_at).getTime()
            };
        } catch (error) {
            console.error('查询认证记录失败:', error.message);
            throw error;
        }
    }

    /**
     * 标记记录为已使用
     * @param {string} uuid - 记录UUID
     * @returns {Promise<boolean>} 操作是否成功
     */
    async markAsUsed(uuid) {
        try {
            const sql = `
                UPDATE ?? 
                SET is_used = TRUE, 
                    status = 'used', 
                    update_at = NOW() 
                WHERE uuid = ? 
                  AND is_used = FALSE
            `;

            const [result] = await this.pool.execute(sql, [this.config.tableName, uuid]);

            return result.affectedRows > 0;
        } catch (error) {
            console.error('标记记录为已使用失败:', error.message);
            throw error;
        }
    }

    /**
     * 根据玩家UUID和操作类型查询最近的记录（用于调试）
     * @param {string} playerUuid - 玩家UUID
     * @param {string} action - 操作类型
     * @returns {Promise<Object[]>} 记录列表
     */
    async getRecordsByPlayer(playerUuid, action = null) {
        try {
            let sql = `
                SELECT uuid, player_uuid, action, token, status, is_used, 
                       create_at, update_at, expires_at
                FROM ??
                WHERE player_uuid = ?
            `;

            const params = [this.config.tableName, playerUuid];

            if (action) {
                sql += ' AND action = ?';
                params.push(action);
            }

            sql += ' ORDER BY create_at DESC LIMIT 10';

            const [rows] = await this.pool.execute(sql, params);

            return rows.map(record => ({
                uuid: record.uuid,
                player_uuid: record.player_uuid,
                action: record.action,
                token: record.token,
                status: record.status,
                is_used: Boolean(record.is_used),
                create_at: record.create_at,
                update_at: record.update_at,
                expires_at: new Date(record.expires_at).getTime()
            }));
        } catch (error) {
            console.error('查询玩家记录失败:', error.message);
            throw error;
        }
    }

    /**
     * 清理过期记录
     * @returns {Promise<number>} 清理的记录数量
     */
    async cleanupExpiredRecords() {
        try {
            const sql = `DELETE FROM ?? WHERE expires_at < NOW()`;
            const [result] = await this.pool.execute(sql, [this.config.tableName]);

            if (result.affectedRows > 0) {
                console.log(`清理了 ${result.affectedRows} 条过期记录`);
            }

            return result.affectedRows;
        } catch (error) {
            console.error('清理过期记录失败:', error.message);
            throw error;
        }
    }

    /**
     * 测试数据库连接
     * @returns {Promise<boolean>} 连接是否正常
     */
    async testConnection() {
        try {
            const sql = 'SELECT 1 as test';
            await this.pool.execute(sql);
            console.log('数据库连接测试成功');
            return true;
        } catch (error) {
            console.error('数据库连接测试失败:', error.message);
            return false;
        }
    }

    /**
     * 关闭连接池
     */
    async close() {
        if (this.pool) {
            await this.pool.end();
            console.log('MySQL连接池已关闭');
        }
    }
}

module.exports = MySQLDatabase;
