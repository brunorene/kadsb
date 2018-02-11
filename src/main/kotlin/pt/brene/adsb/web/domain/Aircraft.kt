package pt.brene.adsb.web.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import org.opensky.libadsb.Position

@JsonInclude(NON_NULL)
data class Aircraft(var key: String,
                    @JsonIgnore var airPosition: Position = Position(),
                    var velocity: Double? = null,
                    var heading: Double? = null) {

    val latitude: Double?
        get() = airPosition.latitude
    val longitude: Double?
        get() = airPosition.longitude
    val altitude: Double?
        get() = airPosition.altitude

    var identity: String? = null
        set(value) {
            field = value?.trim()
        }

    override fun toString(): String =
            "$key($identity) => lon = $longitude lat = $latitude alt = $altitude speed = $velocity heading = $heading"
}