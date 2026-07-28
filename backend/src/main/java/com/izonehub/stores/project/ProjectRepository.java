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

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p FROM Project p LEFT JOIN p.siteStore s LEFT JOIN p.assignedEmployees emp WHERE p.active = true AND (s.manager.id = :userId OR s.id IN (SELECT u.assignedStore.id FROM com.izonehub.stores.user.AppUser u WHERE u.id = :userId AND u.assignedStore IS NOT NULL) OR emp.id = :userId)")
    java.util.List<Project> findProjectsForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
