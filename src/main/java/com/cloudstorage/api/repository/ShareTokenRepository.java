package com.cloudstorage.api.repository;

import com.cloudstorage.api.entity.ShareToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad {@link ShareToken}.
 *
 * @author CloudStorage Team
 */
@Repository
public interface ShareTokenRepository extends JpaRepository<ShareToken, UUID> {

    /**
     * Busca un token de compartición por su valor de token.
     *
     * @param token el valor del token
     * @return el token de compartición si existe
     */
    Optional<ShareToken> findByToken(String token);

    /**
     * Lista todos los tokens de compartición de un usuario específico.
     *
     * @param ownerId el UUID del propietario
     * @return lista de tokens del propietario
     */
    List<ShareToken> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /**
     * Lista los tokens de compartición asociados a un archivo específico.
     *
     * @param fileId el UUID del archivo
     * @param ownerId el UUID del propietario (para verificar autorización)
     * @return lista de tokens del archivo
     */
    List<ShareToken> findByFileIdAndOwnerId(UUID fileId, UUID ownerId);

    /**
     * Borra todos los enlaces de compartición de un archivo (antes de borrarlo definitivamente).
     *
     * @param fileId el UUID del archivo
     */
    @Modifying
    @Query("DELETE FROM ShareToken s WHERE s.file.id = :fileId")
    void deleteByFileId(UUID fileId);
}
