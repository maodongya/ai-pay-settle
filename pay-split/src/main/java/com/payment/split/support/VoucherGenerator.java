package com.payment.split.support; // 分账支持工具包

import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.domain.entity.AccountVoucherEntity; // 会计凭证实体
import com.payment.domain.repository.AccountVoucherRepository; // 会计凭证仓储

import java.math.BigDecimal; // 高精度数值
import java.time.LocalDateTime; // 本地日期时间
import java.util.ArrayList; // 动态数组列表
import java.util.List; // 列表

/**
 * 会计凭证生成器，根据分润结果构建复式记账凭证。
 */
public final class VoucherGenerator {

    private static final String RESERVE = "1002"; // 备付金科目
    private static final String PAYABLE_MERCHANT = "2203"; // 应付商户科目
    private static final String FEE_INCOME = "6001"; // 手续费收入科目
    private static final String PAYABLE_L1 = "2204"; // 应付一级代理科目
    private static final String PAYABLE_L2 = "2205"; // 应付二级代理科目
    private static final String PAYABLE_PARTNER = "2206"; // 应付合作方科目

    /**
     * 私有构造，禁止实例化。
     */
    private VoucherGenerator() {
    }

    /**
     * 根据费用计算结果构建会计凭证列表。
     */
    public static List<AccountVoucherEntity> buildVouchers(FeeCalcResultDTO calc) {
        List<AccountVoucherEntity> list = new ArrayList<>(); // 创建凭证列表
        add(list, calc.billNo, calc.merchantId, RESERVE, PAYABLE_MERCHANT, calc.tradeAmount); // 备付金→应付商户
        addIfPositive(list, calc.billNo, calc.merchantId, PAYABLE_MERCHANT, FEE_INCOME, calc.platformFee); // 应付商户→手续费收入
        addIfPositive(list, calc.billNo, calc.merchantId, PAYABLE_MERCHANT, PAYABLE_L1, calc.agentL1Share); // 应付商户→应付一级代理
        addIfPositive(list, calc.billNo, calc.merchantId, PAYABLE_MERCHANT, PAYABLE_L2, calc.agentL2Share); // 应付商户→应付二级代理
        addIfPositive(list, calc.billNo, calc.merchantId, PAYABLE_MERCHANT, PAYABLE_PARTNER, calc.partnerShare); // 应付商户→应付合作方
        return list; // 返回凭证列表
    }

    /**
     * 金额为正时才添加凭证。
     */
    private static void addIfPositive(List<AccountVoucherEntity> list, String billNo, Long merchantId,
                                      String debit, String credit, BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) { // 金额正数
            add(list, billNo, merchantId, debit, credit, amount); // 添加凭证
        }
    }

    /**
     * 添加一条借贷凭证。
     */
    private static void add(List<AccountVoucherEntity> list, String billNo, Long merchantId,
                            String debit, String credit, BigDecimal amount) {
        AccountVoucherEntity v = new AccountVoucherEntity(); // 创建凭证
        v.billNo = billNo; // 账单号
        v.merchantId = merchantId; // 分片键
        v.debitSubject = debit; // 借方科目
        v.creditSubject = credit; // 贷方科目
        v.amount = amount.abs(); // 凭证金额
        v.syncStatus = 0; // 未同步状态
        v.createTime = LocalDateTime.now(); // 创建时间
        list.add(v); // 加入列表
    }
}
