import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.mapdb.DBMaker
import org.mapdb.Serializer.STRING
import org.mapdb.Serializer.ELSA
import org.mapdb.serializer.GroupSerializer
import org.opensky.libadsb.Decoder
import org.opensky.libadsb.Position
import org.opensky.libadsb.PositionDecoder
import org.opensky.libadsb.msgs.*
import org.opensky.libadsb.tools
import java.io.Serializable
import java.net.Socket

private val RECEIVER = Position(-9.185332, 38.651458, 68.71) // Rua Agatão Lança

private val db = DBMaker.fileDB("flights.db")
        .closeOnJvmShutdown()
        .transactionEnable()
        .transactionEnable()
        .make()

data class AircratInfo(var airPosition: Position? = null,
                       var velocity: Double? = null,
                       var heading: Double? = null,
                       var identity: String? = null) : Serializable {
    override fun toString(): String =
            "$identity => lon = ${airPosition?.longitude} lat = ${airPosition?.latitude} alt = ${airPosition?.altitude} speed = $velocity heading = $heading"
}

@Suppress("UNCHECKED_CAST")
val flights = db.hashMap("flights", STRING, ELSA as GroupSerializer<AircratInfo>).createOrOpen()
val decoders = HashMap<String, PositionDecoder>()

fun replyProducer() = produce<Pair<Double, ModeSReply>> {
    Socket("localhost", 30002)
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

fun main(args: Array<String>) = runBlocking {
    replyProducer().consumeEach { (timestamp, reply) ->
        val msgId = tools.toHexString(reply.icao24)
        val flightInfo: AircratInfo = flights[msgId] ?: AircratInfo()
        val decoder = decoders.computeIfAbsent(msgId) { PositionDecoder() }
        if (tools.isZero(reply.parity) || reply.checkParity()) {
            when (reply) {
                is AirbornePositionMsg ->
                    if (reply.hasPosition()) flightInfo.airPosition = decoder.decodePosition(timestamp, RECEIVER, reply)
                is IdentificationMsg -> flightInfo.identity = String(reply.identity)
                is VelocityOverGroundMsg ->
                    if (reply.hasVelocityInfo()) {
                        flightInfo.velocity = reply.velocity
                        flightInfo.heading = reply.heading
                    }
            }
        }
        flights[msgId] = flightInfo
        db.commit()
        flightInfo.airPosition?.let { println("$flightInfo") }
    }
}