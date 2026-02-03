package com.erp.cashier.service;

import com.erp.cashier.dto.AdminUserResponse;
import com.erp.cashier.dto.LookupCashierResponse;
import com.erp.cashier.dto.LookupCustomerResponse;
import com.erp.cashier.dto.LookupOrganizationResponse;
import com.erp.cashier.model.Organization;
import com.erp.cashier.repository.CashierProfileRepository;
import com.erp.cashier.repository.CustomerProfileRepository;
import com.erp.cashier.repository.OrganizationRepository;
import com.erp.cashier.repository.PersonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Service for lookup endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class LookupService {
    private static final String SOURCE_LOCAL = "local";

    private final SuperAdminService superAdminService;
    private final PersonRepository personRepository;
    private final CashierProfileRepository cashierProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final R2dbcEntityTemplate entityTemplate;

    /**
     * Creates the lookup service.
     *
     * @param superAdminService super admin service
     * @param personRepository person repository
     * @param cashierProfileRepository cashier profile repository
     * @param customerProfileRepository customer profile repository
     * @param organizationRepository organization repository
     * @param entityTemplate entity template
     */
    public LookupService(
            SuperAdminService superAdminService,
            PersonRepository personRepository,
            CashierProfileRepository cashierProfileRepository,
            CustomerProfileRepository customerProfileRepository,
            OrganizationRepository organizationRepository,
            R2dbcEntityTemplate entityTemplate
    ) {
        this.superAdminService = superAdminService;
        this.personRepository = personRepository;
        this.cashierProfileRepository = cashierProfileRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.organizationRepository = organizationRepository;
        this.entityTemplate = entityTemplate;
    }

    /**
     * Looks up an admin by phone.
     *
     * @param phone phone number
     * @return admin response
     */
    public Mono<AdminUserResponse> lookupAdmin(String phone) {
        return superAdminService.lookupAdminByPhone(phone);
    }

    /**
     * Looks up a cashier by identifier.
     *
     * @param cashierId cashier identifier
     * @return cashier response
     */
    public Mono<LookupCashierResponse> lookupCashier(String cashierId) {
        if (!StringUtils.hasText(cashierId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required"));
        }
        return personRepository.findById(cashierId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cashier not found.")))
                .flatMap(person -> cashierProfileRepository.findByPersonId(person.getId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Cashier profile not found."
                        )))
                        .map(profile -> new LookupCashierResponse(
                                person.getId(),
                                person.getUserName(),
                                person.getUserFirstName(),
                                person.getAccountNumber(),
                                person.getCountry(),
                                person.getMail(),
                                person.getPhone(),
                                person.getPassword(),
                                SOURCE_LOCAL
                        )));
    }

    /**
     * Looks up a customer by phone.
     *
     * @param phone phone number
     * @return customer response
     */
    public Mono<LookupCustomerResponse> lookupCustomer(String phone) {
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone is required"));
        }
        String sql = "SELECT p.user_name, p.user_first_name, p.mail, p.country, p.phone, "
                + "cp.profession "
                + "FROM person p "
                + "JOIN customer_profile cp ON cp.person_id = p.id "
                + "WHERE p.phone IS NOT NULL";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .map((row, meta) -> new LookupCustomerResponse(
                        row.get("phone", String.class),
                        row.get("user_name", String.class),
                        row.get("user_first_name", String.class),
                        row.get("mail", String.class),
                        row.get("country", String.class),
                        row.get("profession", String.class),
                        SOURCE_LOCAL
                ))
                .all()
                .filter(response -> normalizePhone(response.getPhone()).equals(normalized))
                .next()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found.")));
    }

    /**
     * Looks up an organization by code.
     *
     * @param code organization code
     * @return organization response
     */
    public Mono<LookupOrganizationResponse> lookupOrganization(String code) {
        String resolved = trimToNull(code);
        if (!StringUtils.hasText(resolved)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required"));
        }
        Mono<Organization> byId = organizationRepository.findById(resolved);
        Mono<Organization> byName = organizationRepository.findAll()
                .filter(org -> resolved.equalsIgnoreCase(trimToNull(org.getName())))
                .next();

        return byId.switchIfEmpty(byName)
                .map(this::mapOrganization)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found."
                )));
    }

    private LookupOrganizationResponse mapOrganization(Organization organization) {
        return new LookupOrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCountry(),
                organization.getDescription(),
                organization.getTelegramBotToken(),
                organization.getIsActive()
        );
    }

    private String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\D+", "");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
