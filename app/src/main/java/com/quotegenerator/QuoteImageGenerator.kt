package com.quotegenerator

import android.content.Context
import android.graphics.Bitmap
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
            ).use {

                android.graphics.BitmapFactory
                    .decodeStream(it)
            }
                ?: throw Exception(
                    "Could not load bg.png"
                )

        typeface =
            context.assets.open(
                "font.ttf"
            ).use {

                Typeface.createFromFile(
                    copyAssetToCache(
                        "font.ttf"
                    )
                )
            }
    }

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

    fun generateAll(
        quotes: List<String>,
        outputDir: File,
        progress: (Int, Int) -> Unit
    ): List<File> {

        if (outputDir.exists()) {

            outputDir.deleteRecursively()
        }

        outputDir.mkdirs()

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

        val canvas =
            Canvas(bitmap)

        val backgroundScaled =
            resizeCropBackground(
                background,
                IMAGE_WIDTH,
                IMAGE_HEIGHT
            )

        canvas.drawBitmap(
            backgroundScaled,
            0f,
            0f,
            null
        )

        backgroundScaled.recycle()

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

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
                (fitted.fontSize * 0.20f)
                    .toInt()
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
        ).use {

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                it
            )
        }

        bitmap.recycle()

        return output
    }

    private data class FittedText(
        val lines: List<String>,
        val fontSize: Int
    )

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

                    lines.add(current)
                }

                if (
                    paint.measureText(word) <=
                        maxWidth
                ) {

                    current =
                        word

                } else {

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

            lines.add(current)
        }

        return lines
    }

    private fun resizeCropBackground(
        source: Bitmap,
        width: Int,
        height: Int
    ): Bitmap {

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

        val left =
            (newWidth - width) / 2

        val top =
            (newHeight - height) / 2

        return Bitmap.createBitmap(
            resized,
            left,
            top,
            width,
            height
        ).also {

            if (it !== resized) {

                resized.recycle()
            }
        }
    }

    fun createZip(
        images: List<File>,
        zipFile: File
    ) {

        ZipOutputStream(
            FileOutputStream(zipFile)
        ).use { zip ->

            images.forEachIndexed {
                    index,
                    file ->

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

                file.inputStream().use {
                    input ->

                    input.copyTo(zip)
                }

                zip.closeEntry()
            }
        }
    }
}
