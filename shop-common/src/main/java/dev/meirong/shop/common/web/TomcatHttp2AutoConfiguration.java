package dev.meirong.shop.common.web;

import org.apache.coyote.http2.Http2Protocol;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Configures Tomcat's HTTP/2 (h2c) upgrade protocol with safe defaults when
 * {@code server.http2.enabled=true}.
 *
 * <p>Spring Boot's built-in {@code Http2.customize()} adds a stock {@link Http2Protocol}
 * with Tomcat 10.1 defaults ({@code overheadDataThreshold=1024},
 * {@code initialWindowSize=65535}).  Under concurrent JDK-HttpClient h2c connections
 * those defaults trigger a FLOW_CONTROL_ERROR on stream 3 (the first post-upgrade
 * stream sent while stream 1 is still in-flight).  This auto-configuration replaces
 * the stock protocol with a tuned instance:
 *
 * <ul>
 *   <li>{@code overheadDataThreshold=0} — disables the small-frame overhead guard
 *       that penalises the {@code {}} payloads used in list/query endpoints.</li>
 *   <li>{@code initialWindowSize=1_048_576} — allocates a 1 MB per-stream receive
 *       buffer so Tomcat can absorb bursts without prematurely depleting the window
 *       during concurrent-stream initialisation.</li>
 * </ul>
 *
 * <p>Only activates for servlet-stack (Tomcat) services with h2c explicitly enabled.
 * Reactive/Netty services are unaffected.
 */
@AutoConfiguration
@ConditionalOnClass({TomcatServletWebServerFactory.class, Http2Protocol.class})
@ConditionalOnProperty(name = "server.http2.enabled", havingValue = "true")
public class TomcatHttp2AutoConfiguration {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatHttp2ProtocolCustomizer() {
        return new TomcatHttp2ProtocolCustomizer();
    }

    static final class TomcatHttp2ProtocolCustomizer
            implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, Ordered {

        /** Run last so Spring Boot's TomcatWebServerFactoryCustomizer has already set Http2. */
        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public void customize(TomcatServletWebServerFactory factory) {
            var http2 = factory.getHttp2();
            if (http2 != null) {
                // Prevent Spring Boot from later adding its stock Http2Protocol
                // (which carries Tomcat's defaults).  We register our own below.
                http2.setEnabled(false);
            }
            factory.addConnectorCustomizers(connector -> {
                var protocol = new Http2Protocol();
                // Disable the small-frame overhead guard.  Our list/query endpoints
                // send tiny JSON bodies (e.g. "{}"), so the default threshold of 1024
                // would increment the overhead counter aggressively.
                protocol.setOverheadDataThreshold(0);
                // 1 MB initial per-stream receive window (vs default 65535 bytes).
                // Gives Tomcat enough headroom to initialise concurrent streams without
                // the window appearing exhausted before the first DATA frame arrives.
                protocol.setInitialWindowSize(1 << 20);
                connector.addUpgradeProtocol(protocol);
            });
        }
    }
}
