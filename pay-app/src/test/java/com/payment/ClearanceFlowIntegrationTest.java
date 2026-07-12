package com.payment; // 应用测试包

import com.payment.api.dto.SettleAccountDTO; // 结算账户 DTO
import com.payment.api.dto.TradeBillDTO; // 交易账单 DTO
import com.payment.api.dto.WithdrawApplyDTO; // 提现申请 DTO
import com.payment.api.dto.WithdrawResultDTO; // 提现结果 DTO
import com.payment.api.service.BillAccessService; // 账单接入服务接口
import com.payment.api.service.SettleAccountService; // 结算账户服务接口
import com.payment.common.enums.BillType; // 账单类型枚举
import com.payment.split.job.OutboxDispatchJob; // 发件箱派发任务
import org.junit.jupiter.api.Test; // 测试方法注解
import org.springframework.beans.factory.annotation.Autowired; // 自动注入注解
import org.springframework.boot.test.context.SpringBootTest; // Spring Boot 集成测试

import java.math.BigDecimal; // 高精度数值
import java.util.UUID; // 通用唯一标识符

import static org.junit.jupiter.api.Assertions.assertEquals; // 断言相等
import static org.junit.jupiter.api.Assertions.assertTrue; // 断言为真

/**
 * 清算流程集成测试，验证账单清算、入账和提现全流程。
 */
@SpringBootTest // Spring Boot 集成测试
class ClearanceFlowIntegrationTest {

    private static final BigDecimal EXPECTED_INCOME = new BigDecimal("905.89"); // 预期商户收入

    @Autowired // 自动注入
    private BillAccessService billAccessService; // 账单接入服务

    @Autowired // 自动注入
    private SettleAccountService settleAccountService; // 结算账户服务

    @Autowired // 自动注入
    private OutboxDispatchJob outboxDispatchJob; // 发件箱派发任务

    /**
     * 验证账单清算后商户余额正确入账。
     */
    @Test // 测试方法
    void shouldClearBillAndCreditMerchantBalance() {
        BigDecimal before = settleAccountService.queryBalance(100001L).waitBalance; // 查询清算前余额
        String billNo = newBillNo(); // 生成账单号
        billAccessService.submitBill(buildPayBill(billNo, new BigDecimal("1000.00"))); // 提交支付账单
        outboxDispatchJob.dispatch(); // 派发出件箱消息入账

        SettleAccountDTO balance = settleAccountService.queryBalance(100001L); // 查询清算后余额
        assertEquals(before.add(EXPECTED_INCOME), balance.waitBalance); // 断言余额增加预期收入
    }

    /**
     * 验证入账后可成功申请提现并冻结余额。
     */
    @Test // 测试方法
    void shouldApplyWithdrawAfterCredit() {
        BigDecimal before = settleAccountService.queryBalance(100001L).waitBalance; // 查询初始余额
        billAccessService.submitBill(buildPayBill(newBillNo(), new BigDecimal("1000.00"))); // 提交支付账单
        outboxDispatchJob.dispatch(); // 派发出件箱消息入账

        BigDecimal afterCredit = settleAccountService.queryBalance(100001L).waitBalance; // 查询入账后余额
        assertEquals(before.add(EXPECTED_INCOME), afterCredit); // 断言入账正确

        WithdrawApplyDTO request = new WithdrawApplyDTO(); // 创建提现申请
        request.merchantId = 100001L; // 商户 ID
        request.withdrawAmount = new BigDecimal("500.00"); // 提现 500 元

        WithdrawResultDTO result = settleAccountService.applyWithdraw(request); // 申请提现
        assertTrue(result.applyNo.startsWith("WD")); // 断言申请单号前缀
        assertEquals(afterCredit.subtract(new BigDecimal("500.00")), result.waitBalance); // 断言待结算余额减少
        assertEquals(new BigDecimal("500.00"), result.frozenBalance); // 断言冻结余额增加
    }

    /**
     * 生成唯一账单号。
     */
    private String newBillNo() {
        return "CL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); // CL 前缀 + 16 位随机
    }

    /**
     * 构建测试用支付账单。
     */
    private TradeBillDTO buildPayBill(String billNo, BigDecimal amount) {
        TradeBillDTO bill = new TradeBillDTO(); // 创建账单 DTO
        bill.billNo = billNo; // 账单号
        bill.billType = BillType.PAY.getCode(); // 支付类型
        bill.businessLine = "A"; // 业务线
        bill.category = "A01"; // 品类
        bill.serviceItem = "A0101"; // 服务项目
        bill.merchantId = 100001L; // 商户 ID
        bill.agentId = 200001L; // 一级代理 ID
        bill.secondAgentId = 200002L; // 二级代理 ID
        bill.orderNo = "OD" + billNo; // 订单号
        bill.tradeAmount = amount; // 交易金额
        bill.cityCode = "110000"; // 城市编码
        bill.payChannel = "ALI_PAY"; // 支付渠道
        return bill; // 返回账单
    }
}
