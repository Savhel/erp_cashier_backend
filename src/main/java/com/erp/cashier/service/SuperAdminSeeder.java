package com.erp.cashier.service;

import com.erp.cashier.model.AdminProfile;
import com.erp.cashier.model.Person;
import com.erp.cashier.config.R2dbcMigrationRunner;
import com.erp.cashier.repository.AdminProfileRepository;
import com.erp.cashier.repository.PersonRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Seeds a super admin account when enabled via configuration.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Component
public class SuperAdminSeeder implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SuperAdminSeeder.class);
    private static final String ROLE_SUPERADMIN = "superadmin";

    private final PersonRepository personRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final PasswordService passwordService;
    private final R2dbcMigrationRunner migrationRunner;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String firstName;

    /**
     * Creates the super admin seeder.
     *
     * @param personRepository person repository
     * @param adminProfileRepository admin profile repository
     * @param entityTemplate entity template for inserts
     * @param passwordService password service
     * @param migrationRunner migration runner
     * @param enabled whether seeding is enabled
     * @param username super admin username
     * @param password super admin password
     * @param firstName super admin first name
     */
    public SuperAdminSeeder(
            PersonRepository personRepository,
            AdminProfileRepository adminProfileRepository,
            R2dbcEntityTemplate entityTemplate,
            PasswordService passwordService,
            R2dbcMigrationRunner migrationRunner,
            @Value("${app.seed.superadmin.enabled:false}") boolean enabled,
            @Value("${app.seed.superadmin.username:}") String username,
            @Value("${app.seed.superadmin.password:}") String password,
            @Value("${app.seed.superadmin.first-name:Super Admin}") String firstName
    ) {
        this.personRepository = personRepository;
        this.adminProfileRepository = adminProfileRepository;
        this.entityTemplate = entityTemplate;
        this.passwordService = passwordService;
        this.migrationRunner = migrationRunner;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.firstName = firstName;
    }

    /**
     * Executes the seed process.
     *
     * @param event application ready event
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            LOGGER.warn("Super admin seed skipped: missing username or password.");
            return;
        }
        migrationRunner.migrate()
                .then(seedSuperAdmin())
                .doOnError(ex -> LOGGER.error("Failed to seed super admin.", ex))
                .subscribe();
    }

    private Mono<Void> seedSuperAdmin() {
        return personRepository.findByUserName(username)
                .flatMap(this::refreshSuperAdmin)
                .switchIfEmpty(createSuperAdmin())
                .then();
    }

    private Mono<Person> createSuperAdmin() {
        Person person = new Person();
        person.setId(UUID.randomUUID().toString());
        person.setUserName(username.trim());
        person.setUserFirstName(firstName.trim());
        person.setMail(username.trim());
        person.setActif(true);
        person.setPassword(passwordService.hashPassword(password));
        return entityTemplate.insert(Person.class)
                .using(person)
                .flatMap(this::ensureAdminProfile)
                .thenReturn(person);
    }

    private Mono<Person> refreshSuperAdmin(Person person) {
        boolean updated = false;
        String trimmedUsername = username != null ? username.trim() : "";
        String trimmedFirstName = firstName != null ? firstName.trim() : "";
        if (StringUtils.hasText(trimmedUsername) && !trimmedUsername.equals(person.getUserName())) {
            person.setUserName(trimmedUsername);
            updated = true;
        }
        if (StringUtils.hasText(trimmedFirstName) && !trimmedFirstName.equals(person.getUserFirstName())) {
            person.setUserFirstName(trimmedFirstName);
            updated = true;
        }
        if (StringUtils.hasText(trimmedUsername) && !trimmedUsername.equals(person.getMail())) {
            person.setMail(trimmedUsername);
            updated = true;
        }
        if (StringUtils.hasText(password)) {
            person.setPassword(passwordService.hashPassword(password));
            updated = true;
        }
        if (updated) {
            return personRepository.save(person)
                    .flatMap(this::ensureAdminProfile);
        }
        return ensureAdminProfile(person).thenReturn(person);
    }

    private Mono<Person> ensureAdminProfile(Person person) {
        return adminProfileRepository.findByPersonId(person.getId())
                .switchIfEmpty(createAdminProfile(person.getId()))
                .thenReturn(person);
    }

    private Mono<AdminProfile> createAdminProfile(String personId) {
        AdminProfile adminProfile = new AdminProfile();
        adminProfile.setId(UUID.randomUUID().toString());
        adminProfile.setPersonId(personId);
        adminProfile.setRoleType(ROLE_SUPERADMIN);
        return entityTemplate.insert(AdminProfile.class)
                .using(adminProfile);
    }
}
