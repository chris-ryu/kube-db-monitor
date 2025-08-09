package io.kubedb.monitor.agent;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConfigTest {

    @Test
    void shouldUseDefaultsWhenNoArgsProvided() {
        // When
        AgentConfig config = AgentConfig.fromArgs(null);

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getSamplingRate()).isEqualTo(1.0);
        assertThat(config.getSupportedDatabases()).contains("mysql", "postgresql", "h2");
        assertThat(config.isMaskSqlParams()).isTrue();
        assertThat(config.getSlowQueryThresholdMs()).isEqualTo(1000L);
    }

    @Test
    void shouldUseDefaultsWhenEmptyArgsProvided() {
        // When
        AgentConfig config = AgentConfig.fromArgs("");

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getSamplingRate()).isEqualTo(1.0);
    }

    @Test
    void shouldParseEnabledFlag() {
        // When
        AgentConfig config = AgentConfig.fromArgs("enabled=false");

        // Then
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    void shouldParseSamplingRate() {
        // When
        AgentConfig config = AgentConfig.fromArgs("sampling-rate=0.5");

        // Then
        assertThat(config.getSamplingRate()).isEqualTo(0.5);
    }

    @Test
    void shouldIgnoreInvalidSamplingRate() {
        // When
        AgentConfig config = AgentConfig.fromArgs("sampling-rate=1.5");

        // Then
        assertThat(config.getSamplingRate()).isEqualTo(1.0); // Should use default
    }

    @Test
    void shouldIgnoreInvalidSamplingRateFormat() {
        // When
        AgentConfig config = AgentConfig.fromArgs("sampling-rate=invalid");

        // Then
        assertThat(config.getSamplingRate()).isEqualTo(1.0); // Should use default
    }

    @Test
    void shouldParseDatabaseTypes() {
        // When
        AgentConfig config = AgentConfig.fromArgs("db-types=mysql,oracle");

        // Then
        assertThat(config.getSupportedDatabases()).containsExactly("mysql", "oracle");
    }

    @Test
    void shouldParseMaskSqlParams() {
        // When
        AgentConfig config = AgentConfig.fromArgs("mask-sql-params=false");

        // Then
        assertThat(config.isMaskSqlParams()).isFalse();
    }

    @Test
    void shouldParseSlowQueryThreshold() {
        // When
        AgentConfig config = AgentConfig.fromArgs("slow-query-threshold=500");

        // Then
        assertThat(config.getSlowQueryThresholdMs()).isEqualTo(500L);
    }

    @Test
    void shouldIgnoreInvalidSlowQueryThreshold() {
        // When
        AgentConfig config = AgentConfig.fromArgs("slow-query-threshold=invalid");

        // Then
        assertThat(config.getSlowQueryThresholdMs()).isEqualTo(1000L); // Should use default
    }

    @Test
    void shouldParseMultipleArgs() {
        // When
        AgentConfig config = AgentConfig.fromArgs("enabled=true,sampling-rate=0.1,db-types=postgresql,mask-sql-params=false");

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getSamplingRate()).isEqualTo(0.1);
        assertThat(config.getSupportedDatabases()).containsExactly("postgresql");
        assertThat(config.isMaskSqlParams()).isFalse();
    }

    @Test
    void shouldIgnoreArgsWithoutEquals() {
        // When
        AgentConfig config = AgentConfig.fromArgs("enabled,sampling-rate=0.5");

        // Then
        assertThat(config.isEnabled()).isTrue(); // Should use default
        assertThat(config.getSamplingRate()).isEqualTo(0.5);
    }

    @Test
    void shouldHandleSpacesInArgs() {
        // When
        AgentConfig config = AgentConfig.fromArgs(" enabled = true , sampling-rate = 0.8 ");

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getSamplingRate()).isEqualTo(0.8);
    }

    @Test
    void shouldProvideStringRepresentation() {
        // Given
        AgentConfig config = AgentConfig.fromArgs("enabled=true,sampling-rate=0.5");

        // When
        String result = config.toString();

        // Then
        assertThat(result).contains("enabled=true");
        assertThat(result).contains("samplingRate=0.5");
    }

    @Test
    void shouldBuildUsingBuilder() {
        // When
        AgentConfig config = new AgentConfig.Builder()
                .enabled(false)
                .samplingRate(0.3)
                .supportedDatabases(Arrays.asList("mysql"))
                .maskSqlParams(false)
                .slowQueryThresholdMs(2000L)
                .build();

        // Then
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getSamplingRate()).isEqualTo(0.3);
        assertThat(config.getSupportedDatabases()).containsExactly("mysql");
        assertThat(config.isMaskSqlParams()).isFalse();
        assertThat(config.getSlowQueryThresholdMs()).isEqualTo(2000L);
    }
}