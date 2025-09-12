package se.moln.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=THIS_IS_A_TEST_SECRET_KEY_THAT_IS_DEFINITELY_LONG_ENOUGH_32_BYTES_MIN",
                "jwt.issuer=order-service-test",
                "userservice.url=http://localhost:9998",
                "productservice.url=http://localhost:9999",
                // In-memory H2 to avoid file locks in tests
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                // JPA settings for tests
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.open-in-view=false",
                "spring.sql.init.mode=never"
        }
)
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
