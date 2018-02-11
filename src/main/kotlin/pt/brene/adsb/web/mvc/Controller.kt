package pt.brene.adsb.web.mvc

import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import pt.brene.adsb.web.domain.Aircraft
import pt.brene.adsb.web.findAircrafts
import reactor.core.publisher.Flux

@RestController
class Controller(private val redisTemplate: ReactiveRedisTemplate<String, String>) {

    @GetMapping("/aircraft")
    fun reactive(): Flux<Aircraft> = redisTemplate.findAircrafts()

}