package com.aistudio.youthverse.rvzkmq.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Produces a diagnostic record without logging account identifiers, email addresses,
 * authentication providers, tokens, or raw backend error text.
 */
fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }.toList()
    val firestoreError = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    val rootError = firestoreError ?: causes.lastOrNull { it !is CancellationException } ?: exception
    val collection = path
        ?.substringBefore('/')
        ?.takeIf { it.isNotBlank() }
        ?.take(64)
        ?: "unknown"

    val diagnostic = JSONObject().apply {
        put("operationType", operationType.value)
        put("collection", collection)
        put("errorType", rootError.javaClass.simpleName)
        firestoreError?.let { put("firestoreCode", it.code.name) }
    }

    Log.e("FirestoreError", "Firestore operation failed: $diagnostic")
    return diagnostic.toString()
}
