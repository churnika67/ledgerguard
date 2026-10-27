package com.ledgerguard.ledger.graphql;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers the {@code BigDecimal} scalar used throughout the schema for money fields. The
 * built-in GraphQL {@code Float} scalar is IEEE 754 double-precision -- exactly the kind of
 * imprecise representation this whole project exists to avoid for money -- so every amount and
 * balance in the schema is typed {@code BigDecimal} instead, carrying the same precision
 * discipline from the REST API into GraphQL.
 */
@Configuration
public class GraphQlScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.GraphQLBigDecimal);
    }
}
