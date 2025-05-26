# 申明

该插件仅对组织内部成员公开，不要私下或对外公开这个插件和算法，避免被其他人知晓混淆和哈希算法的细节，否则可能会导致安全问题。

# AuthLinker

AuthLinker 是一个用于生成带鉴权功能的链接的 Java 插件。通过混淆 Base64 传递明文数据，并结合随机 Token 和哈希校验，确保数据完整性和操作安全性，适用于需要临时授权或操作确认的场景。

## 功能特性

- 生成带鉴权的操作链接
- 支持自定义 Base64 混淆算法
- 随机 Token 防止伪造
- 哈希校验防篡改
- 支持链接过期时间、冷却时间等多项安全配置

## 使用方法

1. **配置数据库和参数**

   编辑 `src/main/resources/config.yml`，填写数据库信息和相关参数。

2. **集成插件**

   将项目打包为 jar，放入你的 Java 应用或服务器插件目录。

3. **生成鉴权链接**

   通过调用接口或命令生成鉴权链接，链接格式如：https://example.com/verify?data={data}&hash={hash}
4. 其中 `data` 为混淆后的 Base64 数据，`token` 为随机生成的 Token。

4. **校验流程**

   - 根据传入 Base64 进行反混淆
   - 服务端根据 `hash_uuid` 查询数据库记录
   - 重新计算哈希并比对
   - 校验通过后执行对应操作

## 安全说明

- **数据完整性**：哈希校验可防止数据被篡改
- **数据保密性**：Base64 和混淆仅作简单保护，若需更高保密性建议使用对称加密（如 AES）
- **防重放**：Token 支持时效性和一次性使用，防止链接被重复利用
- **配置安全**：请妥善保管数据库和 salt 等敏感信息

## 反混淆

[反混淆案例 - typescript](https://github.com/MIAOMC-Server/AuthLinker/blob/main/src/main/resources/TypeScript%20%E5%8F%8D%E6%B7%B7%E6%B7%86%E7%A4%BA%E4%BE%8B.ts) 用法：
  
导入ts文件，然后使用 deobfuscate(data) 直接输入混淆后的内容就好，这样就可以获取json和base64明文了。
```ts
import { deobfuscate } from './deobfuscate'; // 假设文件名为 deobfuscate.ts

// 反混淆配置，这里的配置需要和你在yml里配置的一致
const options = {
   shift: 3, // 偏移量，处理时会变为负数，你在yml里怎么配置的就怎么写;
   obfuscationTable: "jQNHxo9a1zVG8dFcyb27XmiwOl0WULnkPsBKqEAZYfer3t5RMDSCJhgvu4pT-.", // 你定义的混淆表;
   rotationTimestamp: 86400 // 时间戳旋转间隔，单位为秒;
}

const rawData = "eyJkYXRhIjoidHVhaG4wcTFWYnNyLmNFQkVicHVOY09SZFNGVEZyX18uYlY0WDBhVS5KQ1IuNUNfRTRnMS5TZ0JkYmFxVnJHckYwRV9IMEFNVmJzclFTQTNIMFpyWHlhR1FTazhkV2FqbldnZWR5VktWYkRyTkpucmRKYWJYY25xTmNGUkVKVXIucmhVRTBhclhjLmhOMHZaRmJWdUZCQ3UueVZ3VkJnWk9TcXVkV0VqblNxUmQyVksuY09fTkpWVEVjT18uNHBoRVdfXyIsInRpbWUiOjE3NDgyNzU0NDM0NTZ9"
const data = deobfuscate(rawData, options); //这里的 options 可以忽略，但是前提是不修改yml配置

console.log(data);

/*
输出结果：
{"uuid":"13f64297-df7b-4223-ba01-3a475d0eff2e","action":"login","player_uuid":"4b87bd2c-7e96-48b2-a5bb-359e8b22ba20","expires_time":1748275743455}
 */
```

## 依赖

Java 8 或更高版本

Maven 或 Gradle 构建工具

MySQL 数据库（或其他支持的数据库）

Minecraft 1.17 或更高版本（如果作为插件使用）

## 配置示例

详见 `src/main/resources/config.yml`：

```yaml
database:
  host: localhost
  port: 3306
  name: authlinker
  username: root
  password: your_password
  tableName: authlinker

settings:
  endpoint: "https://example.com/verify?data={data}&hash={hash}"
  expired_time: 300
  token_length: 12
  salt: "abc123"
  cooldown: 120
  base64_shift: 3
  base64_obfuscation_table: "jQNHxo9a1zVG8dFcyb27XmiwOl0WULnkPsBKqEAZYfer3t5RMDSCJhgvu4pT-." # 混淆表，不可以出现空格和特殊字符
  rotation_timestamp: 86400 # 时间戳旋转间隔，单位为秒
  console_logging: false # 是否在控制台输出提示信息，true 输出信息