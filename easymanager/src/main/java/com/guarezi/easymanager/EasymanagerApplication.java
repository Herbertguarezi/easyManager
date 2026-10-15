package com.guarezi.easymanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Fase 1.5, Block D: enables the daily billing jobs
// (billing.infrastructure.scheduling.BillingJobsScheduler).
@EnableScheduling
@SpringBootApplication
public class EasymanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasymanagerApplication.class, args);
	}

}
