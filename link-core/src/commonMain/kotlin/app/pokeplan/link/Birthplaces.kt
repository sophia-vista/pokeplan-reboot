package app.pokeplan.link

/**
 * Places to pick a birthplace from, with latitude, longitude and the **standard** UTC offset in minutes (daylight saving is the user's to add: the
 * offset field can be changed). Offline, so no search; a place not here is typed as coordinates.
 */
object Birthplaces {
    val all: List<Place> = listOf(
        Place("Manila", 14.5995, 120.9842, 480), Place("Quezon City", 14.6760, 121.0437, 480), Place("Makati", 14.5547, 121.0244, 480),
        Place("Cebu City", 10.3157, 123.8854, 480), Place("Davao City", 7.1907, 125.4553, 480), Place("Baguio", 16.4023, 120.5960, 480),
        Place("Iloilo City", 10.7202, 122.5621, 480), Place("Cagayan de Oro", 8.4542, 124.6319, 480), Place("Zamboanga City", 6.9214, 122.0790, 480),
        Place("Bacolod", 10.6713, 122.9511, 480), Place("Tacloban", 11.2543, 124.9600, 480), Place("Angeles City", 15.1450, 120.5887, 480),
        Place("Naga", 13.6218, 123.1948, 480), Place("General Santos", 6.1164, 125.1716, 480), Place("Laoag", 18.1978, 120.5936, 480),
        Place("Puerto Princesa", 9.7392, 118.7353, 480), Place("Dagupan", 16.0433, 120.3333, 480), Place("Lucena", 13.9314, 121.6171, 480),
        Place("Batangas City", 13.7565, 121.0583, 480), Place("Olongapo", 14.8292, 120.2824, 480),
        Place("Singapore", 1.3521, 103.8198, 480), Place("Hong Kong", 22.3193, 114.1694, 480), Place("Beijing", 39.9042, 116.4074, 480),
        Place("Shanghai", 31.2304, 121.4737, 480), Place("Taipei", 25.0330, 121.5654, 480), Place("Tokyo", 35.6762, 139.6503, 540),
        Place("Seoul", 37.5665, 126.9780, 540), Place("Bangkok", 13.7563, 100.5018, 420), Place("Jakarta", -6.2088, 106.8456, 420),
        Place("Kuala Lumpur", 3.1390, 101.6869, 480), Place("Hanoi", 21.0285, 105.8542, 420), Place("Mumbai", 19.0760, 72.8777, 330),
        Place("Delhi", 28.6139, 77.2090, 330), Place("Dubai", 25.2048, 55.2708, 240), Place("Riyadh", 24.7136, 46.6753, 180),
        Place("Istanbul", 41.0082, 28.9784, 180), Place("Cairo", 30.0444, 31.2357, 120), Place("Lagos", 6.5244, 3.3792, 60),
        Place("Nairobi", -1.2921, 36.8219, 180), Place("Johannesburg", -26.2041, 28.0473, 120), Place("London", 51.5074, -0.1278, 0),
        Place("Paris", 48.8566, 2.3522, 60), Place("Berlin", 52.5200, 13.4050, 60), Place("Madrid", 40.4168, -3.7038, 60),
        Place("Rome", 41.9028, 12.4964, 60), Place("Moscow", 55.7558, 37.6173, 180), Place("New York", 40.7128, -74.0060, -300),
        Place("Chicago", 41.8781, -87.6298, -360), Place("Los Angeles", 34.0522, -118.2437, -480), Place("San Francisco", 37.7749, -122.4194, -480),
        Place("Honolulu", 21.3069, -157.8583, -600), Place("Toronto", 43.6532, -79.3832, -300), Place("Vancouver", 49.2827, -123.1207, -480),
        Place("Mexico City", 19.4326, -99.1332, -360), Place("São Paulo", -23.5505, -46.6333, -180), Place("Buenos Aires", -34.6037, -58.3816, -180),
        Place("Guam", 13.4443, 144.7937, 600), Place("Sydney", -33.8688, 151.2093, 600), Place("Melbourne", -37.8136, 144.9631, 600),
        Place("Auckland", -36.8485, 174.7633, 720),
    )

    fun named(name: String): Place? = all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** Latitude and longitude typed as decimals ("14.6", "-121.04"), or null if they are not within range. */
    fun coordinates(lat: String, lon: String): Pair<Double, Double>? {
        val la = lat.trim().toDoubleOrNull() ?: return null
        val lo = lon.trim().toDoubleOrNull() ?: return null
        return if (la in -85.0..85.0 && lo in -180.0..180.0) la to lo else null
    }
}
