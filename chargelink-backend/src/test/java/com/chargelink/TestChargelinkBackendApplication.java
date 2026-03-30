package com.chargelink;

import org.springframework.boot.SpringApplication;

public class TestChargelinkBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ChargelinkBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
