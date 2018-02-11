package pt.brene.adsb.web.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext

@Configuration
@ConfigurationProperties(prefix = "redis")
class RedisConfiguration {
    lateinit var host: String

    @Bean
    fun connFactory() = LettuceConnectionFactory(host, 6379)

    @Bean
    fun reactiveRedisTemplate(factory: ReactiveRedisConnectionFactory) =
            ReactiveRedisTemplate<String, String>(factory, RedisSerializationContext.string())

    @Bean
    fun redisTemplate(factory: RedisConnectionFactory): RedisTemplate<String, String> {
        val rt = RedisTemplate<String, String>()
        rt.connectionFactory = factory
        return rt
    }
}