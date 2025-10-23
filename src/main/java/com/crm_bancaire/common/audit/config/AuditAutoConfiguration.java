package com.crm_bancaire.common.audit.config;

import com.crm_bancaire.common.audit.aspect.AuditAspect;
import com.crm_bancaire.common.audit.publisher.AuditPublisher;
import com.crm_bancaire.common.audit.publisher.StreamAuditPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto-configuration principale pour common-audit.
 *
 * Active l'audit automatique avec @Auditable si Spring Cloud Stream est présent.
 */
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnClass(StreamBridge.class)
@Slf4j
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditPublisher auditPublisher(StreamBridge streamBridge) {
        log.info("🔧 Configuring StreamAuditPublisher for automatic audit events");
        return new StreamAuditPublisher(streamBridge);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditAspect auditAspect(AuditPublisher auditPublisher) {
        log.info("🔧 Configuring AuditAspect for @Auditable methods");
        return new AuditAspect(auditPublisher);
    }
}
