# AuthLinker TypeScript 示例

这个示例展示了如何在Node.js/TypeScript环境中验证AuthLinker插件生成的认证链接。

## 主要变更

- **移除Base64混淆**: 不再使用自定义的Base64混淆算法
- **采用RSA加密**: 使用RSA 2048位非对称加密保护数据
- **保持哈希验证**: 数据库和哈希生成逻辑保持不变

## 安装依赖

```bash
npm install
```

## 配置步骤

### 1. 复制RSA私钥

从Minecraft服务器插件目录复制私钥文件：

```bash
# 在服务器上生成密钥对
/authlinker keygen

# 复制私钥文件到本项目
cp /path/to/server/plugins/AuthLinker/keys/private.key ./keys/private.key
```

### 2. 配置盐值

在 `index.ts` 中修改 `CONFIG.salt`，确保与插件配置文件中的 `settings.salt` 一致：

```typescript
const CONFIG = {
    salt: 'your_custom_salt_here_change_this', // 必须与插件配置一致
    // ...
};
```

### 3. 数据库连接

在实际使用中，需要连接到与插件相同的数据库来验证token的有效性。示例中使用内存存储仅供演示。

## 运行

```bash
# 开发模式
npm run dev

# 生产模式
npm start
```

## API端点

### 验证认证链接

```
GET /verify?data={encrypted_data}&token={token}&hash={hash}
```

**参数说明：**
- `data`: RSA加密的JSON数据（包含uuid, action, player_uuid, expires_time）
- `token`: 随机生成的验证令牌
- `hash`: SHA-256哈希值，用于验证数据完整性

**返回示例：**

成功：
```json
{
  "success": true,
  "message": "登录验证成功",
  "data": {
    "action": "login",
    "player_uuid": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2024-01-01T12:00:00.000Z"
  }
}
```

失败：
```json
{
  "success": false,
  "error": "验证链接已过期"
}
```

## 安全注意事项

1. **私钥保护**: 私钥文件必须安全存储，不要提交到版本控制系统
2. **HTTPS**: 生产环境必须使用HTTPS来保护传输安全
3. **数据库验证**: 必须连接到真实数据库验证token的有效性
4. **错误处理**: 实现完善的错误处理和日志记录

## 验证流程

1. **RSA解密**: 使用私钥解密 `data` 参数
2. **时间验证**: 检查 `expires_time` 是否过期
3. **哈希验证**: 重建Base64数据并验证哈希值
4. **数据库验证**: 查询数据库确认token有效性（需要实现）
5. **业务处理**: 根据 `action` 类型执行相应的业务逻辑

## 配置插件端点

在插件的 `config.yml` 中设置验证端点：

```yaml
settings:
  endpoint: "https://your-domain.com/verify?data={data}&token={token}&hash={hash}"
```

## 故障排除

### RSA解密失败
- 确保私钥文件格式正确
- 检查私钥文件路径
- 验证私钥文件是否从插件正确复制

### 哈希验证失败
- 检查盐值配置是否与插件一致
- 确保数据传输过程中没有被修改

### 验证链接过期
- 检查服务器时间同步
- 调整插件配置中的 `expired_time` 设置
