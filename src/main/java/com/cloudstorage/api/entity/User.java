package com.cloudstorage.api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Entidad que representa un usuario del sistema de almacenamiento en la nube. Implementa {@link
 * UserDetails} para integrarse con Spring Security.
 *
 * @author CloudStorage Team
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    /** Identificador único del usuario generado automáticamente como UUID. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nombre completo del usuario. */
    @Column(nullable = false)
    private String name;

    /** Correo electrónico del usuario. Debe ser único en el sistema. */
    @Column(nullable = false, unique = true)
    private String email;

    /** Contraseña del usuario almacenada como hash BCrypt. */
    @Column(nullable = false)
    private String password;

    /** Rol del usuario en el sistema (USER o ADMIN). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.ROLE_USER;

    /**
     * Número de intentos de login fallidos consecutivos. Se resetea a 0 cuando el login es exitoso.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /**
     * Fecha y hora hasta la que la cuenta está bloqueada. Si es null o en el pasado, la cuenta no
     * está bloqueada.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Indica si la cuenta del usuario está habilitada. */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Fecha y hora de creación del registro. Se establece automáticamente al persistir la entidad y
     * no puede ser actualizada.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Lista de archivos que pertenecen al usuario. La relación es bidireccional con eliminación en
     * cascada.
     */
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<FileMetadata> files = new ArrayList<>();

    /** Callback de JPA que establece la fecha de creación antes de persistir la entidad. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Retorna las autoridades (roles) del usuario basadas en su rol asignado.
     *
     * @return colección con el rol del usuario como autoridad
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    /**
     * Retorna el nombre de usuario utilizado para la autenticación. En este sistema se utiliza el
     * correo electrónico como identificador de acceso.
     *
     * @return el correo electrónico del usuario
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Indica si la cuenta del usuario ha expirado.
     *
     * @return {@code true} siempre, la cuenta nunca expira
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indica si la cuenta del usuario está bloqueada. La cuenta se bloquea temporalmente tras
     * múltiples intentos fallidos de login.
     *
     * @return {@code true} si la cuenta NO está bloqueada (lockedUntil es null o pasado)
     */
    @Override
    public boolean isAccountNonLocked() {
        if (lockedUntil == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(lockedUntil);
    }

    /**
     * Indica si las credenciales del usuario han expirado.
     *
     * @return {@code true} siempre, las credenciales nunca expiran
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indica si el usuario está habilitado.
     *
     * @return el valor del campo {@code enabled}
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
