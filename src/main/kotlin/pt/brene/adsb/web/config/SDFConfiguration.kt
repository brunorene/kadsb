package pt.brene.adsb.web.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "sdf")
class SDFConfiguration {
    lateinit var host: String
}