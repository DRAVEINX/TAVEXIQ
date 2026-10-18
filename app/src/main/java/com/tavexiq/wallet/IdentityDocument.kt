package com.tavexiq.wallet

/** Metadata for a document reference. Do not store BSN, document numbers, access codes or scans here. */
data class IdentityDocument(
    val id: Long,
    val name: String,
    val type: String,
    val issuer: String,
    val expiryDate: String,
    val notes: String
)
