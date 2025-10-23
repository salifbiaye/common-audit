package com.crm_bancaire.common.audit.publisher;

import com.crm_bancaire.common.audit.dto.AuditEvent;
import com.crm_bancaire.common.audit.dto.AuditStatus;
import com.crm_bancaire.common.audit.extractor.ActorInfoExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Implémentation abstraite de base pour AuditPublisher.
 *
 * Gère la création des événements et délègue la publication aux sous-classes.
 */
@Slf4j
public abstract class AbstractAuditPublisher implements AuditPublisher {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Override
    public void success(String entity, String entityId, String action) {
        success(entity, entityId, action, null);
    }

    @Override
    public void success(String entity, String entityId, String action, Map<String, Object> metadata) {
        AuditEvent.AuditEventBuilder builder = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .entity(entity)
            .entityId(entityId)
            .action(action)
            .status(AuditStatus.SUCCESS)
            .timestamp(Instant.now())
            .source(serviceName)
            .metadata(metadata);

        ActorInfoExtractor.fillActorInfo(builder);
        publish(builder.build());
    }

    @Override
    public void failed(String entity, String entityId, String action, Throwable error) {
        failed(entity, entityId, action, error != null ? error.getMessage() : "Unknown error");
    }

    @Override
    public void failed(String entity, String entityId, String action, String errorMessage) {
        AuditEvent.AuditEventBuilder builder = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .entity(entity)
            .entityId(entityId)
            .action(action)
            .status(AuditStatus.FAILED)
            .errorMessage(errorMessage)
            .timestamp(Instant.now())
            .source(serviceName);

        ActorInfoExtractor.fillActorInfo(builder);
        publish(builder.build());
    }

    /**
     * Détermine le nom de la destination (queue/topic) selon l'entité.
     * Par défaut: entity.toLowerCase() + ".events"
     * Exemple: "Customer" → "customer.events"
     */
    protected String getDestinationName(String entity) {
        return entity.toLowerCase() + ".events";
    }
}
