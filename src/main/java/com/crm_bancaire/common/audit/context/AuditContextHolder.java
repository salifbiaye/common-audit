package com.crm_bancaire.common.audit.context;

/**
 * Context holder pour stocker temporairement l'entityId pour l'audit.
 *
 * Utilisé dans les cas où la méthode retourne String/Boolean au lieu de l'entité,
 * mais où on veut quand même capturer l'entity ID pour l'audit.
 *
 * Usage:
 * <pre>
 * {@code
 * @Auditable(action = "CREATED", entity = "User")
 * public String addUser(UserRequest request) {
 *     User savedUser = userRepository.save(user);
 *     AuditContextHolder.setEntityId(savedUser.getId()); // Stocker l'ID
 *     return "utilisateur créé avec succès";
 * }
 * }
 * </pre>
 *
 * Le contexte est automatiquement nettoyé par AuditAspect après utilisation.
 */
public class AuditContextHolder {

    private static final ThreadLocal<String> entityIdHolder = new ThreadLocal<>();

    /**
     * Stocke l'entityId pour la requête courante.
     *
     * @param entityId L'ID de l'entité créée/modifiée
     */
    public static void setEntityId(String entityId) {
        entityIdHolder.set(entityId);
    }

    /**
     * Récupère l'entityId de la requête courante.
     *
     * @return L'entity ID ou null si non défini
     */
    public static String getEntityId() {
        return entityIdHolder.get();
    }

    /**
     * Nettoie l'entityId après utilisation.
     *
     * IMPORTANT: Toujours appeler clear() pour éviter les fuites mémoire.
     * Ceci est fait automatiquement par AuditAspect.
     */
    public static void clear() {
        entityIdHolder.remove();
    }
}
