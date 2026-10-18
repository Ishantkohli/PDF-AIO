package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val type: String, // PEN, HIGHLIGHT, STICKY_NOTE, STAMP, SIGNATURE
    val pointsJson: String = "", // JSON array of [{"x": 0.1, "y": 0.2}, ...]
    val colorHex: Long = 0xFFFF0000,
    val strokeWidth: Float = 4f,
    val posX: Float = 0f, // Normalized x (0.0 .. 1.0)
    val posY: Float = 0f, // Normalized y (0.0 .. 1.0)
    val width: Float = 0f, // Normalized width
    val height: Float = 0f, // Normalized height
    val textContent: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "My Signature",
    val pathsJson: String, // JSON array of stroke paths
    val createdTimestamp: Long = System.currentTimeMillis()
)
