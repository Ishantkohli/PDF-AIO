package com.example.data.pdf

data class PptSlide(
    val slideNumber: Int,
    val title: String,
    val paragraphs: List<String>,
    val speakerNotes: String = "",
    val imageUris: List<String> = emptyList(),
    val accentColorHex: String = "#38BDF8"
)

data class PptPresentation(
    val title: String,
    val slides: List<PptSlide>,
    val totalSlides: Int,
    val fileSizeBytes: Long = 0L,
    val sourceUriString: String? = null
)
