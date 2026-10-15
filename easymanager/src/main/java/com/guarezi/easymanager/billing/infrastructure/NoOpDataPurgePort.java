package com.guarezi.easymanager.billing.infrastructure;

import com.guarezi.easymanager.billing.application.ports.out.DataPurgePort;
import com.guarezi.easymanager.billing.domain.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Fase 1.5, Block D: empty on purpose — see DataPurgePort javadoc. Logs
// so the gap is visible (a subscription reaching DELETED with no real
// data purge happening yet is expected right now, not a silent bug).
@Component
public class NoOpDataPurgePort implements DataPurgePort {

    private static final Logger log = LoggerFactory.getLogger(NoOpDataPurgePort.class);

    @Override
    public void purge(UUID subscriptionId, Module module) {
        log.info("Data purge for subscription {} (module {}) skipped — not implemented until Fase 3/7", subscriptionId, module);
    }
}
