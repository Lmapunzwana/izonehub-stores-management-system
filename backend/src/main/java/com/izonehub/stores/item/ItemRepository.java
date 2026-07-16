package com.izonehub.stores.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    // Pushes filtering AND pagination down to the database instead of
    // loading every row into the JVM and slicing it there (see git history
    // for the old ItemController.list(), which did exactly that).
    @Query("""
        select i from Item i
        where i.active = :active
          and (:search = ''
               or lower(i.code) like lower(concat('%', :search, '%'))
               or lower(i.name) like lower(concat('%', :search, '%')))
          and (:category is null or i.category = :category)
        """)
    Page<Item> search(@Param("active") boolean active,
                       @Param("search") String search,
                       @Param("category") ItemCategory category,
                       Pageable pageable);

    @Query("""
        select distinct i from Item i
        left join StoreInventory inv on inv.item = i
        left join MaterialRequestLine mrl on mrl.item = i
        left join MaterialRequest mr on mr.id = mrl.materialRequest.id
        where i.active = :active
          and (:search = ''
               or lower(i.code) like lower(concat('%', :search, '%'))
               or lower(i.name) like lower(concat('%', :search, '%')))
          and (:category is null or i.category = :category)
          and (:storeIds is null or inv.store.id in :storeIds or mr.requestingStore.id in :storeIds)
        """)
    Page<Item> searchWithStoreFilter(@Param("active") boolean active,
                       @Param("search") String search,
                       @Param("category") ItemCategory category,
                       @Param("storeIds") java.util.List<UUID> storeIds,
                       Pageable pageable);

    // Uses the existing unique index on code — O(log n) instead of an O(n)
    // full-table scan for every create/import row.
    boolean existsByCodeIgnoreCase(String code);

    // For bulk CSV import: one query for the whole batch instead of one
    // findAll() per row (previously O(rows x table size) in the worst case).
    @Query("select lower(i.code) from Item i")
    List<String> findAllCodesLowercase();
}
