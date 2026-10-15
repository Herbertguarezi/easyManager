package com.guarezi.easymanager.billing.infrastructure.scheduling;

import com.guarezi.easymanager.billing.application.DelinquencyJob;
import com.guarezi.easymanager.billing.application.ExchangeRateJob;
import com.guarezi.easymanager.billing.application.RetentionJob;
import com.guarezi.easymanager.billing.application.TrialExpirationJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Fase 1.5, Block D: thin wiring only — all 4 jobs' real logic lives in
// billing.application and is tested directly there (same split already
// used for RateLimitingFilter in Fase 1: the mechanism is a plain,
// unit-tested class; this class is just the trigger).
//
// Known limitation, not fixed now: times are server-local (no explicit
// timezone configured anywhere in the app yet), and a single-instance
// in-process scheduler has the same horizontal-scaling caveat already
// documented for FixedWindowRateLimiter (Fase 1 decisions log) — two
// instances would both fire. Fine for this MVP's single-instance
// deployment target; revisit before scaling out.
//
// Exchange rate runs first — not a hard dependency (the other three
// jobs never read plans.currentMonthTokenQuota), just keeps the day's
// token-quota numbers fresh before anything else touches subscriptions.
@Component
public class BillingJobsScheduler {

    private final ExchangeRateJob exchangeRateJob;
    private final DelinquencyJob delinquencyJob;
    private final RetentionJob retentionJob;
    private final TrialExpirationJob trialExpirationJob;

    public BillingJobsScheduler(ExchangeRateJob exchangeRateJob, DelinquencyJob delinquencyJob,
                                 RetentionJob retentionJob, TrialExpirationJob trialExpirationJob) {
        this.exchangeRateJob = exchangeRateJob;
        this.delinquencyJob = delinquencyJob;
        this.retentionJob = retentionJob;
        this.trialExpirationJob = trialExpirationJob;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runExchangeRateJob() {
        exchangeRateJob.run();
    }

    @Scheduled(cron = "0 15 2 * * *")
    public void runDelinquencyJob() {
        delinquencyJob.run();
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void runRetentionJob() {
        retentionJob.run();
    }

    @Scheduled(cron = "0 45 2 * * *")
    public void runTrialExpirationJob() {
        trialExpirationJob.run();
    }
}
