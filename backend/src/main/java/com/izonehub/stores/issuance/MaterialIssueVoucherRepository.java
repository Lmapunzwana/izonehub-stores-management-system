package com.izonehub.stores.issuance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MaterialIssueVoucherRepository extends JpaRepository<MaterialIssueVoucher, UUID> {
}
