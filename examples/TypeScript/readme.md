# 简单的验证后端 Express 实现

一个简单哈希验证后端示例。

## 依赖
- express
- mysql2
- crypto-js

## 文件用途

`index.ts`: 主服务端代码，负责启动 Express 服务、处理 /verify 接口请求、调用反混淆和数据库查询逻辑，并返回哈希比对结果。

`deobfuscate.ts`: 实现数据反混淆的核心函数，将前端混淆后的数据还原为原始 JSON，并提取 uuid 等信息。

## 快速开始
1. 安装依赖：
   ```bash
   npm install
   或
   bun install
   ```
2. 配置数据库连接（在 `index.ts` 中修改 MySQL 配置信息）
3. 启动服务：
   ```bash
   npm run start
   或
   bun run start
   ```
4. 访问接口：

   GET `/verify?data=xxx&hash=xxx`

    - `data`：混淆后的数据
    - `hash`：前端生成的哈希

## 目录结构
```
index.ts           # 主服务端代码
deobfuscate.ts     # 反混淆函数
package.json       # 项目依赖
tsconfig.json      # TypeScript 配置
```

## 示例返回
```
反混淆后JSON：{...}
反混淆后Base64：...
用户传递哈希：...
服务端哈希验算结果：...
是否一致：一致/不一致
```

## 说明
- 反混淆逻辑请参考 `deobfuscate.ts`
- 数据库表结构需包含 `uuid` 和 `token` 字段

---
如需自定义混淆算法或数据库结构，请根据实际需求修改代码。
