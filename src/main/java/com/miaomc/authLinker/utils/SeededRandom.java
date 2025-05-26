package com.miaomc.authLinker.utils;

/**
 * 可跨平台复现的伪随机数生成器
 * 使用线性同余法(LCG)实现，可以在Java和JavaScript中产生相同的随机序列
 */
public class SeededRandom {
    // LCG参数，使用与Java的Random类不同的参数，以确保与JS实现的一致性
    private static final long multiplier = 1664525L;
    private static final long increment = 1013904223L;
    private static final long modulus = 4294967296L; // 2^32

    private long seed;

    /**
     * 使用指定的种子初始化随机数生成器
     *
     * @param seed 随机数种子
     */
    public SeededRandom(long seed) {
        this.seed = seed;
    }

    /**
     * 生成并返回下一个伪随机数
     *
     * @return 0到1之间的伪随机数
     */
    public double nextDouble() {
        seed = (multiplier * seed + increment) % modulus;
        return (double) seed / modulus;
    }

    /**
     * 生成并返回一个范围在[0, max)之间的整数
     *
     * @param max 上限（不包含）
     * @return 范围在[0, max)之间的整数
     */
    public int nextInt(int max) {
        return (int) Math.floor(nextDouble() * max);
    }

    /*
      JavaScript版本的实现代码（作为注释）

      面向对象版本:
      function SeededRandom(seed) {
          this.seed = seed;

          this.nextDouble = function() {
              // JavaScript中需要使用位运算确保结果在32位范围内
              this.seed = (1664525 * this.seed + 1013904223) >>> 0;
              return this.seed / 4294967296;
          };

          this.nextInt = function(max) {
              return Math.floor(this.nextDouble() * max);
          };
      }

      箭头函数版本:
      const createSeededRandom = (seed) => {
          // LCG参数，与Java版本使用相同的参数
          const multiplier = 1664525;
          const increment = 1013904223;
          const modulus = 4294967296; // 2^32

          // 闭包中保存种子状态
          let seedValue = seed;

          // 生成下一个随机数
          const nextDouble = () => {
              seedValue = (multiplier * seedValue + increment) >>> 0;
              return seedValue / modulus;
          };

          // 返回包含随机数生成函数的对象
          return {
              nextDouble,
              nextInt: (max) => Math.floor(nextDouble() * max)
          };
      };

      偏移Base64表的函数示例（JavaScript版本）:
      const offsetBase64Table = (time: number) => {
          const seed: number = Math.floor(time / config.rotation_timestamp);
          const tableArr: Array<string> = config.base64_obfuscation_table.split('');

          const random = createSeededRandom(seed);

          // Fisher-Yates 洗牌
          for (let i = tableArr.length - 1; i > 0; i--) {
              const j = random.nextInt(i + 1);
              const temp = tableArr[i];
              tableArr[i] = tableArr[j];
              tableArr[j] = temp;
          }

          return tableArr.join('');
      };
     */
}
