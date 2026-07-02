package com.izonehub.stores.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface GoodsReceivedNoteRepository extends JpaRepository<GoodsReceivedNote, UUID> {
}
