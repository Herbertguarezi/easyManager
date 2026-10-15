package com.guarezi.easymanager.billing.application.ports.in;

import com.guarezi.easymanager.billing.domain.Module;
import com.guarezi.easymanager.billing.domain.SubscriptionStatus;

import java.util.Optional;
import java.util.UUID;

// software-engineering.md sec. 4.6 contract, scoped to what Fase 1.5 can
// actually implement (see PROGRESS.md Fase 1.5 Block C decisions log for
// every deviation from the literal interface sketched there):
//  - canCreateStore/canActivateStore/getActiveStores: deferred to Fase 2
//    (depend on `stores`, which doesn't exist yet) — not declared here.
//  - canCreateProduct takes the caller-supplied product count, not just
//    sellerId — `products.seller_id` doesn't exist until Fase 3, so this
//    class cannot count products itself yet.
//  - getSubscriptionStatus returns Optional<SubscriptionStatus>, not a
//    bare SubscriptionStatus — SEM_ASSINATURA is not a stored/enum value
//    (see SubscriptionStatus javadoc), so "no subscription" is empty, not
//    a fake enum constant.
public interface EntitlementService {

    boolean canUseModule(UUID sellerId, Module module);

    int getStoreLimit(UUID sellerId, Module module);

    // Implicitly about the seller's ADS_ANALYZER subscription — tokens
    // don't exist for PRODUCTS (RN-TOKEN-001).
    long getTokenBalance(UUID sellerId);

    // storeId is accepted to match the full RF-AU23 contract but unused
    // in this phase — the "Store ativa" guard is Fase 7's job, once
    // store_module_activation (Fase 2) and real Ads Analyzer runs exist.
    boolean canExecuteAnalysis(UUID sellerId, UUID storeId);

    // Implicitly about ADS_ANALYZER — extra token packages don't exist
    // for PRODUCTS.
    boolean canBuyExtraPackage(UUID sellerId);

    Optional<SubscriptionStatus> getSubscriptionStatus(UUID sellerId, Module module);

    boolean isInTrial(UUID sellerId, Module module);

    boolean isPastDue(UUID sellerId, Module module);

    boolean isInRetention(UUID sellerId, Module module);

    // currentProductCount: supplied by the caller (Fase 3's
    // ProductService, once it exists) — see the class javadoc above.
    boolean canCreateProduct(UUID sellerId, int currentProductCount);
}
