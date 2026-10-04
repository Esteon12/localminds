package com.localmind.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val runtimeDir by lazy { File(filesDir, "runtime") }
    private val modelFile by lazy { File(filesDir, "models/model.gguf") }
    private var modelHandle: Long? = null

    companion object { private const val PICK_MODEL = 7001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureRuntime()
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(Bridge(), "LocalMind")
        loadRuntime()
        if (modelFile.exists()) loadModel()
    }

    private fun ensureRuntime() {
        runtimeDir.mkdirs()
        listOf("index.html", "app.js", "style.css").forEach { name ->
            val dst = File(runtimeDir, name)
            if (!dst.exists()) assets.open("runtime/$name").use { input -> dst.outputStream().use { input.copyTo(it) } }
        }
        File(filesDir, "models").mkdirs()
        File(filesDir, "memory").mkdirs()
    }

    private fun loadRuntime() {
        webView.loadUrl("file://${File(runtimeDir, "index.html").absolutePath}")
    }

    private fun emit(type: String, payload: JSONObject) {
        val msg = JSONObject().put("type", type).put("payload", payload).toString()
        webView.post { webView.evaluateJavascript("window.onNativeMessage && window.onNativeMessage(${JSONObject.quote(msg)})", null) }
    }

    private fun loadModel() {
        scope.launch {
            try {
                modelHandle?.let { Llama.releaseModel(it) }
                modelHandle = withContext(Dispatchers.IO) {
                    Llama.loadModel(modelFile.absolutePath, LlamaConfig(contextSize = 2048, threads = 4))
                }
                emit("model", JSONObject().put("ready", true).put("path", modelFile.name))
            } catch (e: Exception) {
                emit("error", JSONObject().put("message", "Model yüklenemedi: ${e.message}"))
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface fun status(): String = JSONObject()
            .put("modelReady", modelHandle != null)
            .put("modelExists", modelFile.exists())
            .put("runtime", runtimeDir.absolutePath)
            .toString()

        @JavascriptInterface fun pickModel() {
            runOnUiThread {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                }
                startActivityForResult(i, PICK_MODEL)
            }
        }

        @JavascriptInterface fun chat(prompt: String): String {
            val id = UUID.randomUUID().toString()
            val handle = modelHandle
            if (handle == null) {
                emit("chat", JSONObject().put("id", id).put("error", "Önce GGUF model seç."))
                return id
            }
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        Llama.complete(
                            handle,
                            prompt = prompt,
                            systemPrompt = "You are LocalMind, an on-device personal AI. Be concise and practical. When the user asks to modify this app, propose edits only inside runtime/index.html, runtime/app.js, or runtime/style.css unless explicitly discussing a native rebuild.",
                            maxTokens = 512,
                        )
                    }
                    emit("chat", JSONObject().put("id", id).put("text", result.text))
                } catch (e: Exception) {
                    emit("chat", JSONObject().put("id", id).put("error", e.message ?: "Inference error"))
                }
            }
            return id
        }

        @JavascriptInterface fun readRuntime(name: String): String {
            val f = safeRuntimeFile(name) ?: return ""
            return if (f.exists()) f.readText() else ""
        }

        @JavascriptInterface fun writeRuntime(name: String, content: String): Boolean {
            val f = safeRuntimeFile(name) ?: return false
            return try { snapshot(); f.writeText(content); true } catch (_: Exception) { false }
        }

        @JavascriptInterface fun applyPatch(json: String): String {
            return try {
                val root = JSONObject(json)
                val files = root.getJSONArray("files")
                snapshot()
                for (i in 0 until files.length()) {
                    val o = files.getJSONObject(i)
                    val f = safeRuntimeFile(o.getString("name")) ?: error("Invalid file")
                    f.writeText(o.getString("content"))
                }
                JSONObject().put("ok", true).put("changed", files.length()).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
        }

        @JavascriptInterface fun reload() { runOnUiThread { loadRuntime() } }

        @JavascriptInterface fun rollback(): Boolean {
            val versions = File(filesDir, "versions").listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: return false
            val src = versions.firstOrNull() ?: return false
            listOf("index.html", "app.js", "style.css").forEach { n ->
                val f = File(src, n); if (f.exists()) f.copyTo(File(runtimeDir, n), overwrite = true)
            }
            runOnUiThread { loadRuntime() }
            return true
        }

        @JavascriptInterface fun remember(key: String, value: String) {
            val db = File(filesDir, "memory/simple.json")
            val obj = if (db.exists()) try { JSONObject(db.readText()) } catch (_: Exception) { JSONObject() } else JSONObject()
            obj.put(key, value)
            db.writeText(obj.toString(2))
        }

        @JavascriptInterface fun recall(key: String): String {
            val db = File(filesDir, "memory/simple.json")
            if (!db.exists()) return ""
            return try { JSONObject(db.readText()).optString(key, "") } catch (_: Exception) { "" }
        }
    }

    private fun safeRuntimeFile(name: String): File? {
        if (name !in setOf("index.html", "app.js", "style.css")) return null
        return File(runtimeDir, name)
    }

    private fun snapshot() {
        val root = File(filesDir, "versions").apply { mkdirs() }
        val dir = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }
        listOf("index.html", "app.js", "style.css").forEach { n ->
            val src = File(runtimeDir, n); if (src.exists()) src.copyTo(File(dir, n), overwrite = true)
        }
        root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.drop(10)?.forEach { it.deleteRecursively() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_MODEL && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            Toast.makeText(this, "Model telefona kopyalanıyor…", Toast.LENGTH_LONG).show()
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        modelFile.parentFile?.mkdirs()
                        contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input)
                            FileOutputStream(modelFile).use { output -> input.copyTo(output, 1024 * 1024) }
                        }
                    }
                    loadModel()
                } catch (e: Exception) {
                    emit("error", JSONObject().put("message", "Model kopyalanamadı: ${e.message}"))
                }
            }
        }
    }

    override fun onDestroy() {
        modelHandle?.let { Llama.releaseModel(it) }
        super.onDestroy()
    }
}
