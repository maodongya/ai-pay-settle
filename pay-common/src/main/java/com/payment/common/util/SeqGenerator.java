package com.payment.common.util; // 工具类所在包

import java.time.LocalDate; // 本地日期类型，用于生成日期前缀
import java.time.format.DateTimeFormatter; // 日期格式化工具
import java.util.concurrent.atomic.AtomicLong; // 原子长整型，保证序号线程安全递增

/**
 * 业务单号生成器，生成账单号、结算号、提现申请号
 */
public final class SeqGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd"); // 日期格式 yyyyMMdd
    private static final AtomicLong BILL_SEQ = new AtomicLong(1); // 账单序号计数器
    private static final AtomicLong SETTLE_SEQ = new AtomicLong(1); // 结算序号计数器
    private static final AtomicLong WITHDRAW_SEQ = new AtomicLong(1); // 提现序号计数器

    /**
     * 私有构造，禁止实例化
     */
    private SeqGenerator() {
    }

    /**
     * 生成账单号，格式 CL + 日期 + 6 位序号
     *
     * @return 账单号
     */
    public static String billNo() {
        return "CL" + LocalDate.now().format(DATE) + String.format("%06d", BILL_SEQ.getAndIncrement()); // 拼接前缀、日期与序号
    }

    /**
     * 生成结算号，格式 ST + 日期 + 8 位序号
     *
     * @return 结算号
     */
    public static String settleNo() {
        return "ST" + LocalDate.now().format(DATE) + String.format("%08d", SETTLE_SEQ.getAndIncrement()); // 拼接前缀、日期与序号
    }

    /**
     * 生成提现申请号，格式 WD + 日期 + 8 位序号
     *
     * @return 提现申请号
     */
    public static String applyNo() {
        return "WD" + LocalDate.now().format(DATE) + String.format("%08d", WITHDRAW_SEQ.getAndIncrement()); // 拼接前缀、日期与序号
    }
}
