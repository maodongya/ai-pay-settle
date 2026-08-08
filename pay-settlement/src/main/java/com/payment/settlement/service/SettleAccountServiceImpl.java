package com.payment.settlement.service;

import com.payment.api.dto.*;
import com.payment.api.service.SettleAccountService;
import com.payment.common.enums.SettleMode;
import com.payment.common.enums.SettleOrderStatus;
import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import com.payment.common.ratelimit.DbRateLimit;
import com.payment.common.ratelimit.DbRateLimitLayer;
import com.payment.common.util.BizSeqGenerator;
import com.payment.control.service.AlertService;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.repository.MerchantContractRepository;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.settlement.support.SettleAccountTxSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 结算账户编排：校验在外，短事务在 SettleAccountTxSupport，渠道 AfterCommit。
 */
@Service
public class SettleAccountServiceImpl implements SettleAccountService {

    private static final Logger log = LoggerFactory.getLogger(SettleAccountServiceImpl.class);

    private final MerchantSettleAccountRepository accountRepository;
    private final MerchantContractRepository contractRepository;
    private final SettlementOrderEntityRepository settlementOrderRepository;
    private final AlertService alertService;
    private final BizSeqGenerator seqGenerator;
    private final SettleAccountTxSupport settleAccountTxSupport;

    public SettleAccountServiceImpl(MerchantSettleAccountRepository accountRepository,
                                    MerchantContractRepository contractRepository,
                                    SettlementOrderEntityRepository settlementOrderRepository,
                                    AlertService alertService,
                                    BizSeqGenerator seqGenerator,
                                    SettleAccountTxSupport settleAccountTxSupport) {
        this.accountRepository = accountRepository;
        this.contractRepository = contractRepository;
        this.settlementOrderRepository = settlementOrderRepository;
        this.alertService = alertService;
        this.seqGenerator = seqGenerator;
        this.settleAccountTxSupport = settleAccountTxSupport;
    }

    @Override
    @DbRateLimit(layer = DbRateLimitLayer.SETTLEMENT)
    public void creditBalance(Long merchantId, String billNo, BigDecimal amount) {
        settleAccountTxSupport.creditBalance(merchantId, billNo, amount);
    }

    @Override
    @DbRateLimit(layer = DbRateLimitLayer.SETTLEMENT)
    public void debitRefundBalance(Long merchantId, String billNo, BigDecimal amount) {
        settleAccountTxSupport.debitRefundBalance(merchantId, billNo, amount);
    }

    @Override
    public SettleAccountDTO queryBalance(Long merchantId) {
        MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
        BigDecimal minWithdraw = contractRepository.findByMerchantId(merchantId)
                .map(c -> c.minWithdraw)
                .orElse(new BigDecimal("100.00"));
        SettleAccountDTO dto = new SettleAccountDTO();
        dto.merchantId = merchantId;
        dto.waitBalance = account.waitBalance;
        dto.frozenBalance = account.frozenBalance;
        dto.availableBalance = account.waitBalance;
        dto.settleMode = account.settleMode;
        dto.minWithdraw = minWithdraw;
        return dto;
    }

    @Override
    @DbRateLimit(layer = DbRateLimitLayer.SETTLEMENT)
    public WithdrawResultDTO applyWithdraw(WithdrawApplyDTO request) {
        Long merchantId = request.merchantId;
        BigDecimal amount = request.withdrawAmount;

        var contract = contractRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
        if (contract.settleMode != SettleMode.D0.getCode() && contract.settleMode != SettleMode.S0.getCode()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "D0 not supported");
        }
        if (amount.compareTo(contract.minWithdraw) < 0) {
            throw BizException.of(ErrorCode.BELOW_MIN_WITHDRAW);
        }
        if (settlementOrderRepository.existsPayingByMerchantId(merchantId)) {
            throw BizException.of(ErrorCode.PAYMENT_IN_PROGRESS);
        }

        MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
        if (account.waitBalance.compareTo(amount) < 0) {
            throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
        }

        String cardNo = request.settleCardNo != null ? request.settleCardNo : account.settleCardNo;
        return settleAccountTxSupport.applyWithdrawLocal(
                request, seqGenerator.applyNo(), seqGenerator.settleNo(), cardNo);
    }

    @Override
    @DbRateLimit(layer = DbRateLimitLayer.SETTLEMENT)
    public void handlePaymentCallback(PaymentCallbackDTO callback) {
        SettlementOrderEntity order = settlementOrderRepository.findBySettleNo(callback.settleNo)
                .orElseThrow(() -> BizException.of(ErrorCode.INVALID_PARAM, "settle not found"));

        if (order.status == SettleOrderStatus.SUCCESS.getCode()) {
            return;
        }

        boolean success = settleAccountTxSupport.applyPaymentCallback(order, callback);
        if (!success) {
            alertService.send(AlertService.PAYMENT_FAIL, order.settleNo + ": " + callback.failReason);
        }
    }

    @Override
    public void runT1Batch(LocalDate batchDate) {
        String batchNo = "BATCH-T1-" + batchDate;
        if (settlementOrderRepository.existsByOriginSettleNo(batchNo)) {
            log.info("T1 batch already ran batchNo={}", batchNo);
            return;
        }

        List<MerchantSettleAccountEntity> accounts = accountRepository
                .findBySettleModeAndWaitBalanceGreaterThanEqualOrderByMerchantIdAsc(
                        SettleMode.T1.getCode(), BigDecimal.ONE);

        int processed = 0;
        for (MerchantSettleAccountEntity account : accounts) {
            try {
                if (processOneT1(account, batchNo)) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("T1 batch failed merchantId={} batchNo={}", account.merchantId, batchNo, e);
            }
        }
        log.info("T1 batch finished batchNo={} processed={}", batchNo, processed);
    }

    private boolean processOneT1(MerchantSettleAccountEntity account, String batchNo) {
        BigDecimal minSettle = contractRepository.findByMerchantId(account.merchantId)
                .map(c -> c.minWithdraw)
                .orElse(new BigDecimal("1.00"));
        if (account.waitBalance.compareTo(minSettle) < 0) {
            return false;
        }
        if (account.settleCardNo == null || account.settleCardNo.isBlank()) {
            return false;
        }
        if (settlementOrderRepository.existsPayingByMerchantId(account.merchantId)) {
            return false;
        }
        BigDecimal amount = account.waitBalance;
        settleAccountTxSupport.processOneT1Local(account, batchNo, seqGenerator.settleNo(), amount);
        return true;
    }

    @Override
    public void retryFailedPayments(int limit) {
        List<SettlementOrderEntity> failed = settlementOrderRepository.findTopNByStatus(
                SettleOrderStatus.FAILED.getCode(), limit);
        int retried = 0;
        for (SettlementOrderEntity order : failed) {
            if (settlementOrderRepository.existsByOriginSettleNoAndStatusNot(
                    order.settleNo, SettleOrderStatus.FAILED.getCode())) {
                continue;
            }
            try {
                MerchantSettleAccountEntity account = accountRepository.findByMerchantId(order.merchantId)
                        .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
                if (account.waitBalance.compareTo(order.settleAmount) < 0) {
                    continue;
                }
                settleAccountTxSupport.retryOnePaymentLocal(order, seqGenerator.settleNo());
                retried++;
            } catch (Exception e) {
                log.warn("payment retry failed settleNo={}", order.settleNo, e);
            }
        }
        if (retried > 0) {
            log.info("payment retry finished count={}", retried);
        }
    }
}
