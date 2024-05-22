package ru.yaroslav_pavlenko.TransactionsRestApi.repositories;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa.TransactionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the schema produced by the Flyway migrations matches the entity mappings:
 * Hibernate runs in validate mode on top of the applied scripts, so any mismatch in
 * columns or types fails the test.
 * <p>
 * The scripts target PostgreSQL, so H2 is started in its compatibility mode.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("The migrated schema passes Hibernate validation")
    void schemaMatchesEntities() {
        assertThat(transactionRepository.findAll()).isEmpty();
    }
}
