package com.erp.cashier.repository;

import com.erp.cashier.model.AttachedDocument;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for attached documents.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface AttachedDocumentRepository extends ReactiveCrudRepository<AttachedDocument, String> {
}
