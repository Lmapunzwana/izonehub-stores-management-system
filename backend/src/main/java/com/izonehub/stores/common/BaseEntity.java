package com.izonehub.stores.common;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@MappedSuperclass public abstract class BaseEntity{@Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;@Column(nullable=false,updatable=false) private Instant createdAt;@PrePersist void prePersist(){if(createdAt==null)createdAt=Instant.now();} public UUID getId(){return id;} public Instant getCreatedAt(){return createdAt;}}
