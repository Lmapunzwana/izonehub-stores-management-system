package com.izonehub.stores.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"siteStore", "assignedEmployees"})
    Optional<Project> findById(UUID id);

    Optional<Project> findByCode(String code);

    // Pushes the active/inactive filter + pagination to the database
    // instead of loading every project row and slicing it in Java.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"siteStore", "assignedEmployees"})
    Page<Project> findByActive(boolean active, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"siteStore", "assignedEmployees"})
    Page<Project> findAll(Pageable pageable);

    // Used when closing a project to decide whether its site store can also
    // be closed — only safe if no OTHER active project still uses it.
    boolean existsBySiteStoreIdAndActiveTrueAndIdNot(UUID siteStoreId, UUID excludeProjectId);
}
