package com.example.mileagetracker.domain.usecase.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Typeface
import com.example.mileagetracker.util.DateFormats

internal fun pdfTitlePaint(): Paint =
    Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 28f
        color = Color.BLACK
        textAlign = Align.CENTER
    }

internal fun pdfSubtitlePaint(bodyPaint: Paint): Paint =
    Paint(bodyPaint).apply {
        textAlign = Align.CENTER
        textSize = 15f
    }

internal fun pdfHeadingPaint(): Paint =
    Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 16f
        color = Color.BLACK
    }

internal fun pdfSubtlePaint(): Paint =
    Paint().apply {
        textSize = 11f
        color = Color.GRAY
    }

internal fun pdfGeneratedTimestamp(): String = DateFormats.exportPdfGeneratedNow()
