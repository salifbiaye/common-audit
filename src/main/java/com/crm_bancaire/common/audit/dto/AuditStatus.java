package com.crm_bancaire.common.audit.dto;

/**
 * Statut d'un événement d'audit.
 */
public enum AuditStatus {
    /**
     * L'opération s'est terminée avec succès
     */
    SUCCESS,

    /**
     * L'opération a échoué (erreur métier attendue)
     */
    FAILED,

    /**
     * L'opération a rencontré une erreur technique inattendue
     */
    ERROR
}
