package com.arbaz.tikdl // same package as in build.gradle

import android.app.Activity                 // basic screen class
import android.content.ContentValues        // holds file details for saving
import android.os.Bundle
import android.os.Handler                   // lets background work update the screen
import android.os.Looper
import android.provider.MediaStore          // saves video into the phone gallery
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject                  // reads the API's JSON answer
import java.net.HttpURLConnection           // makes internet requests
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread             // runs code in the background

class MainActivity : Activity() {

    private lateinit var input: EditText    // box where you paste the link
    private lateinit var status: TextView   // shows progress/errors
    private lateinit var button: Button     // the download button
    private val ui = Handler(Looper.getMainLooper()) // posts updates to the screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Vertical layout with padding, built in code (no XML needed)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }
        input = EditText(this).apply {
            hint = "Paste TikTok link here"  // grey helper text
            setSingleLine(true)
        }
        button = Button(this).apply {
            text = "Download HD (no watermark)"
            setOnClickListener { startDownload() } // when tapped, start
        }
        status = TextView(this).apply {
            text = "Ready"
            setPadding(0, 32, 0, 0)
        }
        layout.addView(input)   // add the three items to the screen
        layout.addView(button)
        layout.addView(status)
        setContentView(layout)  // show the layout
    }

    // Updates the status text safely from any thread
    private fun say(msg: String) = ui.post { status.text = msg }

    private fun startDownload() {
        val link = input.text.toString().trim() // read pasted link
        if (link.isEmpty()) { say("Please paste a link first"); return }
        button.isEnabled = false                // stop double taps
        say("Finding video...")

        thread { // everything below runs in the background
            try {
                // Ask the third-party service for the video links
                val api = "https://www.tikwm.com/api/?hd=1&url=" +
                        URLEncoder.encode(link, "UTF-8")
                val json = JSONObject(httpGet(api))
                if (json.optInt("code") != 0) {
                    throw Exception(json.optString("msg", "Video not found"))
                }
                val data = json.getJSONObject("data")

                // Prefer HD; fall back to normal quality if HD is missing
                var videoUrl = data.optString("hdplay")
                if (videoUrl.isBlank()) videoUrl = data.optString("play")
                if (videoUrl.startsWith("/")) videoUrl = "https://www.tikwm.com$videoUrl"

                say("Downloading...")
                saveVideo(videoUrl)
                say("Done! Saved in Movies/TikDL (check your Gallery)")
            } catch (e: Exception) {
                say("Error: ${e.message}") // show what went wrong
            } finally {
                ui.post { button.isEnabled = true } // enable button again
            }
        }
    }

    // Downloads a web page/text and returns it as a String
    private fun httpGet(address: String): String {
        val c = URL(address).openConnection() as HttpURLConnection
        c.connectTimeout = 15000                // give up after 15 seconds
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    // Downloads the video file and saves it into the Movies/TikDL folder
    private fun saveVideo(videoUrl: String) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "tiktok_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/TikDL")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Could not create file")

        val c = URL(videoUrl).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        contentResolver.openOutputStream(uri)!!.use { out ->
            c.inputStream.use { it.copyTo(out) } // copy video bytes into the file
        }
    }
}
