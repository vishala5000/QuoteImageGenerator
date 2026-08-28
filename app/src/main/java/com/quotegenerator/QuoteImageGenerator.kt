package com.quotegenerator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

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

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private fun loadBackground(): Bitmap {
        val input = context.assets.open("bg.png")
        val bitmap = BitmapFactory.decodeStream(input)
        input.close()

        return resizeCrop(
            bitmap,
            IMAGE_WIDTH,
            IMAGE_HEIGHT
        )
    }

    private fun loadFont(): Typeface {
        return Typeface.createFromAsset(
            context.assets,
            "font.ttf"
        )
    }

    private fun resizeCrop(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {

        val sourceRatio =
            source.width.toFloat() / source.height.toFloat()

        val targetRatio =
            targetWidth.toFloat() / targetHeight.toFloat()

        val newWidth: Int
        val newHeight: Int

        if (sourceRatio > targetRatio) {

            newHeight = targetHeight

            newWidth =
                (newHeight * sourceRatio).toInt()

        } else {

            newWidth = targetWidth

            newHeight =
                (newWidth / sourceRatio).toInt()
        }

        val resized = Bitmap.createScaledBitmap(
            source,
            newWidth,
            newHeight,
            true
        )

        val left =
            (newWidth - targetWidth) / 2

        val top =
            (newHeight - targetHeight) / 2

        return Bitmap.createBitmap(
            resized,
            left,
            top,
            targetWidth,
            targetHeight
        )
    }

    private fun textWidth(
        text: String,
        paint: Paint
    ): Float {

        return paint.measureText(text)
    }

    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Float
    ): List<String> {

        val words = text.trim().split(
            Regex("\\s+")
        )

        if (words.isEmpty()) {
            return emptyList()
        }

        val lines = mutableListOf<String>()

        var current = ""

        for (word in words) {

            val test =
                if (current.isEmpty()) {
                    word
                } else {
                    "$current $word"
                }

            if (textWidth(test, paint) <= maxWidth) {

                current = test

            } else {

                if (current.isNotEmpty()) {
                    lines.add(current)
                }

                if (
                    textWidth(word, paint)
                    <= maxWidth
                ) {

                    current = word

                } else {

                    var part = ""

                    for (character in word) {

                        val testPart =
                            part + character

                        if (
                            textWidth(
                                testPart,
                                paint
                            ) <= maxWidth
                        ) {

                            part = testPart

                        } else {

                            if (part.isNotEmpty()) {
                                lines.add(part)
                            }

                            part = character.toString()
                        }
                    }

                    current = part
                }
            }
        }

        if (current.isNotEmpty()) {
            lines.add(current)
        }

        return lines
    }

    private data class FitResult(
        val lines: List<String>,
        val fontSize: Float,
        val lineSpacing: Float
    )

    private fun fitText(
        quote: String,
        typeface: Typeface
    ): FitResult {

        for (
            size in
            MAX_FONT_SIZE downTo MIN_FONT_SIZE
        ) {

            textPaint.typeface = typeface
            textPaint.textSize = size.toFloat()

            val lines =
                wrapText(
                    quote,
                    textPaint,
                    TEXT_AREA_WIDTH.toFloat()
                )

            val fontMetrics =
                textPaint.fontMetrics

            val lineHeight =
                fontMetrics.descent -
                fontMetrics.ascent

            val spacing =
                max(
                    5f,
                    size * 0.20f
                )

            val totalHeight =
                lines.size * lineHeight +
                max(
                    0,
                    lines.size - 1
                ) * spacing

            var maxWidth = 0f

            for (line in lines) {

                maxWidth =
                    max(
                        maxWidth,
                        textWidth(
                            line,
                            textPaint
                        )
                    )
            }

            if (
                maxWidth <=
                TEXT_AREA_WIDTH
                &&
                totalHeight <=
                TEXT_AREA_HEIGHT
            ) {

                return FitResult(
                    lines,
                    size.toFloat(),
                    spacing
                )
            }
        }

        textPaint.typeface = typeface
        textPaint.textSize =
            MIN_FONT_SIZE.toFloat()

        val lines =
            wrapText(
                quote,
                textPaint,
                TEXT_AREA_WIDTH.toFloat()
            )

        return FitResult(
            lines,
            MIN_FONT_SIZE.toFloat(),
            max(
                5f,
                MIN_FONT_SIZE * 0.20f
            )
        )
    }

    fun generateImage(
        quote: String,
        number: Int,
        outputDir: File
    ): File {

        val background =
            loadBackground()

        val typeface =
            loadFont()

        val bitmap =
            background.copy(
                Bitmap.Config.ARGB_8888,
                true
            )

        val canvas =
            Canvas(bitmap)

        val result =
            fitText(
                quote,
                typeface
            )

        textPaint.typeface = typeface
        textPaint.textSize =
            result.fontSize

        textPaint.color =
            android.graphics.Color.WHITE

        textPaint.textAlign =
            Paint.Align.CENTER

        val fontMetrics =
            textPaint.fontMetrics

        val lineHeight =
            fontMetrics.descent -
            fontMetrics.ascent

        val totalHeight =
            result.lines.size * lineHeight +
            max(
                0,
                result.lines.size - 1
            ) * result.lineSpacing

        var y =
            IMAGE_HEIGHT / 2f -
            totalHeight / 2f -
            fontMetrics.ascent

        for (line in result.lines) {

            canvas.drawText(
                line,
                IMAGE_WIDTH / 2f,
                y,
                textPaint
            )

            y +=
                lineHeight +
                result.lineSpacing
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val output =
            File(
                outputDir,
                "$number.png"
            )

        FileOutputStream(output).use { stream ->

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                stream
            )
        }

        bitmap.recycle()

        return output
    }

    fun generateAll(
        quotes: List<String>,
        outputDir: File
    ): List<File> {

        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }

        outputDir.mkdirs()

        val background =
            loadBackground()

        background.recycle()

        val generated =
            mutableListOf<File>()

        quotes.forEachIndexed { index, quote ->

            generated.add(
                generateImage(
                    quote,
                    index + 1,
                    outputDir
                )
            )
        }

        return generated
    }

    fun createZip(
        images: List<File>,
        zipFile: File
    ) {

        if (zipFile.exists()) {
            zipFile.delete()
        }

        ZipOutputStream(
            FileOutputStream(zipFile)
        ).use { zip ->

            images.forEachIndexed { index, image ->

                val imageNumber =
                    index + 1

                val folderNumber =
                    ((imageNumber - 1) /
                        IMAGES_PER_FOLDER) + 1

                val entryName =
                    "$folderNumber/$imageNumber.png"

                zip.putNextEntry(
                    ZipEntry(entryName)
                )

                image.inputStream().use { input ->

                    input.copyTo(zip)
                }

                zip.closeEntry()
            }
        }
    }
}
