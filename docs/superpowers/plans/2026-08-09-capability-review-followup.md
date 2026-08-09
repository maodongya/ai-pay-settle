# Capability Review Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or inline execution) task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close P0/P1 gaps from `docs/superpowers/specs/2026-08-09-pay-settle-capability-review.md` and align drifted docs.

**Architecture:** Prefer small CAS/Job/Classifier fixes over new platforms; alert webhook and cluster rate-limit deferred to later. Docs get honesty stamps (已实现 / 部分 / 未实现).

**Tech Stack:** Java 17, Spring Boot, MyBatis, RocketMQ, existing Micrometer.

**Spec:** `docs/superpowers/specs/2026-08-09-pay-settle-capability-review.md` §8–§9

## Global Constraints

- Do not enable ACCOUNT_ONLY.
- Keep short-TX boundaries (claim / fee_split / finalize).
- No force-push; commits on `20260808bugfix`.

---

## File map

| Area | Files |
|------|-------|
| R8 fail bill CAS | `ClearanceTaskTxSupport.java`, watchdog tests |
| R6 compensate | `ClearanceTaskCompensateJob.java`, tests |
| R11/R4 classifier | `MqConsumeExceptionClassifier.java`, `MqListenerInvoker.java`, new test |
| R1 T+1 | `SettleAccountServiceImpl` / `SettleAccountTxSupport`, tests |
| R2 PAYING query | new Job + channel query on Mock + service method |
| Docs | `00`, `01`, `05`, `08`, access README, monitor doc snippets |

---

## Task 1: Fail path bill CAS check (R8)

- [ ] Update `failRunningTask` to check `updateStatusByBillNoAndMerchantId` result; log warn if 0
- [ ] Extend watchdog acceptance test
- [ ] Run calc tests

## Task 2: Compensate throttle (R6)

- [ ] Only `createTask` when missing; republish only when task newly created OR status PENDING and optional cooldown flag
- [ ] Default: republish only for newly created tasks; add `pay.compensate.republish-pending-tasks=false` (default false) to opt-in old behavior
- [ ] Update tests

## Task 3: Classifier RATE_LIMITED → ACK (R11)

- [ ] Map `ErrorCode.RATE_LIMITED` to ACK
- [ ] Unit test classifier
- [ ] Document DLQ still rethrows until client max reconsume (note in review; optional log tag)

## Task 4: T+1 idempotency per merchant (R1)

- [ ] Stop using shared batchNo as sole `existsByOriginSettleNo` gate for whole day
- [ ] Use per-merchant origin key (e.g. `batchNo + ":" + merchantId`) or skip exists check at batch level; keep order UK
- [ ] Add/adjust unit test

## Task 5: PAYING status query Job (R2)

- [ ] Add `queryStatus(settleNo)` on channel (Mock returns SUCCESS)
- [ ] `PaymentStatusQueryJob` scans PAYING, calls handlePaymentCallback or dedicated apply
- [ ] Config interval

## Task 6: Doc drift fixes (R16/R9)

- [ ] `docs/00`: Job table + FR-CT01 honesty; ClearanceTaskCompensateJob
- [ ] `docs/01`: alert channels = DB+log (planned webhook); approval not implemented
- [ ] `docs/05`: Redis lock obsolete → CAS; callback CAS-first
- [ ] `docs/08`: validate scope = current impl; WaitOrigin via calc activate
- [ ] `pay-access/README.md`: compensate jobs
- [ ] Monitor doc note if quick

## Task 7: Update capability-review §落地 + tests

- [ ] Mark R1/R2/R6/R8/R11 addressed in spec
- [ ] `mvn -pl pay-calc,pay-access,pay-settlement,pay-mq -am test` relevant suites

---

*Plan version: 2026-08-09*
