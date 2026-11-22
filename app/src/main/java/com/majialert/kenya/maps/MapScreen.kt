package com.majialert.kenya.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

private val KENYA_CENTRE = LatLng(0.2, 37.9)

private fun Severity.color(): Color = when (this) {
    Severity.LOW -> Color(0xFFFBC02D)
    Severity.MODERATE -> Color(0xFFF57C00)
    Severity.HIGH -> Color(0xFFD32F2F)
    Severity.SEVERE -> Color(0xFF7B1FA2)
}

/**
 * @param alerts        live alerts from Member 3 (expired ones are filtered out here too, as a safety net)
 * @param focusCountyId optional: Member 1's county page can open the map zoomed on a county
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(alerts: List<FloodAlert>, focusCountyId: Int? = null) {
    val context = LocalContext.current
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(KENYA_CENTRE, 5.7f)
    }

    // ----- Permissions -----
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasPermission = r.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // ----- GPS: current location + nearest county -----
    var nearestCounty by remember { mutableStateOf<County?>(null) }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc: Location? ->
                loc ?: return@addOnSuccessListener
                nearestCounty = counties.minByOrNull {
                    val out = FloatArray(1)
                    Location.distanceBetween(loc.latitude, loc.longitude, it.lat, it.lng, out)
                    out[0]
                }
            }
    }

    // ----- Focus a county when asked -----
    LaunchedEffect(focusCountyId) {
        counties.firstOrNull { it.id == focusCountyId }?.let {
            cameraState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 9f)
            )
        }
    }

    // ----- Search -----
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        if (query.isBlank()) emptyList()
        else counties.filter { it.name.contains(query, ignoreCase = true) }.take(5)
    }

    var selectedSite by remember { mutableStateOf<FloodSite?>(null) }
    val activeAlerts = remember(alerts) { alerts.filter { it.isActive } }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                zoomGesturesEnabled = true,
                myLocationButtonEnabled = hasPermission
            ),
            onMapClick = { selectedSite = null }
        ) {
            // Flood-prone site markers
            floodProneSites.forEach { site ->
                Marker(
                    state = rememberMarkerState(key = site.name, position = LatLng(site.lat, site.lng)),
                    title = site.name,
                    snippet = site.county,
                    onClick = { selectedSite = site; false }
                )
            }
            // Active warnings drawn as coloured circles over the affected county
            activeAlerts.forEach { alert ->
                counties.firstOrNull { it.id == alert.countyId }?.let { c ->
                    Circle(
                        center = LatLng(c.lat, c.lng),
                        radius = 25_000.0,
                        fillColor = alert.severity.color().copy(alpha = 0.35f),
                        strokeColor = alert.severity.color(),
                        strokeWidth = 3f
                    )
                }
            }
        }

        // Search bar + dropdown
        Column(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search county") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            if (results.isNotEmpty()) {
                Surface(tonalElevation = 4.dp, shadowElevation = 4.dp) {
                    LazyColumn {
                        items(results, key = { it.id }) { c ->
                            Text(
                                c.name,
                                Modifier.fillMaxWidth().clickable {
                                    query = ""
                                    // animate from a coroutine-safe place: use the state's move
                                    cameraState.move(
                                        com.google.android.gms.maps.CameraUpdateFactory
                                            .newLatLngZoom(LatLng(c.lat, c.lng), 9f)
                                    )
                                }.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom info: site details, else nearest-county hint
        Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            selectedSite?.let { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text("${s.county} County", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(s.history)
                    }
                }
            } ?: nearestCounty?.let {
                val warn = activeAlerts.count { a -> a.countyId == it.id }
                AssistChip(
                    onClick = {},
                    label = { Text("You're near ${it.name} ┬À $warn active warning(s)") }
                )
            }
        }
    }
}
