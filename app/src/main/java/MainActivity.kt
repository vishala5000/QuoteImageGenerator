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

        quoteInput = findViewById(R.id.quoteInput)
        generateButton = findViewById(R.id.generateButton)
        statusText = findViewById(R.id.statusText)

        generateButton.setOnClickListener {
            startGeneration()
        }
    }

    private fun startGeneration() {

        val quotes =
            quoteInput.text
                .toString()
                .split(Regex("\\r?\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

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

                generator.createZip(
                    images,
                    zipFile
                )

                val savedUri =
                    saveZipToDownloads(zipFile)

                runOnUiThread {

                    generateButton.isEnabled =
                        true

                    if (savedUri != null) {

                        statusText.text =
                            "✓ Complete\n" +
                            "${images.size} images generated\n" +
                            "ZIP saved to Downloads"

                        Toast.makeText(
                            this,
                            "ZIP saved to Downloads",
                            Toast.LENGTH_LONG
                        ).show()

                    } else {

                        statusText.text =
                            "Generation complete, " +
                            "but ZIP could not be saved."
                    }
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
    ): android.net.Uri? {

        val resolver = contentResolver

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    "images.zip"
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
            ) ?: return null

        try {

            resolver.openOutputStream(uri).use { output ->

                FileInputStream(sourceFile).use { input ->

                    if (output == null) {
                        throw Exception(
                            "Unable to open Downloads."
                        )
                    }

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

            return uri

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
