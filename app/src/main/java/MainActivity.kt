package com.quotegenerator

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var quoteInput: EditText
    private lateinit var generateButton: Button

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        quoteInput =
            findViewById(
                R.id.quoteInput
            )

        generateButton =
            findViewById(
                R.id.generateButton
            )

        generateButton.setOnClickListener {

            generateQuotes()
        }
    }

    private fun generateQuotes() {

        val quotes =
            quoteInput
                .text
                .toString()
                .split(
                    Regex("\\r?\\n")
                )
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

        generateButton.isEnabled =
            false

        thread {

            try {

                val generator =
                    QuoteImageGenerator(
                        this
                    )

                val outputDir =
                    File(
                        cacheDir,
                        "generated_images"
                    )

                val images =
                    generator.generateAll(
                        quotes,
                        outputDir
                    )

                val zipFile =
                    File(
                        cacheDir,
                        "images.zip"
                    )

                generator.createZip(
                    images,
                    zipFile
                )

                runOnUiThread {

                    generateButton.isEnabled =
                        true

                    Toast.makeText(
                        this,
                        "${images.size} images generated",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (error: Exception) {

                runOnUiThread {

                    generateButton.isEnabled =
                        true

                    Toast.makeText(
                        this,
                        "Error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
