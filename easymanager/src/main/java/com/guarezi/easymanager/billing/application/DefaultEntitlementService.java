package com.guarezi.easymanager.billing.application;

import com.guarezi.easymanager.auth.application.ports.out.SellerRepository;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import com.guarezi.easymanager.billing.application.ports.in.EntitlementService;
import com.guarezi.easymanager.billing.application.ports.out.PlanRepository;
import com.guarezi.easymanager.billing.application.ports.out.SubscriptionRepository;
import com.guarezi.easymanager.billing.application.ports.out.TokenLedgerRepository;
import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.Plan;
import com.guarezi.easymanager.billing.domain.Subscription;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Fase 1.5, Block C — see EntitlementService's own javadoc for every
// scoping deviation from software-engineering.md sec. 4.6, and
// PROGRESS.md's decisions log for why each one was made. Named
// "Default..." (not "...Impl") because the interface itself already
// carries the "Service" suffix from the architecture doc — this is the
// one and only implementation in this phase.
@Service
public class DefaultEntitlementService implements EntitlementService {

    // RN-INAD-001/RN-SUB-007: past-due and cancellation-requested keep
    // module access while a decision/ladder plays out elsewhere; only
    // these four + TRIAL_ACTIVE count as "usable" (WITHDRAWN/DELETED are
    // new-ish or always were excluded, same bucket as RETENTION).
    private static final Set<SubscriptionStatus> USABLE_STATUSES = EnumSet.of(
            SubscriptionStatus.TRIAL_ACTIVE, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.CANCELLATION_REQUESTED);

    private static final Set<SubscriptionStatus> TRIAL_STATUSES = EnumSet.of(
            SubscriptionStatus.TRIAL_ACTIVE, SubscriptionStatus.TRIAL_RESTRICTED, SubscriptionStatus.TRIAL_EXHAUSTED);

    // RN-TRIAL-004: the trial's own isolated pool, never mixed with a
    // paid plan's cycle math.
    private static final long TRIAL_TOKEN_POOL = 60_000;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TokenLedgerRepository tokenLedgerRepository;
    private final SellerRepository sellerRepository;

    public DefaultEntitlementService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
                                      TokenLedgerRepository tokenLedgerRepository, SellerRepository sellerRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.tokenLedgerRepository = tokenLedgerRepository;
        this.sellerRepository = sellerRepository;
    }

    @Override
    public boolean canUseModule(UUID sellerId, Module module) {
        return subscriptionRepository.findBySellerIdAndModule(sellerId, module)
                .map(Subscription::getStatus)
                .map(USABLE_STATUSES::contains)
                .orElse(false);
    }

    @Override
    public int getStoreLimit(UUID sellerId, Module module) {
        Optional<Subscription> subscription = subscriptionRepository.findBySellerIdAndModule(sellerId, module);
        if (subscription.isEmpty()) {
            return 0;
        }
        if (TRIAL_STATUSES.contains(subscription.get().getStatus())) {
            // RN-PROD-002 states this explicitly for PRODUCTS; applied
            // the same way to ADS_ANALYZER trial, which has no Store
            // limit of its own documented anywhere.
            return 1;
        }
        UUID planId = subscription.get().getPlanId();
        if (planId == null) {
            return 0;
        }
        return planRepository.findById(planId).map(Plan::getStoresLimit).orElse(0);
    }

    @Override
    public long getTokenBalance(UUID sellerId) {
        Optional<Subscription> subscription = subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER);
        if (subscription.isEmpty()) {
            return 0;
        }

        Subscription ads = subscription.get();
        if (TRIAL_STATUSES.contains(ads.getStatus())) {
            return TRIAL_TOKEN_POOL - tokenLedgerRepository.sumAllTokensUsed(ads.getId());
        }

        if (ads.getPlanId() == null || ads.getCurrentTokenPeriodStartDate() == null) {
            return 0;
        }
        long quota = planRepository.findById(ads.getPlanId())
                .map(Plan::getCurrentMonthTokenQuota)
                .map(Long::longValue)
                .orElse(0L);
        long purchased = tokenLedgerRepository.sumTokensPurchasedInCycle(ads.getId(), ads.getCurrentTokenPeriodStartDate());
        long used = tokenLedgerRepository.sumTokensUsedInCycle(ads.getId(), ads.getCurrentTokenPeriodStartDate());
        return quota + purchased - used;
    }

    @Override
    public boolean canExecuteAnalysis(UUID sellerId, UUID storeId) {
        // storeId: accepted for the RF-AU23 contract, unused until Fase 7
        // adds the Store-active guard — see the interface javadoc.
        if (!canUseModule(sellerId, Module.ADS_ANALYZER)) {
            return false;
        }
        if (getTokenBalance(sellerId) <= 0) {
            return false;
        }
        // RN-ABUSE-005: a PENDING seller never executes an analysis, even
        // with module + balance available; INACTIVE is excluded for the
        // same reason a deactivated account can't do anything else.
        return sellerRepository.findById(sellerId)
                .map(Seller::getStatus)
                .map(status -> status == SellerStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    public boolean canBuyExtraPackage(UUID sellerId) {
        return subscriptionRepository.findBySellerIdAndModule(sellerId, Module.ADS_ANALYZER)
                .map(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    public Optional<SubscriptionStatus> getSubscriptionStatus(UUID sellerId, Module module) {
        return subscriptionRepository.findBySellerIdAndModule(sellerId, module).map(Subscription::getStatus);
    }

    @Override
    public boolean isInTrial(UUID sellerId, Module module) {
        return subscriptionRepository.findBySellerIdAndModule(sellerId, module)
                .map(Subscription::getStatus)
                .map(TRIAL_STATUSES::contains)
                .orElse(false);
    }

    @Override
    public boolean isPastDue(UUID sellerId, Module module) {
        return subscriptionRepository.findBySellerIdAndModule(sellerId, module)
                .map(subscription -> subscription.getStatus() == SubscriptionStatus.PAST_DUE)
                .orElse(false);
    }

    @Override
    public boolean isInRetention(UUID sellerId, Module module) {
        return subscriptionRepository.findBySellerIdAndModule(sellerId, module)
                .map(subscription -> subscription.getStatus() == SubscriptionStatus.RETENTION)
                .orElse(false);
    }

    @Override
    public boolean canCreateProduct(UUID sellerId, int currentProductCount) {
        boolean inTrial = subscriptionRepository.findBySellerIdAndModule(sellerId, Module.PRODUCTS)
                .map(Subscription::getStatus)
                .map(TRIAL_STATUSES::contains)
                .orElse(false);
        // RN-PROD-002/003: 15-product cap only during the trial; no cap
        // at all on a paid plan (only the Store count is capped there).
        return !inTrial || currentProductCount < 15;
    }
}
