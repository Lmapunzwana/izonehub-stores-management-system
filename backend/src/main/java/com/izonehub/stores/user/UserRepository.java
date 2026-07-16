package com.izonehub.stores.user;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRepository extends JpaRepository<AppUser,UUID>{
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedStore", "roles"})
    List<AppUser> findAll();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedStore", "roles"})
    Optional<AppUser> findById(UUID id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedStore", "roles"})
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    
    List<AppUser> findByAssignedStore(com.izonehub.stores.store.Store store);
}
