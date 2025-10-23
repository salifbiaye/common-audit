package com.crm_bancaire.common.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour marquer une méthode comme auditable.
 *
 * L'aspect AuditAspect interceptera automatiquement les méthodes annotées
 * et publiera un événement d'audit avec les informations de l'utilisateur courant.
 *
 * Usage:
 * <pre>
 * {@code
 * @Auditable(action = "CREATED", entity = "Customer")
 * public Customer createCustomer(Customer customer) {
 *     return customerRepository.save(customer);
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Action effectuée (CREATED, UPDATED, DELETED, etc.)
     */
    String action();

    /**
     * Type d'entité concernée (Customer, User, Account, etc.)
     */
    String entity();

    /**
     * Expression SpEL pour extraire l'ID de l'entité depuis le résultat de la méthode.
     * Par défaut: "getId()" - appelle getId() sur le retour de la méthode.
     *
     * Exemples:
     * - "getId()" - appelle getId() sur l'objet retourné
     * - "#result.id" - accède au champ id
     * - "#p0.id" - utilise l'ID du premier paramètre
     */
    String entityIdExpression() default "getId()";

    /**
     * Métadonnées additionnelles (optionnel).
     * Peut contenir du JSON ou des paires clé-valeur.
     */
    String metadata() default "";
}
