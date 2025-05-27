import express from 'express';
import { deobfuscate } from './deobfuscate';
const mysql = require('mysql2/promise');
const sha256 = require('crypto-js/sha256');

const app = express();
let connection: any;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

interface Options {
    shift: number,
    obfuscationTable: string,
    rotationTimestamp: number,
    decodeBase64?: boolean
}

interface Connection {
    host: string,
    user: string,
    database: string,
    password: string,
}

// 插件配置信息
const options: Options = {
    shift: 3,
    obfuscationTable: "jQNHxo9a1zVG8dFcyb27XmiwOl0WULnkPsBKqEAZYfer3t5RMDSCJhgvu4pT-.",
    rotationTimestamp: 86400
}

// MySQL 配置信息
const connectionData: Connection = {
    host: 'localhost',
    user: 'test1',
    database: 'test1',
    password: 'password',
}

// 数据表定义
const databaseTable = "authlinker";


// 服务端验算哈希
const verifyHash = async (base64: string, hashuuid: string) => {
    try {
        const [result] = await connection.query(
            `SELECT * FROM ${databaseTable} WHERE uuid = ?;`,
            [hashuuid]
        )
        const sqlData = result[0]

        if (result.length === 0 || !sqlData.token) {
            return "Error: Token Not Found!"
        }

        const token = sqlData.token;
        return await sha256(base64 + token + "abc123").toString();

    } catch (err) {
        console.log(err);
    }

}

app.get("/verify", async (req: any, res: any) => {
    try {
        const data = req.query.data as string;
        const hash = req.query.hash as string;

        // 获取明文JSON
        const plainJson = deobfuscate(data, { decodeBase64: true, ...options });
        // 获取明文Base64
        const plainBase64 = deobfuscate(data, { decodeBase64: false, ...options })

        const uuid = JSON.parse(plainJson).uuid;
        const verifiedhash = await verifyHash(plainBase64, uuid)

        res.send(`
            反混淆后JSON：${plainJson}<br/>
            反混淆后Base64：${plainBase64}<br/>
            用户传递哈希：${hash}<br/>
            服务端哈希验算结果：${verifiedhash}<br>
            是否一致：${hash === verifiedhash ? "一致" : "不一致"}
        `);
    } catch (err) {
        res.send(`Something went wrong: <br/> ${err}`)
    }
});

async function main() {

    connection = await mysql.createConnection(connectionData);

    const PORT = 3000;
    app.listen(PORT, () => {
        console.log(`[Server] Running on port ${PORT} `);
    });
}

main();