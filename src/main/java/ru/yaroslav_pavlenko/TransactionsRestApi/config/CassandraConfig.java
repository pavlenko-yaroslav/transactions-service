package ru.yaroslav_pavlenko.TransactionsRestApi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.KeyspaceOption;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

import java.util.List;

/**
 * Cassandra stores exchange rates only. The keyspace and table are created at startup so
 * the service comes up against an empty cluster from docker-compose; the equivalent CQL
 * script lives in src/main/resources/db/cassandra/schema.cql.
 */
@Configuration
@EnableCassandraRepositories(basePackages = "ru.yaroslav_pavlenko.TransactionsRestApi.repositories.cassandra")
public class CassandraConfig extends AbstractCassandraConfiguration {

    private final CassandraProperties properties;

    @Value("${spring.cassandra.local-datacenter:datacenter1}")
    private String localDatacenter;

    public CassandraConfig(CassandraProperties properties) {
        this.properties = properties;
    }

    @Override
    protected String getKeyspaceName() {
        return properties.getKeyspaceName();
    }

    @Override
    protected String getContactPoints() {
        return String.join(",", properties.getContactPoints());
    }

    @Override
    protected int getPort() {
        return properties.getPort();
    }

    @Override
    protected String getLocalDataCenter() {
        return localDatacenter;
    }

    @Override
    public SchemaAction getSchemaAction() {
        return SchemaAction.CREATE_IF_NOT_EXISTS;
    }

    @Override
    public String[] getEntityBasePackages() {
        return new String[]{"ru.yaroslav_pavlenko.TransactionsRestApi.models.cassandra"};
    }

    @Override
    protected List<CreateKeyspaceSpecification> getKeyspaceCreations() {
        return List.of(CreateKeyspaceSpecification.createKeyspace(getKeyspaceName())
                .ifNotExists()
                .with(KeyspaceOption.DURABLE_WRITES, true)
                .withSimpleReplication());
    }
}
