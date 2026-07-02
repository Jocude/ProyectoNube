package com.cloudstorage.api.repository;

import com.cloudstorage.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad {@link User}.
 * Proporciona operaciones CRUD estándar y consultas personalizadas
 * para la gestión de usuarios en el sistema.
 *
 * @author CloudStorage Team
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca un usuario por su dirección de correo electrónico.
     *
     * @param email la dirección de correo electrónico a buscar
     * @return un {@link Optional} que contiene el usuario si existe, o vacío en caso contrario
     */
    Optional<User> findByEmail(String email);

    /**
     * Verifica si ya existe un usuario registrado con el correo electrónico indicado.
     *
     * @param email la dirección de correo electrónico a verificar
     * @return {@code true} si existe un usuario con ese email, {@code false} en caso contrario
     */
    boolean existsByEmail(String email);
}
