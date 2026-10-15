package com.guarezi.easymanager.billing.application.ports.out;

import com.guarezi.easymanager.billing.domain.Module;

import java.util.UUID;

// Fase 1.5, Block D: called by the retention and trial-expiration jobs
// right after a subscription moves to DELETED — only the state
// transition happens in this phase. Real purging of a module's data
// (products, ad_snapshots, etc.) is Fase 3/7's job, once those tables'
// own deletion logic exists; see NoOpDataPurgePort.
public interface DataPurgePort {
    void purge(UUID subscriptionId, Module module);
}
