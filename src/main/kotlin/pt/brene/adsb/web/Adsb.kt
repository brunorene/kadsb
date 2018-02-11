package pt.brene.adsb.web

import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.launch
import mu.KLogging
import org.opensky.libadsb.Decoder
import org.opensky.libadsb.Position
import org.opensky.libadsb.PositionDecoder
import org.opensky.libadsb.msgs.AirbornePositionMsg
import org.opensky.libadsb.msgs.IdentificationMsg
import org.opensky.libadsb.msgs.ModeSReply
import org.opensky.libadsb.msgs.VelocityOverGroundMsg
import org.opensky.libadsb.tools
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import pt.brene.adsb.web.config.SDFConfiguration
import java.net.Socket
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.timerTask

@Component
class Adsb(private val redisTemplate: ReactiveRedisTemplate<String, String>,
           private val sdfConf: SDFConfiguration) {

    private val receiver = Position(-9.1961215, 38.6512998, 68.71)
    private val decoders = HashMap<String, PositionDecoder>()
    private val ops = Pair(redisTemplate.opsForHash<String, String>(), redisTemplate.opsForGeo())

    companion object : KLogging()

    private fun replyProducer() = produce<Pair<Double, ModeSReply>> {
        Socket(sdfConf.host, 30002)
                .getInputStream()
                .bufferedReader(Charsets.US_ASCII)
                .useLines {
                    it.forEach {
                        val matcher = Regex("\\*([^;]+);").toPattern().matcher(it)
                        matcher.find()
                        send(Pair(System.currentTimeMillis() / 1000.0, Decoder.genericDecoder(matcher.group(1))))
                    }
                }
    }

    @EventListener(ContextRefreshedEvent::class)
    fun processMessages() = launch {
        replyProducer().consumeEach { (timestamp, reply) ->
            redisTemplate.findAircrafts()
            val msgId = tools.toHexString(reply.icao24)
            ops.find(msgId)
                    .subscribe {
                        val decoder = decoders.computeIfAbsent(msgId) {
                            Timer("remove decoder $msgId ${LocalDateTime.now()}").schedule(
                                    timerTask { decoders.remove(msgId) }, 600_000L)
                            PositionDecoder()
                        }
                        var saveIt = true
                        if (tools.isZero(reply.parity) || reply.checkParity()) {
                            when (reply) {
                                is AirbornePositionMsg -> {
                                    val currentPos: Position? = decoder.decodePosition(timestamp, receiver, reply)
                                    if (currentPos != null)
                                        it.airPosition = currentPos
                                    it.airPosition.altitude = reply.altitude
                                }
                                is IdentificationMsg -> it.identity = String(reply.identity)
                                is VelocityOverGroundMsg ->
                                    if (reply.hasVelocityInfo()) {
                                        it.velocity = reply.velocity
                                        it.heading = reply.heading
                                    }
                                else -> saveIt = false
                            }
                        } else saveIt = false
                        redisTemplate.expireFlight(msgId, Duration.ofMinutes(10))
                        if (saveIt) {
                            logger.debug { it }
                            ops.save(it, receiver)
                        }
                    }
        }
    }
}