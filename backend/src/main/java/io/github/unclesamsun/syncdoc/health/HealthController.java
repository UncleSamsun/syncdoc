package io.github.unclesamsun.syncdoc.health;

import io.github.unclesamsun.syncdoc.common.ApiPaths;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-022 생존, API-023 준비. 관리 actuator를 대신하며 외부에 공개해도 되는 정보만 준다. */
@RestController
@RequestMapping(ApiPaths.BASE + "/health")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** API-022. 프로세스가 살아 있으면 200. 의존성은 확인하지 않는다. */
    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    /** API-023. 필수 의존성(DB)이 준비되면 200, 아니면 503. */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP", "database", "UP"));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "database", "DOWN"));
        }
    }
}
