package com.crm_bancaire.common.audit.publisher;

import com.crm_bancaire.common.audit.dto.AuditEvent;

import java.util.Map;

/**
 * Interface pour publier des événements d'audit.
 *
 * Les implémentations peuvent utiliser RabbitMQ, Kafka, ou un autre système de messaging.
 */
public interface AuditPublisher {

    /**
     * Publie un événement d'audit.
     *
     * @param event L'événement à publier
     */
    void publish(AuditEvent event);

    /**
     * Publie un événement d'audit de succès.
     *
     * @param entity Type d'entité (Customer, User, etc.)
     * @param entityId ID de l'entité
     * @param action Action effectuée (CREATED, UPDATED, etc.)
     */
    void success(String entity, String entityId, String action);

    /**
     * Publie un événement d'audit de succès avec métadonnées.
     *
     * @param entity Type d'entité
     * @param entityId ID de l'entité
     * @param action Action effectuée
     * @param metadata Métadonnées additionnelles
     */
    void success(String entity, String entityId, String action, Map<String, Object> metadata);

    /**
     * Publie un événement d'audit d'échec.
     *
     * @param entity Type d'entité
     * @param entityId ID de l'entité
     * @param action Action effectuée
     * @param error Exception ou erreur
     */
    void failed(String entity, String entityId, String action, Throwable error);

    /**
     * Publie un événement d'audit d'échec avec message custom.
     *
     * @param entity Type d'entité
     * @param entityId ID de l'entité
     * @param action Action effectuée
     * @param errorMessage Message d'erreur
     */
    void failed(String entity, String entityId, String action, String errorMessage);
}
