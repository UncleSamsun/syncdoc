package io.github.unclesamsun.syncdoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SyncDocApplicationTests {

	@Test
	void contextLoads() {
	}

}
