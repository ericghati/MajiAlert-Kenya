
package com.majialert.kenya.data.repository

import com.google.firebase.firestore.FirebaseFirestore

data class MapCountyRisk(
    val countyCode: String,
    val riskBand: String,
    val asOf: com.google.firebase.Timestamp?
)

object MapRiskRepository {

    private val db = FirebaseFirestore.getInstance()

    fun getAllCountyRisks(
        onSuccess: (List<MapCountyRisk>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("counties")
            .get()
            .addOnSuccessListener { snapshot ->

                val risks = snapshot.documents.map { doc ->
                    MapCountyRisk(
                        countyCode = doc.id,
                        riskBand = doc.getString("riskBand")
                            ?: "unknown",
                        asOf = doc.getTimestamp("asOf")
                    )
                }

                onSuccess(risks)
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }
}
