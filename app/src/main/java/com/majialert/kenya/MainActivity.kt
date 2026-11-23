
package com.majialert.kenya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.majialert.kenya.ui.navigation.AppNavigation
import com.majialert.kenya.ui.theme.MajiAlertTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MajiAlertTheme {
                AppNavigation()
            }
        }
    }
}
