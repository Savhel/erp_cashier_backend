package com.erp.cashier.dto;

import lombok.Data;

/**
 * Document response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class DocumentResponse {
    private DocumentInfoResponse document;
    private PersonSummaryResponse uploader;
    private AdminProfileResponse adminProfile;

    /**
     * Default constructor for JSON serialization.
     */
    public DocumentResponse() {
    }

    /**
     * Creates a document response.
     *
     * @param document document info
     * @param uploader uploader summary
     * @param adminProfile admin profile
     */
    public DocumentResponse(
            DocumentInfoResponse document,
            PersonSummaryResponse uploader,
            AdminProfileResponse adminProfile
    ) {
        this.document = document;
        this.uploader = uploader;
        this.adminProfile = adminProfile;
    }
}
