// @ts-ignore
import { Buffer } from 'buffer';

const STANDARD_BASE64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const DEFAULT_OBFUSCATION_TABLE = 'jQNHxo9a1zVG8dFcyb27XmiwOl0WULnkPsBKqEAZYfer3t5RMDSCJhgvu4pT-.'
    .slice(0, 64);
const DEFAULT_SHIFT = 3;
const DEFAULT_ROTATION_TIMESTAMP = 86400;

function createSeededRandom(seed: number) {
    let seedValue = seed >>> 0;
    return {
        nextDouble: () => {
            seedValue = (1664525 * seedValue + 1013904223) >>> 0;
            return seedValue / 4294967296;
        },
        nextInt: (max: number) => Math.floor(((seedValue = (1664525 * seedValue + 1013904223) >>> 0) / 4294967296) * max)
    };
}

function offsetBase64Table(timestamp: number, obfuscationTable = DEFAULT_OBFUSCATION_TABLE, rotationTimestamp = DEFAULT_ROTATION_TIMESTAMP) {
    const seed = Math.floor(timestamp / (rotationTimestamp * 1000));
    const tableArr = obfuscationTable.slice(0, 64).split('');
    const random = createSeededRandom(seed);
    for (let i = tableArr.length - 1; i > 0; i--) {
        const j = random.nextInt(i + 1);
        [tableArr[i], tableArr[j]] = [tableArr[j], tableArr[i]];
    }
    const rotated = tableArr.join('');
    return rotated;
}

function getMappingTables(table: string) {
    const obfuscationMap: Record<string, string> = {};
    const deobfuscationMap: Record<string, string> = {};
    for (let i = 0; i < STANDARD_BASE64_CHARS.length; i++) {
        const std = STANDARD_BASE64_CHARS[i];
        const obf = table[i] || std;
        obfuscationMap[std] = obf;
        deobfuscationMap[obf] = std;
    }
    obfuscationMap['='] = '_';
    deobfuscationMap['_'] = '=';
    return { obfuscationMap, deobfuscationMap };
}

function reverseReplace(str: string, deobfuscationMap: Record<string, string>) {
    return str.split('').map(c => deobfuscationMap[c] || c).join('');
}

function applyShift(input: string, shiftAmount: number) {
    let result = '';
    for (const c of input) {
        if (c === '=' || c === '_') {
            result += c;
            continue;
        }
        const idx = STANDARD_BASE64_CHARS.indexOf(c);
        if (idx !== -1) {
            let newIdx = (idx + shiftAmount) % STANDARD_BASE64_CHARS.length;
            if (newIdx < 0) newIdx += STANDARD_BASE64_CHARS.length;
            result += STANDARD_BASE64_CHARS[newIdx];
        } else {
            result += c;
        }
    }
    return result;
}

// 反混淆主函数
export function deobfuscate(input: string, options?: {
    shift?: number,
    obfuscationTable?: string,
    rotationTimestamp?: number,
    decodeBase64?: boolean
}): string {
    const shift = options?.shift ?? DEFAULT_SHIFT;
    const obfuscationTable = options?.obfuscationTable ?? DEFAULT_OBFUSCATION_TABLE;
    const rotationTimestamp = options?.rotationTimestamp ?? DEFAULT_ROTATION_TIMESTAMP;
    let json: string;
    try {
        json = Buffer.from(input, 'base64').toString('utf-8');
        // 解析JSON
        const match = json.match(/\{"data":"([^"]+)","time":(\d+)\}/);
        if (!match) throw new Error('Not obfuscated json');
        const data = match[1];
        const timestamp = Number(match[2]);
        // 生成混淆表和映射表
        const rotatedTable = offsetBase64Table(timestamp, obfuscationTable, rotationTimestamp);
        const { deobfuscationMap } = getMappingTables(rotatedTable);
        // 反向字符替换
        const replaced = reverseReplace(data, deobfuscationMap);
        // 反向位移
        const shifted = applyShift(replaced, -shift);
        
        if (decodeBase64) {
            // Base64解码
            const decoded = Buffer.from(shifted, 'base64').toString('utf-8');
            return decoded;
        }
        
        return shifted
        
    } catch (e) {
        // 兼容旧格式（无json包裹）
        const { deobfuscationMap } = getMappingTables(obfuscationTable);
        const replaced = reverseReplace(input, deobfuscationMap);
        const shifted = applyShift(replaced, -shift);
        const decoded = Buffer.from(shifted, 'base64').toString('utf-8');
        return decoded;
    }
}
