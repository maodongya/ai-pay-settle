package com.payment.settlement.account;

import com.payment.account.api.command.AccountCommands;
import com.payment.account.api.command.PostingCommands;
import com.payment.account.api.command.WriteContext;
import com.payment.account.api.dto.ApiDtos;
import com.payment.account.api.dubbo.AccountDubboService;
import com.payment.account.api.dubbo.AccountQueryDubboService;
import com.payment.account.api.dubbo.PostingDubboService;
import com.payment.account.api.dubbo.PostingResultDubboService;
import com.payment.account.api.dubbo.RpcContractConstants;
import com.payment.account.api.query.AccountQueries;
import com.payment.account.api.query.LedgerQueries;
import com.payment.account.api.result.ApiResults;
import com.payment.account.common.enums.AccountType;
import com.payment.account.common.enums.BizType;
import com.payment.account.common.enums.OwnerType;
import com.payment.account.common.enums.TxnStatus;
import com.payment.common.config.PayAccountProperties;
import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 账务过账 Dubbo 适配层。
 */
@Component
@ConditionalOnProperty(prefix = "pay.account", name = "enabled", havingValue = "true")
public class AccountPostingClient {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingClient.class);

    private final PayAccountProperties properties;

    @DubboReference(group = RpcContractConstants.WRITE_GROUP,
            version = RpcContractConstants.VERSION,
            timeout = 3000,
            retries = 0,
            check = false,
            url = "${pay.account.url:dubbo://127.0.0.1:50051}")
    private PostingDubboService postingService;

    @DubboReference(group = RpcContractConstants.QUERY_GROUP,
            version = RpcContractConstants.VERSION,
            timeout = 3000,
            retries = 0,
            check = false,
            url = "${pay.account.url:dubbo://127.0.0.1:50051}")
    private AccountDubboService accountService;

    @DubboReference(group = RpcContractConstants.QUERY_GROUP,
            version = RpcContractConstants.VERSION,
            timeout = 3000,
            retries = 0,
            check = false,
            url = "${pay.account.url:dubbo://127.0.0.1:50051}")
    private AccountQueryDubboService accountQueryService;

    @DubboReference(group = RpcContractConstants.QUERY_GROUP,
            version = RpcContractConstants.VERSION,
            timeout = 3000,
            retries = 0,
            check = false,
            url = "${pay.account.url:dubbo://127.0.0.1:50051}")
    private PostingResultDubboService postingResultService;

    public AccountPostingClient(PayAccountProperties properties) {
        this.properties = properties;
    }

    public WriteContext buildContext(String bizNo, BizType bizType, Instant occurredAt) {
        return new WriteContext(
                properties.getTenantId(),
                properties.getCallerId(),
                UUID.randomUUID().toString(),
                bizNo,
                bizType,
                occurredAt != null ? occurredAt : Instant.now());
    }

    public ApiResults.PostingResult settleMerchant(Long merchantId,
                                                   String billNo,
                                                   BigDecimal settlementAmount,
                                                   BigDecimal platformFee,
                                                   String batchNo,
                                                   Instant occurredAt) {
        WriteContext ctx = buildContext(billNo, BizType.SETTLE_CREDIT, occurredAt);
        PostingCommands.MerchantSettleCommand command = new PostingCommands.MerchantSettleCommand(
                ctx,
                String.valueOf(merchantId),
                amount(settlementAmount),
                nonNegativeAmount(platformFee),
                batchNo);
        return invokePosting(() -> postingService.settleMerchant(command), ctx);
    }

    public ApiResults.PostingResult debitMerchant(Long merchantId,
                                                  String billNo,
                                                  BigDecimal amount,
                                                  String reason,
                                                  Instant occurredAt) {
        WriteContext ctx = buildContext(billNo, BizType.SETTLE_DEBIT, occurredAt);
        PostingCommands.MerchantDebitCommand command = new PostingCommands.MerchantDebitCommand(
                ctx,
                String.valueOf(merchantId),
                amount(amount),
                reason);
        return invokePosting(() -> postingService.debitMerchantSettlement(command), ctx);
    }

    public ApiResults.PostingResult freeze(Long merchantId, String settleNo, BigDecimal amount, Instant occurredAt) {
        WriteContext ctx = buildContext(settleNo, BizType.SETTLE_FREEZE, occurredAt);
        PostingCommands.MerchantFreezeCommand command = new PostingCommands.MerchantFreezeCommand(
                ctx,
                String.valueOf(merchantId),
                settleNo,
                amount(amount));
        return invokePosting(() -> postingService.freezeMerchantFunds(command), ctx);
    }

    public ApiResults.PostingResult unfreeze(Long merchantId, String settleNo, BigDecimal amount, Instant occurredAt) {
        WriteContext ctx = buildContext(settleNo, BizType.SETTLE_UNFREEZE, occurredAt);
        PostingCommands.MerchantUnfreezeCommand command = new PostingCommands.MerchantUnfreezeCommand(
                ctx,
                String.valueOf(merchantId),
                settleNo,
                amount(amount));
        return invokePosting(() -> postingService.unfreezeMerchantFunds(command), ctx);
    }

    public ApiResults.PostingResult deduct(Long merchantId, String settleNo, BigDecimal amount, Instant occurredAt) {
        WriteContext ctx = buildContext(settleNo, BizType.SETTLE_DEDUCT, occurredAt);
        PostingCommands.MerchantDeductCommand command = new PostingCommands.MerchantDeductCommand(
                ctx,
                String.valueOf(merchantId),
                settleNo,
                amount(amount));
        return invokePosting(() -> postingService.deductMerchantFrozenFunds(command), ctx);
    }

    public ApiResults.OpenAccountResult openSettleAccounts(Long merchantId) {
        ApiResults.OpenAccountResult settle = openAccount(merchantId, AccountType.MERCHANT_SETTLE, "201");
        openAccount(merchantId, AccountType.MERCHANT_FROZEN, "202");
        return settle;
    }

    public ApiDtos.BalanceDto querySettleBalance(Long merchantId) {
        return queryAccountBalance(merchantId, AccountType.MERCHANT_SETTLE);
    }

    public ApiDtos.BalanceDto queryFrozenBalance(Long merchantId) {
        return queryAccountBalance(merchantId, AccountType.MERCHANT_FROZEN);
    }

    public boolean isAccountNotFound(ApiResults.PostingResult result) {
        return result != null && "ACC-4040".equals(result.errorCode());
    }

    private ApiResults.OpenAccountResult openAccount(Long merchantId, AccountType accountType, String suffix) {
        String bizNo = "OPEN:" + merchantId + ":" + suffix;
        WriteContext ctx = buildContext(bizNo, BizType.ACCOUNT_OPEN, Instant.now());
        AccountCommands.OpenAccountCommand command = new AccountCommands.OpenAccountCommand(
                ctx,
                OwnerType.MERCHANT,
                String.valueOf(merchantId),
                accountType,
                properties.getCurrency(),
                null,
                null);
        try {
            ApiResults.OpenAccountResult result = accountService.openAccount(command);
            if (result.idempotent() || result.accountNo() != null) {
                return result;
            }
            throw BizException.of(ErrorCode.MERCHANT_INVALID, "open account failed: " + accountType);
        } catch (RpcException e) {
            if (isTimeoutOrNetwork(e)) {
                throw BizException.of(ErrorCode.PAYMENT_CHANNEL_TIMEOUT, e.getMessage());
            }
            throw BizException.of(ErrorCode.PAYMENT_FAILED, e.getMessage());
        }
    }

    private ApiDtos.BalanceDto queryAccountBalance(Long merchantId, AccountType accountType) {
        List<ApiDtos.AccountDto> accounts = accountService.listAccounts(new AccountQueries.AccountsByOwnerQuery(
                properties.getTenantId(),
                OwnerType.MERCHANT,
                String.valueOf(merchantId),
                accountType,
                properties.getCurrency()));
        if (accounts == null || accounts.isEmpty()) {
            throw BizException.of(ErrorCode.MERCHANT_INVALID, "account not found: " + accountType);
        }
        try {
            return accountQueryService.getBalance(new AccountQueries.BalanceQuery(
                    properties.getTenantId(),
                    accounts.get(0).accountNo(),
                    null,
                    AccountQueries.Consistency.STRONG));
        } catch (RpcException e) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "account balance query failed: " + e.getMessage());
        }
    }

    private ApiResults.PostingResult invokePosting(PostingCall call, WriteContext ctx) {
        try {
            ApiResults.PostingResult result = call.execute();
            return requireSuccess(result);
        } catch (RpcException e) {
            if (isTimeoutOrNetwork(e)) {
                ApiResults.PostingResult recovered = queryByIdempotency(ctx.bizNo(), ctx.bizType());
                if (recovered != null) {
                    return requireSuccess(recovered);
                }
            }
            throw toBizException(e, ctx);
        }
    }

    private ApiResults.PostingResult queryByIdempotency(String bizNo, BizType bizType) {
        try {
            return postingResultService.getByIdempotencyKey(
                    new LedgerQueries.PostingByIdempotencyKeyQuery(properties.getTenantId(), bizNo, bizType));
        } catch (RpcException e) {
            log.warn("idempotency query failed bizNo={} bizType={}", bizNo, bizType, e);
            return null;
        }
    }

    private ApiResults.PostingResult requireSuccess(ApiResults.PostingResult result) {
        if (result == null) {
            throw BizException.of(ErrorCode.PAYMENT_FAILED, "empty posting result");
        }
        if (result.idempotent() || result.status() == TxnStatus.SUCCEEDED) {
            return result;
        }
        if ("ACC-4091".equals(result.errorCode())) {
            throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE, result.errorMessage());
        }
        if ("ACC-4040".equals(result.errorCode())) {
            throw BizException.of(ErrorCode.MERCHANT_INVALID, result.errorMessage());
        }
        if (result.retryable()) {
            throw BizException.of(ErrorCode.PAYMENT_CHANNEL_TIMEOUT, result.errorMessage());
        }
        throw BizException.of(ErrorCode.PAYMENT_FAILED, result.errorCode() + ": " + result.errorMessage());
    }

    private BizException toBizException(RpcException e, WriteContext ctx) {
        if (isTimeoutOrNetwork(e)) {
            ApiResults.PostingResult recovered = queryByIdempotency(ctx.bizNo(), ctx.bizType());
            if (recovered != null) {
                try {
                    requireSuccess(recovered);
                } catch (BizException biz) {
                    return biz;
                }
            }
            return BizException.of(ErrorCode.PAYMENT_CHANNEL_TIMEOUT, e.getMessage());
        }
        return BizException.of(ErrorCode.PAYMENT_FAILED, e.getMessage());
    }

    private static boolean isTimeoutOrNetwork(RpcException e) {
        return e.isTimeout() || e.isNetwork() || e.isBiz();
    }

    private PostingCommands.Amount amount(BigDecimal value) {
        return new PostingCommands.Amount(value, properties.getCurrency());
    }

    private PostingCommands.NonNegativeAmount nonNegativeAmount(BigDecimal value) {
        BigDecimal fee = value != null ? value : BigDecimal.ZERO;
        return new PostingCommands.NonNegativeAmount(fee, properties.getCurrency());
    }

    @FunctionalInterface
    private interface PostingCall {
        ApiResults.PostingResult execute();
    }
}
