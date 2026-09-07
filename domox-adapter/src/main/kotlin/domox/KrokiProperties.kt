package domox

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for the Kroki diagram service.
 *
 * Bound from `domox.kroki.*` in `application.yml` (Spring Boot relaxed binding maps
 * `domox.kroki.host` and `domox.kroki.port` onto [host] and [port]).
 *
 * When no explicit host/port arguments are passed to [HttpRequest.invokePlantUML],
 * the runtime first checks the `kroki.host`/`kroki.port` system properties (set by
 * Testcontainers-based integration tests) and falls back to these defaults.
 *
 * Example `application.yml`:
 * <pre>
 * domox:
 *   kroki:
 *     host: localhost
 *     port: 8001
 * </pre>
 *
 * The default port (8001) matches the host-side mapping in `docker-compose.yml`
 * (`"8001:8000"`); the container itself listens on 8000 internally.
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "domox.kroki")
class KrokiProperties {
    var host: String = "localhost"
    var port: Int = 8001
}
