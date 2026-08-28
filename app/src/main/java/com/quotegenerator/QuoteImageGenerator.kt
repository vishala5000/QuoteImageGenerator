```kotlin
package com.quotegenerator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

class QuoteImageGenerator(
    private val context: Context
) {

    companion object {

        const val IMAGE_WIDTH = 1080
        const val IMAGE_HEIGHT = 1350

        const val TEXT_AREA_WIDTH = 680
        const val TEXT_AREA_HEIGHT = 850

        const val MAX_FONT_SIZE = 70
        const val MIN_FONT_SIZE = 20

        const val IMAGES_PER_FOLDER = 3
    }

    private val background: Bitmap

    private val typeface: Typeface

    init {

        background =
            context.assets.open(
                "bg.png"
            ).use { input ->

                BitmapFactory.decodeStream(input)
                    ?: throw Exception(
                        "Could not load bg.png"
                    )
            }

        val fontFile =
            copyAssetToCache(
                "font.ttf"
            )

        typeface =
            Typeface.createFromFile(
                fontFile
            )
    }

    // ============================================================
    // COPY FONT FROM ASSETS TO CACHE
    // ============================================================

    private fun copyAssetToCache(
        name: String
    ): File {

        val output =
            File(
                context.cacheDir,
                name
            )

        if (!output.exists()) {

            context.assets
                .open(name)
                .use { input ->

                    FileOutputStream(
                        output
                    ).use { file ->

                        input.copyTo(file)
                    }
                }
        }

        return output
    }

    // ============================================================
    // GENERATE ALL IMAGES
    // ============================================================

    fun generateAll(
        quotes: List<String>,
        outputDir: File,
        progress: (Int, Int) -> Unit
    ): List<File> {

        if (outputDir.exists()) {

            outputDir.deleteRecursively()
        }

        if (!outputDir.mkdirs() &&
            !outputDir.exists()
        ) {

            throw Exception(
                "Could not create output directory."
            )
        }

        val results =
            mutableListOf<File>()

        quotes.forEachIndexed {
                index,
                quote ->

            val number =
                index + 1

            val file =
                generateImage(
                    quote,
                    number,
                    outputDir
                )

            results.add(file)

            progress(
                number,
                quotes.size
            )
        }

        return results
    }

    // ============================================================
    // GENERATE SINGLE IMAGE
    // ============================================================

    private fun generateImage(
        quote: String,
        number: Int,
        outputDir: File
    ): File {

        val bitmap =
            Bitmap.createBitmap(
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                Bitmap.Config.ARGB_8888
            )

        try {

            val canvas =
                Canvas(bitmap)

            // Create an independent cropped background.
            val backgroundScaled =
                resizeCropBackground(
                    background,
                    IMAGE_WIDTH,
                    IMAGE_HEIGHT
                )

            try {

                canvas.drawBitmap(
                    backgroundScaled,
                    0f,
                    0f,
                    null
                )

            } finally {

                if (!backgroundScaled.isRecycled) {
                    backgroundScaled.recycle()
                }
            }

            val paint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                )

            paint.typeface =
                typeface

            paint.color =
                android.graphics.Color.WHITE

            paint.textAlign =
                Paint.Align.CENTER

            val fitted =
                fitText(
                    paint,
                    quote
                )

            paint.textSize =
                fitted.fontSize.toFloat()

            val lines =
                fitted.lines

            val lineSpacing =
                max(
                    5,
                    (
                        fitted.fontSize *
                            0.20f
                        ).toInt()
                )

            val totalHeight =
                lines.size *
                    fitted.fontSize +
                    (lines.size - 1) *
                    lineSpacing

            var y =
                IMAGE_HEIGHT / 2f -
                    totalHeight / 2f -
                    paint.ascent()

            val centerX =
                IMAGE_WIDTH / 2f

            for (line in lines) {

                canvas.drawText(
                    line,
                    centerX,
                    y,
                    paint
                )

                y +=
                    fitted.fontSize +
                    lineSpacing
            }

            val output =
                File(
                    outputDir,
                    "$number.png"
                )

            FileOutputStream(
                output
            ).use { stream ->

                val success =
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        stream
                    )

                if (!success) {

                    throw Exception(
                        "Could not save image $number.png"
                    )
                }
            }

            return output

        } finally {

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    // ============================================================
    // FITTED TEXT DATA
    // ============================================================

    private data class FittedText(
        val lines: List<String>,
        val fontSize: Int
    )

    // ============================================================
    // FIT TEXT
    // ============================================================

    private fun fitText(
        paint: Paint,
        text: String
    ): FittedText {

        for (
            size in
            MAX_FONT_SIZE downTo MIN_FONT_SIZE
        ) {

            paint.textSize =
                size.toFloat()

            val lines =
                wrapText(
                    paint,
                    text,
                    TEXT_AREA_WIDTH
                )

            val spacing =
                max(
                    5,
                    (size * 0.20f).toInt()
                )

            val totalHeight =
                lines.size * size +
                    (lines.size - 1) *
                    spacing

            val maxWidth =
                lines.maxOfOrNull {
                    paint.measureText(it)
                }
                    ?: 0f

            if (
                maxWidth <=
                    TEXT_AREA_WIDTH &&
                totalHeight <=
                    TEXT_AREA_HEIGHT
            ) {

                return FittedText(
                    lines,
                    size
                )
            }
        }

        paint.textSize =
            MIN_FONT_SIZE.toFloat()

        return FittedText(
            wrapText(
                paint,
                text,
                TEXT_AREA_WIDTH
            ),
            MIN_FONT_SIZE
        )
    }

    // ============================================================
    // TEXT WRAPPING
    // ============================================================

    private fun wrapText(
        paint: Paint,
        text: String,
        maxWidth: Int
    ): List<String> {

        val words =
            text.trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotEmpty()
                }

        if (words.isEmpty()) {

            return emptyList()
        }

        val lines =
            mutableListOf<String>()

        var current =
            ""

        for (word in words) {

            val test =
                if (current.isEmpty()) {

                    word

                } else {

                    "$current $word"
                }

            if (
                paint.measureText(test) <=
                    maxWidth
            ) {

                current =
                    test

            } else {

                if (current.isNotEmpty()) {

                    lines.add(
                        current
                    )
                }

                // Normal word fits.
                if (
                    paint.measureText(word) <=
                        maxWidth
                ) {

                    current =
                        word

                } else {

                    // Handle very long words.
                    var partial =
                        ""

                    for (char in word) {

                        val testChar =
                            partial + char

                        if (
                            paint.measureText(
                                testChar
                            ) <= maxWidth
                        ) {

                            partial =
                                testChar

                        } else {

                            if (
                                partial.isNotEmpty()
                            ) {

                                lines.add(
                                    partial
                                )
                            }

                            partial =
                                char.toString()
                        }
                    }

                    current =
                        partial
                }
            }
        }

        if (current.isNotEmpty()) {

            lines.add(
                current
            )
        }

        return lines
    }

    // ============================================================
    // RESIZE + CROP BACKGROUND
    // ============================================================
    //
    // IMPORTANT:
    // This function creates a completely independent bitmap.
    // The temporary resized bitmap is only recycled AFTER
    // the pixels have been copied into the new bitmap.
    //
    // This prevents:
    //
    // "cannot use a recycled source in createBitmap"
    //
    // ============================================================

    private fun resizeCropBackground(
        source: Bitmap,
        width: Int,
        height: Int
    ): Bitmap {

        if (source.isRecycled) {

            throw Exception(
                "Background bitmap has been recycled."
            )
        }

        val sourceRatio =
            source.width.toFloat() /
                source.height.toFloat()

        val targetRatio =
            width.toFloat() /
                height.toFloat()

        val newWidth: Int

        val newHeight: Int

        if (
            sourceRatio > targetRatio
        ) {

            newHeight =
                height

            newWidth =
                (
                    height *
                        sourceRatio
                    ).toInt()

        } else {

            newWidth =
                width

            newHeight =
                (
                    width /
                        sourceRatio
                    ).toInt()
        }

        val resized =
            Bitmap.createScaledBitmap(
                source,
                newWidth,
                newHeight,
                true
            )

        try {

            val left =
                (newWidth - width) / 2

            val top =
                (newHeight - height) / 2

            // Always create a completely independent bitmap.
            val cropped =
                Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

            val canvas =
                Canvas(cropped)

            canvas.drawBitmap(
                resized,
                -left.toFloat(),
                -top.toFloat(),
                null
            )

            return cropped

        } finally {

            // The returned bitmap is independent,
            // so now it is safe to recycle resized.
            if (
                resized !== source &&
                !resized.isRecycled
            ) {

                resized.recycle()
            }
        }
    }

    // ============================================================
    // CREATE ZIP
    // ============================================================

    fun createZip(
        images: List<File>,
        zipFile: File
    ) {

        if (zipFile.exists()) {

            zipFile.delete()
        }

        zipFile.parentFile?.mkdirs()

        ZipOutputStream(
            FileOutputStream(zipFile)
        ).use { zip ->

            images.forEachIndexed {
                    index,
                    file ->

                if (!file.exists()) {

                    throw Exception(
                        "Image file not found: ${file.name}"
                    )
                }

                val number =
                    index + 1

                val folder =
                    (
                        (number - 1) /
                            IMAGES_PER_FOLDER
                        ) + 1

                val entryName =
                    "$folder/$number.png"

                zip.putNextEntry(
                    ZipEntry(entryName)
                )

                file.inputStream().use { input ->

                    input.copyTo(zip)
                }

                zip.closeEntry()
            }
        }
    }
}
```
