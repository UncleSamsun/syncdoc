package io.github.unclesamsun.syncdoc;

import org.springframework.boot.SpringApplication;

public class TestSyncDocApplication {

	public static void main(String[] args) {
		SpringApplication.from(SyncDocApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
