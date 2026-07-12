package com.payment.settlement.service; // 结算服务包

import com.payment.api.dto.ReconcileBillDTO; // 对账单 DTO
import com.payment.api.service.ReconcileService; // 对账服务接口
import com.payment.common.enums.AccountFlowOpType; // 账户流水操作类型枚举
import com.payment.common.enums.SettleOrderStatus; // 结算订单状态枚举
import com.payment.domain.entity.MerchantSettleAccountEntity; // 商户结算账户实体
import com.payment.domain.entity.ReconcileBillEntity; // 对账单实体
import com.payment.domain.repository.AccountFlowRepository; // 账户流水仓储
import com.payment.domain.repository.MerchantSettleAccountRepository; // 商户结算账户仓储
import com.payment.domain.repository.ReconcileBillRepository; // 对账单仓储
import com.payment.domain.repository.SettlementOrderEntityRepository; // 结算订单仓储
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.stereotype.Service; // Spring 服务注解
import org.springframework.transaction.annotation.Transactional; // 事务注解

import java.math.BigDecimal; // 高精度数值
import java.time.LocalDate; // 本地日期
import java.time.LocalDateTime; // 本地日期时间
import java.time.LocalTime; // 本地时间

/**
 * 对账服务实现，生成和下载商户日对账单。
 */
@Service // 注册为 Spring 服务
public class ReconcileServiceImpl implements ReconcileService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileServiceImpl.class); // 日志记录器

    private final ReconcileBillRepository reconcileBillRepository; // 对账单仓储
    private final MerchantSettleAccountRepository accountRepository; // 商户结算账户仓储
    private final AccountFlowRepository accountFlowRepository; // 账户流水仓储
    private final SettlementOrderEntityRepository settlementOrderRepository; // 结算订单仓储

    /**
     * 构造注入依赖。
     */
    public ReconcileServiceImpl(ReconcileBillRepository reconcileBillRepository,
                                MerchantSettleAccountRepository accountRepository,
                                AccountFlowRepository accountFlowRepository,
                                SettlementOrderEntityRepository settlementOrderRepository) {
        this.reconcileBillRepository = reconcileBillRepository; // 赋值对账单仓储
        this.accountRepository = accountRepository; // 赋值账户仓储
        this.accountFlowRepository = accountFlowRepository; // 赋值流水仓储
        this.settlementOrderRepository = settlementOrderRepository; // 赋值结算订单仓储
    }

    /**
     * 为所有商户生成指定日期的对账单。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public void generateDailyBills(LocalDate billDate) {
        int generated = 0; // 生成计数
        for (MerchantSettleAccountEntity account : accountRepository.findAll()) { // 遍历所有账户
            if (reconcileBillRepository.findByMerchantIdAndBillDate(account.merchantId, billDate).isPresent()) { // 对账单已存在
                continue; // 跳过
            }
            reconcileBillRepository.save(buildBill(account.merchantId, billDate)); // 生成并保存对账单
            generated++; // 计数加一
        }
        log.info("reconcile bills generated date={} count={}", billDate, generated); // 记录生成日志
    }

    /**
     * 下载指定商户和日期的对账单，不存在则即时生成。
     */
    @Override // 实现接口方法
    public ReconcileBillDTO downloadBill(Long merchantId, LocalDate billDate) {
        ReconcileBillEntity bill = reconcileBillRepository.findByMerchantIdAndBillDate(merchantId, billDate) // 查询对账单
                .orElseGet(() -> reconcileBillRepository.save(buildBill(merchantId, billDate))); // 不存在则生成

        ReconcileBillDTO dto = new ReconcileBillDTO(); // 创建 DTO
        dto.merchantId = bill.merchantId; // 商户 ID
        dto.billDate = bill.billDate; // 对账日期
        dto.totalIncome = bill.totalIncome; // 总收入
        dto.totalSettle = bill.totalSettle; // 总结算
        dto.fileUrl = bill.fileUrl; // 文件 URL
        dto.content = renderCsv(bill); // CSV 内容
        return dto; // 返回 DTO
    }

    /**
     * 构建指定商户和日期的对账单实体。
     */
    private ReconcileBillEntity buildBill(Long merchantId, LocalDate billDate) {
        LocalDateTime start = billDate.atStartOfDay(); // 当日开始时间
        LocalDateTime end = billDate.atTime(LocalTime.MAX); // 当日结束时间

        BigDecimal totalIncome = accountFlowRepository.findByMerchantIdAndCreateTimeBetween(merchantId, start, end) // 查询当日流水
                .stream() // 转为流
                .filter(f -> f.opType == AccountFlowOpType.CREDIT.getCode()) // 过滤入账流水
                .map(f -> f.amount) // 取金额
                .reduce(BigDecimal.ZERO, BigDecimal::add); // 汇总

        BigDecimal totalSettle = settlementOrderRepository // 查询当日成功结算
                .findByMerchantIdAndStatusAndUpdateTimeBetween(
                        merchantId, SettleOrderStatus.SUCCESS.getCode(), start, end)
                .stream() // 转为流
                .map(o -> o.settleAmount) // 取结算金额
                .reduce(BigDecimal.ZERO, BigDecimal::add); // 汇总

        ReconcileBillEntity bill = new ReconcileBillEntity(); // 创建对账单
        bill.merchantId = merchantId; // 商户 ID
        bill.billDate = billDate; // 对账日期
        bill.totalIncome = totalIncome; // 总收入
        bill.totalSettle = totalSettle; // 总结算
        bill.fileUrl = "/reconcile/" + merchantId + "/" + billDate + ".csv"; // 文件路径
        bill.createTime = LocalDateTime.now(); // 创建时间
        return bill; // 返回对账单
    }

    /**
     * 将对账单渲染为 CSV 格式字符串。
     */
    private String renderCsv(ReconcileBillEntity bill) {
        return "merchantId,billDate,totalIncome,totalSettle\n" // CSV 表头
                + bill.merchantId + "," + bill.billDate + "," + bill.totalIncome + "," + bill.totalSettle + "\n"; // CSV 数据行
    }
}
