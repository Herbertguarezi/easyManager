package com.guarezi.easymanager.billing.application.ports.out;

import java.math.BigDecimal;

// RN-TOKEN-002: fetches today's USD/BRL rate for the daily job. Source
// chosen with the user (AwesomeAPI — see PROGRESS.md Fase 1.5 Block D
// decisions log): public, free, no API key, maintained specifically for
// BRL quotes.
public interface ExchangeRateProvider {
    BigDecimal fetchUsdBrlRate();
}
