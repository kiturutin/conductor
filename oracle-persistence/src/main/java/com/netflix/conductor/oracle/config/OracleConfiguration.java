/*
 * Copyright 2023 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.oracle.config;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.oracle.dao.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OracleProperties.class)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "oracle")
// Import the DataSourceAutoConfiguration when oracle database is selected.
// By default, the datasource configuration is excluded in the main module.
@Import(DataSourceAutoConfiguration.class)
public class OracleConfiguration {

    DataSource dataSource;

    private final OracleProperties properties;

    public OracleConfiguration(DataSource dataSource, OracleProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Bean(initMethod = "migrate")
    @PostConstruct
    public Flyway flywayForPrimaryDb() {
        FluentConfiguration config = Flyway.configure();

        var locations = new ArrayList<String>();
        locations.add("classpath:db/migration_oracle");

        if (properties.getExperimentalQueueNotify()) {
            locations.add("classpath:db/migration_oracle_notify");
        }

        if (properties.isApplyDataMigrations()) {
            locations.add("classpath:db/migration_oracle_data");
        }

        config.locations(locations.toArray(new String[0]));

        return config.configuration(Map.of("flyway.oracleql.transactional.lock", "false"))
                .schemas(properties.getSchema())
                .dataSource(dataSource)
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public OracleMetadataDAO oracleMetadataDAO(
            @Qualifier("oracleRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            OracleProperties properties) {
        return new OracleMetadataDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public OracleExecutionDAO oracleExecutionDAO(
            @Qualifier("oracleRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new OracleExecutionDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public OraclePollDataDAO oraclePollDataDAO(
            @Qualifier("oracleRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            OracleProperties properties) {
        return new OraclePollDataDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    public OracleQueueDAO oracleQueueDAO(
            @Qualifier("oracleRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            OracleProperties properties) {
        return new OracleQueueDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "oracle")
    public OracleIndexDAO oracleIndexDAO(
            @Qualifier("oracleRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            OracleProperties properties) {
        return new OracleIndexDAO(retryTemplate, objectMapper, dataSource, properties);
    }

    @Bean
    @DependsOn({"flywayForPrimaryDb"})
    @ConditionalOnProperty(
            name = "conductor.workflow-execution-lock.type",
            havingValue = "oracle")
    public OracleLockDAO oracleLockDAO(
            @Qualifier("oracleRetryTemplate") RetryTemplate retryTemplate,
            ObjectMapper objectMapper) {
        return new OracleLockDAO(retryTemplate, objectMapper, dataSource);
    }

    @Bean
    public RetryTemplate oracleRetryTemplate(OracleProperties properties) {
        SimpleRetryPolicy retryPolicy = new CustomRetryPolicy();
        retryPolicy.setMaxAttempts(3);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        return retryTemplate;
    }

    public static class CustomRetryPolicy extends SimpleRetryPolicy {

        private static final String ER_LOCK_DEADLOCK = "40P01";
        private static final String ER_SERIALIZATION_FAILURE = "40001";

        @Override
        public boolean canRetry(final RetryContext context) {
            final Optional<Throwable> lastThrowable =
                    Optional.ofNullable(context.getLastThrowable());
            return lastThrowable
                    .map(throwable -> super.canRetry(context) && isDeadLockError(throwable))
                    .orElseGet(() -> super.canRetry(context));
        }

        private boolean isDeadLockError(Throwable throwable) {
            SQLException sqlException = findCauseSQLException(throwable);
            if (sqlException == null) {
                return false;
            }
            return ER_LOCK_DEADLOCK.equals(sqlException.getSQLState())
                    || ER_SERIALIZATION_FAILURE.equals(sqlException.getSQLState());
        }

        private SQLException findCauseSQLException(Throwable throwable) {
            Throwable causeException = throwable;
            while (null != causeException && !(causeException instanceof SQLException)) {
                causeException = causeException.getCause();
            }
            return (SQLException) causeException;
        }
    }
}
