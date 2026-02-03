package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Document info payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class DocumentInfoResponse {
    private String id;
    private String objetType;
    private String objetId;
    private String fileName;
    private String typeMime;
    private String storageUrl;
    private LocalDateTime uploadOn;
    private Boolean isVerified;
    private Boolean isDeleted;

    /**
     * Default constructor for JSON serialization.
     */
    public DocumentInfoResponse() {
    }

    /**
     * Creates a document info response.
     *
     * @param id document identifier
     * @param objetType object type
     * @param objetId object identifier
     * @param fileName file name
     * @param typeMime MIME type
     * @param storageUrl storage URL
     * @param uploadOn upload timestamp
     * @param isVerified verified flag
     * @param isDeleted deleted flag
     */
    public DocumentInfoResponse(
            String id,
            String objetType,
            String objetId,
            String fileName,
            String typeMime,
            String storageUrl,
            LocalDateTime uploadOn,
            Boolean isVerified,
            Boolean isDeleted
    ) {
        this.id = id;
        this.objetType = objetType;
        this.objetId = objetId;
        this.fileName = fileName;
        this.typeMime = typeMime;
        this.storageUrl = storageUrl;
        this.uploadOn = uploadOn;
        this.isVerified = isVerified;
        this.isDeleted = isDeleted;
    }
}
