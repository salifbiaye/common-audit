package com.crm_bancaire.common.audit.publisher;

import com.crm_bancaire.common.audit.dto.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * Implémentation d'AuditPublisher utilisant Spring Cloud Stream (RabbitMQ/Kafka).
 *
 * Utilise StreamBridge pour publier les événements de manière dynamique
 * sans avoir à déclarer les bindings dans application.yml.
 */
@Slf4j
@RequiredArgsConstructor
public class StreamAuditPublisher extends AbstractAuditPublisher {

    private final StreamBridge streamBridge;

    @Override
    public void publish(AuditEvent event) {
        try {
            String destination = getDestinationName(event.getEntity());

            boolean sent = streamBridge.send(destination, event);

            if (sent) {
                log.debug("✅ Audit event published: {} {} for {} {}",
                    event.getStatus(), event.getAction(), event.getEntity(), event.getEntityId());
            } else {
                log.error("❌ Failed to publish audit event: {} {} for {} {}",
                    event.getStatus(), event.getAction(), event.getEntity(), event.getEntityId());
            }
        } catch (Exception e) {
            log.error("💥 Error publishing audit event for {} {}: {}",
                event.getEntity(), event.getEntityId(), e.getMessage(), e);
        }
    }
}
