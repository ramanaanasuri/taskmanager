package karate;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * API automation against a RUNNING deployment (local, GCP, or AWS).
 * Deliberately opt-in so `mvn test` stays green without a live app:
 *
 *   mvn test -Dkarate.api=true \
 *     -Dkarate.env=gcp \
 *     -Dtester.password=$API_TESTER_PASSWORD
 *
 * karate.env picks the baseUrl in karate-config.js (local | gcp | aws).
 */
class KarateApiTest {

    @Karate.Test
    @EnabledIfSystemProperty(named = "karate.api", matches = "true")
    Karate apiSuite() {
        return Karate.run("classpath:karate").relativeTo(getClass());
    }
}
