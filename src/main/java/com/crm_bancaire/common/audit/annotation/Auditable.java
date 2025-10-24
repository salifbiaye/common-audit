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
 * // Cas 1: Méthode retournant l'entité (extraction automatique via getId())
 * @Auditable(action = "CREATED", entity = "Customer")
 * public Customer createCustomer(Customer customer) {
 *     return customerRepository.save(customer);
 * }
 *
 * // Cas 2: Méthode retournant String/Boolean - utiliser entityIdExpression
 * @Auditable(action = "CREATED", entity = "User", entityIdExpression = "#result")
 * public String addUser(UserRequest request) {
 *     User user = userRepository.save(new User(request));
 *     return user.getId(); // Le String retourné contient l'ID
 * }
 *
 * // Cas 3: L'ID est dans un paramètre de la méthode
 * @Auditable(action = "UPDATED", entity = "User", entityIdExpression = "#id")
 * public Boolean updateUser(String id, UserRequest request) {
 *     userRepository.update(id, request);
 *     return true;
 * }
 *
 * // Cas 4: L'ID est dans le premier paramètre
 * @Auditable(action = "DELETED", entity = "User", entityIdExpression = "#p0")
 * public Boolean deleteUser(String id) {
 *     userRepository.delete(id);
 *     return true;
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
