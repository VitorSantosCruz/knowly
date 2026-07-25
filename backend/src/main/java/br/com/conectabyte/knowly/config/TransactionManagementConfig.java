package br.com.conectabyte.knowly.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Forces the transaction advisor to be the outermost advice (order 0), so transactions are already
 * open by the time other aspects (e.g. TenantFilterAspect) run their around-advice on the same
 * {@code @Transactional} service methods.
 */
@Configuration
@EnableTransactionManagement(order = 0)
public class TransactionManagementConfig {}
