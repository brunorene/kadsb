package pt.brene.adsb.web

import io.lettuce.core.protocol.LettuceCharsets.ASCII
import mu.KotlinLogging
import org.opensky.libadsb.Position
import org.springframework.data.geo.Point
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation
import org.springframework.data.redis.core.ReactiveGeoOperations
import org.springframework.data.redis.core.ReactiveHashOperations
import org.springframework.data.redis.core.ReactiveRedisTemplate
import pt.brene.adsb.web.domain.Aircraft
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.ByteBuffer
import java.time.Duration

object Fields {
    const val LATITUDE = "latitude"
    const val DISTANCE = "distance"
    const val LONGITUDE = "longitude"
    const val ALTITUDE = "altitude"
    const val VELOCITY = "velocity"
    const val HEADING = "heading"
    const val IDENTITY = "identity"
    const val CENTER = "center"
    const val AIRCRAFT = "aircraft"
    const val FLIGHT_HASH = "flight_hash_"
    const val FLIGHT_GEO = "flight_geo_"
}

private val logger = KotlinLogging.logger {}

fun ReactiveRedisTemplate<String, String>.expireFlight(key: String, timeout: Duration) {
    expire("${Fields.FLIGHT_HASH}$key", timeout).subscribe()
    expire("${Fields.FLIGHT_GEO}$key", timeout).subscribe()
}

fun Position.toGeo(name: String) = GeoLocation(name, Point(latitude, longitude))

fun Pair<ReactiveHashOperations<String, String, String>, ReactiveGeoOperations<String, String>>.save(aircraft: Aircraft, center: Position) {
    if (aircraft.airPosition.latitude != null && aircraft.airPosition.longitude != null) {
        second.add("${Fields.FLIGHT_GEO}${aircraft.key}", aircraft.airPosition.toGeo(Fields.AIRCRAFT)).subscribe()
        second.add("${Fields.FLIGHT_GEO}${aircraft.key}", center.toGeo(Fields.CENTER)).subscribe()
    }
    if (aircraft.airPosition.altitude != null)
        first.put("${Fields.FLIGHT_HASH}${aircraft.key}", Fields.ALTITUDE,
                  aircraft.airPosition.altitude.toString()).subscribe()
    if (aircraft.velocity != null)
        first.put("${Fields.FLIGHT_HASH}${aircraft.key}", Fields.VELOCITY, aircraft.velocity.toString()).subscribe()
    if (aircraft.heading != null)
        first.put("${Fields.FLIGHT_HASH}${aircraft.key}", Fields.HEADING, aircraft.heading.toString()).subscribe()
    if (aircraft.identity != null)
        first.put("${Fields.FLIGHT_HASH}${aircraft.key}", Fields.IDENTITY, aircraft.identity ?: "").subscribe()
}

fun ReactiveRedisTemplate<String, String>.findAircrafts(): Flux<Aircraft> =
        connectionFactory.reactiveConnection.keyCommands()
                .keys(ByteBuffer.wrap("${Fields.FLIGHT_HASH}*".toByteArray(ASCII)))
                .flatMapIterable { it }
                .map { String(it.array(), ASCII).replace(Fields.FLIGHT_HASH, "") }
                .flatMap { Pair(opsForHash<String, String>(), opsForGeo()).pairs(it).toAircraft(it) }

fun Pair<ReactiveHashOperations<String, String, String>, ReactiveGeoOperations<String, String>>.pairs(key: String): Flux<Pair<String, String>> =
        Flux.concat(first.entries("${Fields.FLIGHT_HASH}$key").map { it.toPair() },
                    second.position("${Fields.FLIGHT_GEO}$key", Fields.AIRCRAFT)
                            .flatMapIterable {
                                listOf(Pair(Fields.LATITUDE, it.x.toString()),
                                       Pair(Fields.LONGITUDE, it.y.toString()))
                            })

fun Flux<Pair<String, String>>.toAircraft(key: String): Mono<Aircraft> =
        reduce(Aircraft(key)) { aircraft, (key, value) ->
            with(aircraft) {
                when (key) {
                    Fields.LATITUDE  -> airPosition.latitude = value.toDouble()
                    Fields.LONGITUDE -> airPosition.longitude = value.toDouble()
                    Fields.ALTITUDE  -> airPosition.altitude = value.toDouble()
                    Fields.VELOCITY  -> velocity = value.toDouble()
                    Fields.HEADING   -> heading = value.toDouble()
                    Fields.IDENTITY  -> identity = value
                }
            }
            aircraft
        }.defaultIfEmpty(Aircraft(key))
                .doOnError { logger.error(it) { it.localizedMessage } }

fun Pair<ReactiveHashOperations<String, String, String>, ReactiveGeoOperations<String, String>>.find(key: String): Mono<Aircraft> = pairs(
        key).toAircraft(key)
