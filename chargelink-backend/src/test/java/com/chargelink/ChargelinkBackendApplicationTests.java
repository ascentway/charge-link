package com.chargelink;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration smoke test: verifies the Spring context loads successfully
 * with a real Postgres container (via Testcontainers) in place of the live DB.
 *
 * Requires Docker to be running locally. Tagged as "integration" so it
 * can be excluded from unit-test-only CI pipelines.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ChargelinkBackendApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts without errors.
    }

}

