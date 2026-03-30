package com.chargelink;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ChargelinkBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
