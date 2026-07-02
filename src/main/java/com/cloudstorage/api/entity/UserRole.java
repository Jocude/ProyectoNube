package com.cloudstorage.api.entity;

/**
 * Enumeración de roles de usuario disponibles en el sistema.
 *
 * <p>Los roles se usan para controlar el acceso a recursos y funcionalidades.</p>
 *
 * @author CloudStorage Team
 */
public enum UserRole {

    /**
     * Usuario estándar con acceso a sus propios archivos y carpetas.
     */
    ROLE_USER,

    /**
     * Administrador del sistema con acceso a métricas y panel B2B.
     */
    ROLE_ADMIN
}
