package com.crm_bancaire.common.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Événement d'audit universel.
 *
 * Cet événement est publié automatiquement par @Auditable ou manuellement via AuditPublisher.
 * Il contient toutes les informations nécessaires pour tracer qui a fait quoi, quand, et avec quel résultat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    /**
     * ID unique de l'événement
     */
    private String eventId;

    /**
     * Action effectuée (CREATED, UPDATED, DELETED, CUSTOM, etc.)
     */
    private String action;

    /**
     * Type d'entité concernée (Customer, User, Account, etc.)
     */
    private String entity;

    /**
     * ID de l'entité concernée
     */
    private String entityId;

    /**
     * Sub (ID Keycloak) de l'utilisateur qui a effectué l'action
     */
    private String actorSub;

    /**
     * Email de l'utilisateur qui a effectué l'action
     */
    private String actorEmail;

    /**
     * Username de l'utilisateur qui a effectué l'action
     */
    private String actorUsername;

    /**
     * Prénom de l'utilisateur qui a effectué l'action
     */
    private String actorFirstName;

    /**
     * Nom de l'utilisateur qui a effectué l'action
     */
    private String actorLastName;

    /**
     * Rôle de l'utilisateur qui a effectué l'action
     */
    private String actorRole;

    /**
     * Statut de l'opération (SUCCESS, FAILED, ERROR)
     */
    private AuditStatus status;

    /**
     * Message d'erreur si status != SUCCESS
     */
    private String errorMessage;

    /**
     * Timestamp de l'événement
     */
    private Instant timestamp;

    /**
     * Nom du microservice source
     */
    private String source;

    /**
     * Métadonnées additionnelles (custom data)
     */
    private Map<String, Object> metadata;
}
