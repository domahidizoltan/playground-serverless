package playground.serverless.cron

import io.quarkus.arc.DefaultBean
import io.quarkus.arc.config.ConfigProperties
import io.quarkus.runtime.StartupEvent
import org.apache.camel.CamelContext
import org.apache.camel.RoutesBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.quarkus.kotlin.routes
import java.time.Instant
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.enterprise.event.Observes
import javax.enterprise.inject.Produces
import kotlin.random.Random

@ApplicationScoped
class QuarkusApp {

    fun onStart(@Observes ev: StartupEvent?, context: CamelContext) {
        val randomString = (2 until Random.nextInt(2,6))
                .map { ('A' until 'Z').random() }
                .joinToString(" ")

        context.start()
        context.createFluentProducerTemplate()
                .withBody(randomString)
                .to("direct:send")
                .asyncSend()
    }

}

@ConfigProperties(prefix = "nats")
class NatsConfig {
    var host: String = "localhost"
    var port: String = "4222"
    var topic: String = "test"
}

@Dependent
class CamelConfig {

    @Produces
    @DefaultBean
    fun camelContext(natsRoute: RoutesBuilder) =
            DefaultCamelContext().apply { addRoutes(natsRoute) }

    @Produces
    fun natsRoute(config: NatsConfig) = routes {
        from("direct:send")
                .autoStartup(true)
                .split(body().tokenize(" "))
                    .process { exchange -> exchange.`in`.body = "${exchange.`in`.body} [${Instant.now()}]" }
                    .log("sending ${body()}")
                    .to("nats://${config.host}:${config.port}?topic=RAW(${config.topic})")
                .end()
    }

}
