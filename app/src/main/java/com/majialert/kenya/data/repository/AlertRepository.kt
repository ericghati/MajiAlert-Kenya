
package com.majialert.kenya.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.majialert.kenya.data.model.FloodAlert

object AlertRepository {

    private val db = FirebaseFirestore.getInstance()

    fun observeActiveAlerts(
        onSuccess: (List<FloodAlert>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        return db.collection("alerts")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val alerts = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FloodAlert(
                            id = doc.id,
                            county = doc.getString("county") ?: "",
                            severity = doc.getString("severity") ?: "",
                            source = doc.getString("source") ?: "",
                            message = doc.getString("message") ?: "",
                            reasons = (doc.get("reasons") as? List<*>)
                                ?.filterIsInstance<String>()
                                ?: emptyList(),
                            issuedAt = doc.getTimestamp("issuedAt"),
                            validUntil = doc.getTimestamp("validUntil"),
                            active = doc.getBoolean("active") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                onSuccess(alerts)
            }
    }
}
