package com.quotegenerator

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var quoteInput: EditText
    private lateinit var generateButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        quoteInput =
            findViewById(R.id.quoteInput)

        generateButton =
            findViewById(R.id.generateButton)

        statusText =
            findViewById(R.id.statusText)

        generateButton.setOnClickListener {

            startGeneration()
        }
    }

    private fun startGeneration() {

        val quotes =
            quoteInput.text
                .toString()
                .split(Regex("\\r?\\n"))
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotEmpty()
                }

        if (quotes.isEmpty()) {

            Toast.makeText(
                this,
                "Please enter at least one quote.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        generateButton.isEnabled = false

        statusText.text =
            "Starting generation..."

        thread {

            try {

                val generator =
                    QuoteImageGenerator(this)

                val outputDir =
                    File(
                        cacheDir,
                        "generated_images"
                    )

                if (outputDir.exists()) {

                    outputDir.deleteRecursively()
                }

                outputDir.mkdirs()

                val images =
                    generator.generateAll(
                        quotes,
                        outputDir
                    ) { current, total ->

                        runOnUiThread {

                            statusText.text =
                                "Generating $current / $total"
                        }
                    }

                runOnUiThread {

                    statusText.text =
                        "Creating ZIP..."
                }

                val zipFile =
                    File(
                        cacheDir,
                        "images.zip"
                    )

                if (zipFile.exists()) {

                    zipFile.delete()
                }

                generator.createZip(
                    images,
                    zipFile
                )

                saveZipToDownloads(zipFile)

                runOnUiThread {

                    generateButton.isEnabled =
                        true

                    statusText.text =
                        "✓ Complete\n" +
                        "${images.size} images generated\n" +
                        "ZIP saved to Downloads"

                    Toast.makeText(
                        this,
                        "ZIP saved to Downloads/QuoteImageGenerator",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (error: Exception) {

                runOnUiThread {

                    generateButton.isEnabled =
                        true

                    statusText.text =
                        "❌ Error:\n${error.message}"

                    Toast.makeText(
                        this,
                        error.message
                            ?: "Generation failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveZipToDownloads(
        sourceFile: File
    ) {

        val resolver =
            contentResolver

        val fileName =
            "images.zip"

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.Downloads.MIME_TYPE,
                    "application/zip"
                )

                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                            "/QuoteImageGenerator"
                )

                put(
                    MediaStore.Downloads.IS_PENDING,
                    1
                )
            }

        val uri =
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )
                ?: throw Exception(
                    "Could not create ZIP in Downloads."
                )

        try {

            resolver.openOutputStream(uri).use { output ->

                if (output == null) {

                    throw Exception(
                        "Could not open Downloads."
                    )
                }

                FileInputStream(
                    sourceFile
                ).use { input ->

                    input.copyTo(output)
                }
            }

            val completedValues =
                ContentValues().apply {

                    put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                    )
                }

            resolver.update(
                uri,
                completedValues,
                null,
                null
            )

        } catch (error: Exception) {

            resolver.delete(
                uri,
                null,
                null
            )

            throw error
        }
    }
}
