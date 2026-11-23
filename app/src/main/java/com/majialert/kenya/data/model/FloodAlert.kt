
package com.majialert.kenya.data.model

import com.google.firebase.Timestamp

data class FloodAlert(
    val id: String = "",
    val county: String = "",
    val severity: String = "",
    val source: String = "",
    val message: String = "",
    val reasons: List<String> = emptyList(),
    val issuedAt: Timestamp? = null,
    val validUntil: Timestamp? = null,
    val active: Boolean = false
)
