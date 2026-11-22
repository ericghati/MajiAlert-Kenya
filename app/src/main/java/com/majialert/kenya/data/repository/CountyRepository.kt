
package com.majialert.kenya.data.repository

import com.google.firebase.firestore.FirebaseFirestore

object CountyRepository {

    private val db = FirebaseFirestore.getInstance()

    fun getCountyRisk(
        countyCode: String,
        onSuccess: (Map<String, Any?>?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("counties")
            .document(countyCode)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onSuccess(document.data)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }
}
