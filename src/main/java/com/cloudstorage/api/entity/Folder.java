package com.cloudstorage.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Entidad que representa una carpeta o directorio creado por el usuario.
 * Permite una estructura jerárquica (carpetas dentro de carpetas).
 */
@Entity
@Table(name = "folders", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "parent_id", "owner_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Carpeta padre. Si es nula, significa que está en la raíz de la cuenta del usuario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Folder parent;

    /**
     * Usuario propietario de la carpeta.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
