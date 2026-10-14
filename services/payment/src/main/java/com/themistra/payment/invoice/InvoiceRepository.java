package com.themistra.payment.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByMerchantAccountUuidOrderByCreatedAtDesc(UUID merchantAccountUuid);

    Optional<Invoice> findByInvoiceUuidAndMerchantAccountUuid(UUID invoiceUuid, UUID merchantAccountUuid);
}
