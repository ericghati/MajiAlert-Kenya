
package com.majialert.kenya.ui.counties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majialert.kenya.data.model.County
import com.majialert.kenya.data.model.CountyData

@Composable
fun CountiesScreen(
    selectedCountyCode: String = "KE047",
    onCountySelected: (County) -> Unit = {}
) {
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    val filteredCounties = CountyData.counties.filter {
        it.name.contains(searchQuery.trim(), ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Kenyan Counties",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Select a county to monitor flood risk")

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search counties") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("${filteredCounties.size} counties found")

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = filteredCounties,
                key = { it.code }
            ) { county ->
                CountyRow(
                    county = county,
                    selected = county.code == selectedCountyCode,
                    onClick = { onCountySelected(county) }
                )
            }
        }
    }
}

@Composable
private fun CountyRow(
    county: County,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    county.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    county.code,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (selected) {
                Text(
                    "Selected",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
