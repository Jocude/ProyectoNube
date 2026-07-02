package com.cloudstorage.api.repository;

import com.cloudstorage.api.entity.Folder;
import com.cloudstorage.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la gestión de carpetas en el almacenamiento.
 */
@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {

    /**
     * Busca las carpetas raíz (sin padre) del usuario.
     */
    List<Folder> findByOwnerIdAndParentIsNull(UUID ownerId);

    /**
     * Busca las subcarpetas dentro de una carpeta padre específica.
     */
    List<Folder> findByOwnerIdAndParentId(UUID ownerId, UUID parentId);

    /**
     * Busca una carpeta específica por su ID y el propietario.
     */
    Optional<Folder> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * Comprueba si ya existe una carpeta con el mismo nombre en la raíz del usuario.
     */
    boolean existsByNameAndOwnerIdAndParentIsNull(String name, UUID ownerId);

    /**
     * Comprueba si ya existe una carpeta con el mismo nombre dentro de un directorio específico.
     */
    boolean existsByNameAndOwnerIdAndParentId(String name, UUID ownerId, UUID parentId);
}
