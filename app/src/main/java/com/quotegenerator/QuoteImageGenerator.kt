package com.quotegenerator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG
            or Paint.SUBPIXEL_TEXT_FLAG
    ).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    init {

        // Load background ONCE
        val input =
            context.assets.open("bg.png")

        val original =
            BitmapFactory.decodeStream(input)

        input.close()

        requireNotNull(original) {
            "Unable to load Assets/bg.png"
        }

        background =
            resizeCrop(
                original,
                IMAGE_WIDTH,
                IMAGE_HEIGHT
            )

        if (original !== background) {
            original.recycle()
        }

        // Load font ONCE
        typeface =
            context.assets
                .open("font.ttf")
                .use { inputStream ->

                    Typeface.createFromFile(
                        copyAssetToCache(
                            inputStream,
                            "generator_font.ttf"
                        )
                    )
                }
    }

    private fun copyAssetToCache(
        inputStream: java.io.InputStream,
        filename: String
    ): File {

        val file =
            File(
                context.cacheDir,
                filename
            )

        if (!file.exists()) {

            FileOutputStream(file).use { output ->

                inputStream.copyTo(output)
            }
        }

        return file
    }

    private fun resizeCrop(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {

        val sourceRatio =
            source.width.toFloat() /
            source.height.toFloat()

        val targetRatio =
            targetWidth.toFloat() /
            targetHeight.toFloat()

        val newWidth: Int
        val newHeight: Int

        if (sourceRatio > targetRatio) {

            newHeight = targetHeight

            newWidth =
                (newHeight * sourceRatio)
                    .toInt()

        } else {

            newWidth = targetWidth

            newHeight =
                (newWidth / sourceRatio)
                    .toInt()
        }

        val resized =
            Bitmap.createScaledBitmap(
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
        ).also {

            if (resized !== source) {
                resized.recycle()
            }
        }
    }

    private fun wrapText(
        text: String,
        maxWidth: Float
    ): List<String> {

        val words =
            text.trim()
                .split(Regex("\\s+"))
                .filter {
                    it.isNotEmpty()
                }

        if (words.isEmpty()) {
            return emptyList()
        }

        val lines =
            mutableListOf<String>()

        var current = ""

        for (word in words) {

            val test =
                if (current.isEmpty()) {
                    word
                } else {
                    "$current $word"
                }

            if (
                paint.measureText(test)
                <= maxWidth
            ) {

                current = test

            } else {

                if (current.isNotEmpty()) {
                    lines.add(current)
                }

                // Handle words wider than the area
                if (
                    paint.measureText(word)
                    <= maxWidth
                ) {

                    current = word

                } else {

                    var part = ""

                    for (character in word) {

                        val testPart =
                            part + character

                        if (
                            paint.measureText(
                                testPart
                            ) <= maxWidth
                        ) {

                            part = testPart

                        } else {

                            if (part.isNotEmpty()) {
                                lines.add(part)
                            }

                            part =
                                character.toString()
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
        val spacing: Float
    )

    private fun fitText(
        quote: String
    ): FitResult {

        for (
            size in
            MAX_FONT_SIZE downTo MIN_FONT_SIZE
        ) {

            paint.typeface = typeface
            paint.textSize = size.toFloat()

            val lines =
                wrapText(
                    quote,
                    TEXT_AREA_WIDTH.toFloat()
                )

            val metrics =
                paint.fontMetrics

            val lineHeight =
                metrics.descent -
                metrics.ascent

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

            var maximumWidth = 0f

            for (line in lines) {

                maximumWidth =
                    max(
                        maximumWidth,
                        paint.measureText(line)
                    )
            }

            if (
                maximumWidth <=
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

        paint.typeface = typeface
        paint.textSize =
            MIN_FONT_SIZE.toFloat()

        return FitResult(
            wrapText(
                quote,
                TEXT_AREA_WIDTH.toFloat()
            ),
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

        val image =
            background.copy(
                Bitmap.Config.ARGB_8888,
                true
            )

        val canvas =
            Canvas(image)

        val result =
            fitText(quote)

        paint.typeface =
            typeface

        paint.textSize =
            result.fontSize

        paint.color =
            Color.WHITE

        paint.textAlign =
            Paint.Align.CENTER

        val metrics =
            paint.fontMetrics

        val lineHeight =
            metrics.descent -
            metrics.ascent

        val totalHeight =
            result.lines.size * lineHeight +
            max(
                0,
                result.lines.size - 1
            ) * result.spacing

        var y =
            IMAGE_HEIGHT / 2f -
            totalHeight / 2f -
            metrics.ascent

        for (line in result.lines) {

            canvas.drawText(
                line,
                IMAGE_WIDTH / 2f,
                y,
                paint
            )

            y +=
                lineHeight +
                result.spacing
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

            image.compress(
                Bitmap.CompressFormat.PNG,
                100,
                stream
            )
        }

        image.recycle()

        return output
    }

    fun generateAll(
        quotes: List<String>,
        outputDir: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<File> {

        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }

        outputDir.mkdirs()

        val images =
            ArrayList<File>(quotes.size)

        quotes.forEachIndexed { index, quote ->

            val number =
                index + 1

            val image =
                generateImage(
                    quote,
                    number,
                    outputDir
                )

            images.add(image)

            onProgress?.invoke(
                number,
                quotes.size
            )
        }

        return images
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
                    (
                        (imageNumber - 1) /
                        IMAGES_PER_FOLDER
                    ) + 1

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
