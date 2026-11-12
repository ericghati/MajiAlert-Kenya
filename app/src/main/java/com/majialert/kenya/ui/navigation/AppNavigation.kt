package com.majialert.kenya.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.majialert.kenya.maps.MapScreen
import com.majialert.kenya.ui.counties.CountiesScreen
import com.majialert.kenya.ui.home.HomeScreen
import com.majialert.kenya.ui.safety.SafetyScreen

private enum class AppDestination(val label: String) {
    Home("Home"),
    Map("Map"),
    Counties("Counties"),
    Safety("Safety")
}

@Composable
fun AppNavigation() {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val destinations = AppDestination.values()

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { Text(destination.label.take(1)) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when (destinations[selectedIndex]) {
                AppDestination.Home -> HomeScreen()
                AppDestination.Map -> MapScreen()
                AppDestination.Counties -> CountiesScreen()
                AppDestination.Safety -> SafetyScreen()
            }
        }
    }
}
