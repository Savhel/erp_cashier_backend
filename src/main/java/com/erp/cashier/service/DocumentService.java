package com.erp.cashier.service;

import com.erp.cashier.dto.AdminProfileResponse;
import com.erp.cashier.dto.DocumentInfoResponse;
import com.erp.cashier.dto.DocumentResponse;
import com.erp.cashier.dto.PersonSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * Service for document queries.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class DocumentService {
    private final R2dbcEntityTemplate entityTemplate;

    /**
     * Creates the document service.
     *
     * @param entityTemplate entity template
     */
    public DocumentService(R2dbcEntityTemplate entityTemplate) {
        this.entityTemplate = entityTemplate;
    }

    /**
     * Lists documents with uploader information.
     *
     * @return documents
     */
    public Flux<DocumentResponse> listDocuments(String organizationId, String agencyId) {
        String resolvedOrganizationId = trimToNull(organizationId);
        String resolvedAgencyId = trimToNull(agencyId);
        if (!StringUtils.hasText(resolvedOrganizationId) && !StringUtils.hasText(resolvedAgencyId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Scope is required."));
        }

        String sql = "SELECT d.id, d.objet_type, d.objet_id, d.file_name, d.type_mime, d.storage_url, "
                + "d.upload_on, d.is_verified, d.is_deleted, "
                + "p.id AS person_id, p.user_name, p.user_first_name, "
                + "ap.role_type, ap.organization_id, ap.agency_id "
                + "FROM attached_document d "
                + "LEFT JOIN person p ON p.id = d.upload_by "
                + "LEFT JOIN admin_profile ap ON ap.person_id = d.upload_by "
                + "LEFT JOIN cashier_profile cp ON cp.person_id = d.upload_by "
                + "LEFT JOIN agency cpa ON cpa.id = cp.base_agency_id "
                + "WHERE (d.is_deleted = false OR d.is_deleted IS NULL) ";
        StringBuilder scopedSql = new StringBuilder(sql);
        if (StringUtils.hasText(resolvedAgencyId)) {
            scopedSql.append("AND (ap.agency_id = :agencyId OR cp.base_agency_id = :agencyId) ");
        } else {
            scopedSql.append(
                    "AND (ap.organization_id = :organizationId OR cpa.organization_id = :organizationId) "
            );
        }
        scopedSql.append("ORDER BY d.upload_on DESC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(scopedSql.toString());
        if (StringUtils.hasText(resolvedAgencyId)) {
            spec = spec.bind("agencyId", resolvedAgencyId);
        } else {
            spec = spec.bind("organizationId", resolvedOrganizationId);
        }
        return spec
                .map((row, meta) -> {
                    DocumentInfoResponse document = new DocumentInfoResponse(
                            row.get("id", String.class),
                            row.get("objet_type", String.class),
                            row.get("objet_id", String.class),
                            row.get("file_name", String.class),
                            row.get("type_mime", String.class),
                            row.get("storage_url", String.class),
                            row.get("upload_on", java.time.LocalDateTime.class),
                            row.get("is_verified", Boolean.class),
                            row.get("is_deleted", Boolean.class)
                    );
                    PersonSummaryResponse uploader = new PersonSummaryResponse(
                            row.get("person_id", String.class),
                            row.get("user_name", String.class),
                            row.get("user_first_name", String.class)
                    );
                    AdminProfileResponse profile = new AdminProfileResponse();
                    profile.setRoleType(row.get("role_type", String.class));
                    profile.setOrganizationId(row.get("organization_id", String.class));
                    profile.setAgencyId(row.get("agency_id", String.class));
                    return new DocumentResponse(document, uploader, profile);
                })
                .all();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
