package com.erp.cashier.service;

import com.erp.cashier.dto.UpdateUserProfileRequest;
import com.erp.cashier.dto.UserProfileResponse;
import com.erp.cashier.model.Organization;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.OrganizationRepository;
import com.erp.cashier.repository.PersonRepository;
import com.erp.cashier.security.JwtPayload;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Service for current user profile operations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class UserProfileService {
    private final PersonRepository personRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * Creates the user profile service.
     *
     * @param personRepository person repository
     * @param organizationRepository organization repository
     */
    public UserProfileService(
            PersonRepository personRepository,
            OrganizationRepository organizationRepository
    ) {
        this.personRepository = personRepository;
        this.organizationRepository = organizationRepository;
    }

    /**
     * Returns the current user's profile.
     *
     * @param payload authentication payload
     * @return user profile
     */
    public Mono<UserProfileResponse> getProfile(JwtPayload payload) {
        String userId = resolveUserId(payload);
        String organizationId = resolveOrganizationId(payload);

        Mono<Person> personMono = personRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.")));
        Mono<Optional<Organization>> organizationMono = fetchOrganization(organizationId);

        return Mono.zip(personMono, organizationMono)
                .map(tuple -> {
                    Person person = tuple.getT1();
                    Optional<Organization> organization = tuple.getT2();
                    String telegramBotToken = organization.map(Organization::getTelegramBotToken).orElse(null);
                    return new UserProfileResponse(
                            person.getId(),
                            person.getUserName(),
                            person.getUserFirstName(),
                            person.getTelegramChatId(),
                            telegramBotToken
                    );
                });
    }

    /**
     * Updates the current user's profile.
     *
     * @param payload authentication payload
     * @param request update request
     * @return updated profile
     */
    public Mono<UserProfileResponse> updateProfile(JwtPayload payload, UpdateUserProfileRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile payload is required."));
        }

        String userId = resolveUserId(payload);
        String organizationId = resolveOrganizationId(payload);
        String telegramChatId = trimToNull(request.getTelegramChatId());
        String telegramBotToken = trimToNull(request.getTelegramBotToken());

        Mono<Person> personUpdate = personRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.")))
                .flatMap(person -> {
                    person.setTelegramChatId(telegramChatId);
                    return personRepository.save(person);
                });

        Mono<Optional<Organization>> organizationUpdate = updateOrganizationToken(organizationId, telegramBotToken);

        return Mono.zip(personUpdate, organizationUpdate)
                .map(tuple -> {
                    Person person = tuple.getT1();
                    Optional<Organization> organization = tuple.getT2();
                    String resolvedToken = organization.map(Organization::getTelegramBotToken).orElse(null);
                    return new UserProfileResponse(
                            person.getId(),
                            person.getUserName(),
                            person.getUserFirstName(),
                            person.getTelegramChatId(),
                            resolvedToken
                    );
                });
    }

    private Mono<Optional<Organization>> fetchOrganization(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.just(Optional.empty());
        }
        return organizationRepository.findById(organizationId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Optional<Organization>> updateOrganizationToken(String organizationId, String telegramBotToken) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.just(Optional.empty());
        }
        return organizationRepository.findById(organizationId)
                .flatMap(organization -> {
                    organization.setTelegramBotToken(telegramBotToken);
                    return organizationRepository.save(organization);
                })
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private String resolveUserId(JwtPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.getUserId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return payload.getUserId();
    }

    private String resolveOrganizationId(JwtPayload payload) {
        if (payload == null) {
            return null;
        }
        return trimToNull(payload.getOrganizationId());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
