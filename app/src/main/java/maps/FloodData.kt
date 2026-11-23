package com.majialert.kenya.maps

// ---------- Shared models (agree on these with Member 3; swap for theirs once ready) ----------
enum class Severity { LOW, MODERATE, HIGH, SEVERE }

data class FloodAlert(
    val id: String,
    val countyId: Int,
    val severity: Severity,
    val message: String,
    val issuedAt: Long,   // epoch millis
    val expiresAt: Long   // epoch millis
) {
    val isActive get() = expiresAt > System.currentTimeMillis()
}

data class County(val id: Int, val name: String, val lat: Double, val lng: Double)

data class FloodSite(
    val name: String,
    val county: String,
    val lat: Double,
    val lng: Double,
    val history: String
)

// ---------- 15 flood-prone areas ----------
// Coordinates are approximate (town/area centre). Cite NDMA, KMD, Kenya Red Cross or news reports
// in your report/presentation for the "documented" claim.
val floodProneSites = listOf(
    FloodSite("Budalangi", "Busia", 0.1167, 34.0000, "Lower Nzoia basin; recurrent riverine flooding and displacement."),
    FloodSite("Kano Plains / Nyando", "Kisumu", -0.1700, 34.9000, "Nyando River bursts banks almost every long-rains season."),
    FloodSite("Kisumu City (Nyalenda)", "Kisumu", -0.1022, 34.7617, "Lake Victoria backflow and poor drainage."),
    FloodSite("Homa Bay Town", "Homa Bay", -0.5273, 34.4571, "Lake-level rise and river overflow affect lakeside settlements."),
    FloodSite("Mathare", "Nairobi", -1.2600, 36.8600, "Informal settlement along Mathare River; flash floods."),
    FloodSite("Kibera", "Nairobi", -1.3133, 36.7878, "Dense settlement; blocked drainage and Ngong River overflow."),
    FloodSite("Mai Mahiu", "Nakuru", -0.9917, 36.5833, "Deadly flash flood and mudflow in April 2024."),
    FloodSite("Marigat / Lake Baringo", "Baringo", 0.4700, 35.9833, "Rising lake levels submerged homes and schools."),
    FloodSite("Narok Town", "Narok", -1.0783, 35.8600, "Flash floods after heavy rain in Mara catchment."),
    FloodSite("Garissa Town", "Garissa", -0.4569, 39.6461, "Tana River flooding during El Nino and long rains."),
    FloodSite("Hola / Tana Delta", "Tana River", -1.5000, 40.0300, "Tana River overflow displaces thousands regularly."),
    FloodSite("Mandera Town", "Mandera", 3.9373, 41.8569, "Daua River and flash floods in the north-east."),
    FloodSite("Lodwar", "Turkana", 3.1191, 35.5973, "Turkwel River flash floods in an otherwise arid area."),
    FloodSite("Mombasa Island (Mikindani)", "Mombasa", -4.0435, 39.6682, "Urban flooding from poor drainage in heavy coastal rain."),
    FloodSite("Voi", "Taita Taveta", -3.3961, 38.5561, "Seasonal river flooding and washed-out roads.")
)

// ---------- 47 counties (approx. county HQ coordinates) ----------
val counties = listOf(
    County(1, "Mombasa", -4.0435, 39.6682), County(2, "Kwale", -4.1737, 39.4521),
    County(3, "Kilifi", -3.6305, 39.8499), County(4, "Tana River", -1.5000, 40.0300),
    County(5, "Lamu", -2.2717, 40.9020), County(6, "Taita Taveta", -3.5050, 38.3770),
    County(7, "Garissa", -0.4569, 39.6461), County(8, "Wajir", 1.7471, 40.0573),
    County(9, "Mandera", 3.9373, 41.8569), County(10, "Marsabit", 2.3284, 37.9899),
    County(11, "Isiolo", 0.3546, 37.5822), County(12, "Meru", 0.0480, 37.6559),
    County(13, "Tharaka-Nithi", -0.2950, 37.9690), County(14, "Embu", -0.5389, 37.4596),
    County(15, "Kitui", -1.3667, 38.0106), County(16, "Machakos", -1.5177, 37.2634),
    County(17, "Makueni", -1.7833, 37.6333), County(18, "Nyandarua", -0.2667, 36.3833),
    County(19, "Nyeri", -0.4201, 36.9476), County(20, "Kirinyaga", -0.4989, 37.2803),
    County(21, "Murang'a", -0.7210, 37.1526), County(22, "Kiambu", -1.1714, 36.8356),
    County(23, "Turkana", 3.1191, 35.5973), County(24, "West Pokot", 1.2389, 35.1119),
    County(25, "Samburu", 1.0966, 36.6986), County(26, "Trans Nzoia", 1.0191, 35.0020),
    County(27, "Uasin Gishu", 0.5143, 35.2698), County(28, "Elgeyo-Marakwet", 0.6703, 35.5081),
    County(29, "Nandi", 0.2046, 35.1050), County(30, "Baringo", 0.4919, 35.7430),
    County(31, "Laikipia", 0.2667, 36.5333), County(32, "Nakuru", -0.3031, 36.0800),
    County(33, "Narok", -1.0783, 35.8600), County(34, "Kajiado", -1.8524, 36.7768),
    County(35, "Kericho", -0.3689, 35.2863), County(36, "Bomet", -0.7813, 35.3416),
    County(37, "Kakamega", 0.2827, 34.7519), County(38, "Vihiga", 0.0833, 34.7167),
    County(39, "Bungoma", 0.5635, 34.5606), County(40, "Busia", 0.4608, 34.1115),
    County(41, "Siaya", 0.0607, 34.2881), County(42, "Kisumu", -0.1022, 34.7617),
    County(43, "Homa Bay", -0.5273, 34.4571), County(44, "Migori", -1.0634, 34.4731),
    County(45, "Kisii", -0.6817, 34.7667), County(46, "Nyamira", -0.5633, 34.9358),
    County(47, "Nairobi", -1.2921, 36.8219)
)