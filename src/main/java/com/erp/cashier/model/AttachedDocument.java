package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Attached document entity mapped to the attached_document table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("attached_document")
@Data
public class AttachedDocument {
    @Id
    private String id;

    @Column("objet_type")
    private String objetType;

    @Column("objet_id")
    private String objetId;

    @Column("file_name")
    private String fileName;

    @Column("type_mime")
    private String typeMime;

    @Column("storage_url")
    private String storageUrl;

    @Column("upload_on")
    private LocalDateTime uploadOn;

    @Column("upload_by")
    private String uploadBy;

    @Column("is_verified")
    private Boolean isVerified;

    @Column("is_deleted")
    private Boolean isDeleted;

    /**
     * Default constructor for framework usage.
     */
    public AttachedDocument() {
    }
}
