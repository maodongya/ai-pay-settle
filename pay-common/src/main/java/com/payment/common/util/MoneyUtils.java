package com.payment.common.util; // 工具类所在包

import java.math.BigDecimal; // 高精度数值类型，用于金额计算
import java.math.RoundingMode; // 舍入模式枚举
import java.time.LocalDate; // 本地日期类型
import java.time.format.DateTimeFormatter; // 日期格式化工具
import java.util.concurrent.atomic.AtomicLong; // 原子长整型，用于序号生成

/**
 * 金额计算工具类，提供金额精度处理与费率乘法
 */
public final class MoneyUtils {

    public static final RoundingMode ROUND = RoundingMode.HALF_UP; // 默认四舍五入模式
    public static final int MONEY_SCALE = 2; // 金额保留小数位数

    /**
     * 私有构造，禁止实例化
     */
    private MoneyUtils() {
    }

    /**
     * 将金额规范到标准精度
     *
     * @param value 原始金额
     * @return 保留两位小数的金额
     */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUND); // 按标准精度四舍五入
    }

    /**
     * 按费率计算金额并规范精度
     *
     * @param base 基数金额
     * @param rate 费率
     * @return 计算后的金额
     */
    public static BigDecimal multiplyRate(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).setScale(MONEY_SCALE, ROUND); // 相乘后四舍五入到标准精度
    }
}
