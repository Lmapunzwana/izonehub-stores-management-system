package com.izonehub.stores.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

import java.util.Optional;

public interface GoodsReceivedNoteRepository extends JpaRepository<GoodsReceivedNote, UUID> {
    Optional<GoodsReceivedNote> findByExpectedReceiptId(UUID expectedReceiptId);

    // Fetch-joins expectedReceipt and its lines in one round trip so supplier
    // performance aggregation doesn't trigger an N+1 (one query per GRN plus
    // one per line) the way a plain findAll() + lazy access would.
    @Query("""
        select distinct g from GoodsReceivedNote g
        join fetch g.expectedReceipt er
        left join fetch er.lines l
        left join fetch l.item
        order by g.receivedAt desc
        """)
    List<GoodsReceivedNote> findAllWithReceiptAndLines();
}
