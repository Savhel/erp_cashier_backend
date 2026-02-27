package com.erp.cashier.controller;

import com.erp.cashier.dto.DocumentResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.DocumentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Document endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/admin/documents")
public class DocumentController {
    private final DocumentService documentService;

    /**
     * Creates the document controller.
     *
     * @param documentService document service
     */
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Lists documents for admin users.
     *
     * @return documents
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<DocumentResponse> listDocuments(Authentication authentication) {
        return documentService.listDocuments(
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    private String resolveAgencyId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getAgencyId();
        }
        return null;
    }

    private String resolveOrganizationId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getOrganizationId();
        }
        return null;
    }
}
