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
import com.majialert.kenya.data.model.CountyData
import androidx.compose.runtime.mutableStateOf
import com.majialert.kenya.ui.counties.CountyDetailsScreen

private enum class AppDestination(val label: String) {
    Home("Home"),
    Map("Map"),
    Counties("Counties"),
    Safety("Safety")
}

@Composable
fun AppNavigation() {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    var selectedCountyCode by rememberSaveable {
        mutableStateOf("KE047")
    }

    var showCountyDetails by rememberSaveable {
        mutableStateOf(false)
    }

    val selectedCounty = CountyData.counties.first {
        it.code == selectedCountyCode
    }
    val destinations = AppDestination.values()

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = {
                            selectedIndex = index
                            showCountyDetails = false
                        },
                        icon = { Text(destination.label.take(1)) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {

            if (showCountyDetails) {

                CountyDetailsScreen(
                    county = selectedCounty,
                    onBack = {
                        showCountyDetails = false
                        selectedIndex = 2
                    }
                )

            } else {

                when (destinations[selectedIndex]) {

                    AppDestination.Home -> HomeScreen(
                        countyName = selectedCounty.name,
                        countyCode = selectedCounty.code,
                        onNavigateToMap = {
                            selectedIndex = 1
                        },
                        onNavigateToCounties = {
                            selectedIndex = 2
                        }
                    )

                    AppDestination.Map -> MapScreen(
                        alerts = emptyList()
                    )

                    AppDestination.Counties -> CountiesScreen(
                        selectedCountyCode = selectedCountyCode,
                        onCountySelected = { county ->
                            selectedCountyCode = county.code
                            showCountyDetails = true
                        }
                    )

                    AppDestination.Safety -> SafetyScreen()
                }
            }

        }
    }
}
