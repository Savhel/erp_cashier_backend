package com.erp.cashier.repository;

import com.erp.cashier.model.Person;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for people.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Repository
public interface PersonRepository extends ReactiveCrudRepository<Person, String> {
    /**
     * Finds a person by username.
     *
     * @param userName username
     * @return matching person
     */
    Mono<Person> findByUserName(String userName);

    /**
     * Finds a person by email.
     *
     * @param mail email
     * @return matching person
     */
    Mono<Person> findByMail(String mail);

}
