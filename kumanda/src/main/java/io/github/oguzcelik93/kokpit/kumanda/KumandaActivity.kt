package io.github.oguzcelik93.kokpit.kumanda

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Kokpit Kumanda: scan the unit's panel QR, connect over Bluetooth, and show the very same
 * panel page the unit serves to a phone's browser. Nothing here can do more than that page:
 * the unit still asks for the QR's one-time token, then the PIN, and keeps its time limits.
 */
class KumandaActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val link by lazy { UnitLink(this) }
    private var bridge: LocalBridge? = null
    private var pending: QrResult.Panel? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private lateinit var home: View
    private lateinit var status: TextView
    private lateinit var scan: Button
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUi()
        handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /** `kokpit://panel?…` opened by the phone camera lands here too. */
    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) onQr(data.toString())
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun text(size: Float, color: Int, value: String = "") = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        text = value
        setLineSpacing(0f, 1.2f)
    }

    private fun buildUi() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            addView(text(28f, TITLE, getString(R.string.app_name)).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(text(17f, FG, getString(R.string.steps)).apply { setPadding(0, dp(20), 0, dp(24)) })
            scan = Button(this@KumandaActivity).apply {
                text = getString(R.string.scan)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(BG)
                setBackgroundColor(ACCENT)
                minHeight = dp(72)
                setOnClickListener { startScan() }
            }
            addView(scan, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            status = text(17f, TITLE).apply { setPadding(0, dp(20), 0, dp(20)); gravity = Gravity.CENTER_HORIZONTAL }
            addView(status)
            addView(text(14f, DIM, getString(R.string.note)))
        }
        home = ScrollView(this).apply { addView(column) }
        web = WebView(this).apply { visibility = View.GONE; setBackgroundColor(BG) }
        setUpWeb()
        val root = FrameLayout(this).apply {
            setBackgroundColor(BG)
            addView(home, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            // Edge to edge (Android 15+): keep clear of the bars and the keyboard (PIN entry).
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                    v.setPadding(b.left, b.top, b.right, b.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWeb() {
        web.settings.apply {
            javaScriptEnabled = true // the panel page is a small script
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" && u.host == "127.0.0.1" && u.port == bridge?.port) return false
                // Anything else (the parking spot's map link) opens outside, never inside the panel.
                if (u.scheme == "https" || u.scheme == "geo") runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val pick = params.createIntent().apply {
                    if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    startActivityForResult(pick, REQ_FILE)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }
    }

    private fun show(message: String) {
        status.text = message
    }

    private fun startScan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { onQr(it.rawValue.orEmpty()) }
            .addOnFailureListener { show(getString(R.string.scanner_failed)) }
    }

    private fun onQr(text: String) {
        when (val qr = parsePanelQr(text)) {
            QrResult.NotKokpit -> show(getString(R.string.not_kokpit))
            QrResult.NoBluetooth -> show(getString(R.string.unit_no_bt))
            is QrResult.Panel -> {
                pending = qr
                connect()
            }
        }
    }

    private fun hasBtPermission() =
        Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun connect() {
        if (!link.hasBluetooth) return show(getString(R.string.no_bt))
        if (!hasBtPermission()) return requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BT)
        if (!link.isEnabled) {
            return try {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE)
            } catch (_: Exception) {
                show(getString(R.string.bt_off))
            }
        }
        val qr = pending ?: return
        pending = null
        closePanel()
        show(getString(R.string.connecting))
        scan.isEnabled = false
        Thread {
            try {
                val name = link.connect(qr.unitName)
                val b = LocalBridge(link).also { it.start() }
                main.post {
                    if (isDestroyed) { b.stop(); return@post }
                    bridge = b
                    show(getString(R.string.connected, name))
                    scan.isEnabled = true
                    home.visibility = View.GONE
                    web.visibility = View.VISIBLE
                    web.loadUrl("http://127.0.0.1:${b.port}/?t=${qr.token}")
                }
            } catch (e: LinkException) {
                main.post { show(e.message.orEmpty()); scan.isEnabled = true }
            }
        }.start()
    }

    /** Back to the scan screen; the Bluetooth link to the unit is dropped. */
    private fun closePanel() {
        web.stopLoading()
        web.loadUrl("about:blank")
        web.visibility = View.GONE
        home.visibility = View.VISIBLE
        bridge?.stop()
        bridge = null
        Thread { link.close() }.start()
    }

    @Deprecated("Activity back handling")
    override fun onBackPressed() {
        if (web.visibility == View.VISIBLE) {
            closePanel()
            show(getString(R.string.closed))
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != REQ_BT) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) connect()
        else { pending = null; show(getString(R.string.need_permission)) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ENABLE -> if (resultCode == RESULT_OK) connect() else { pending = null; show(getString(R.string.bt_off)) }
            REQ_FILE -> {
                val uris = if (resultCode != RESULT_OK || data == null) null
                else data.clipData?.let { c -> Array(c.itemCount) { c.getItemAt(it).uri } } ?: data.data?.let { arrayOf(it) }
                fileCallback?.onReceiveValue(uris)
                fileCallback = null
            }
        }
    }

    override fun onDestroy() {
        bridge?.stop()
        bridge = null
        Thread { link.close() }.start()
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val REQ_BT = 1
        const val REQ_ENABLE = 2
        const val REQ_FILE = 3
        val BG = Color.parseColor("#0b0d0a")
        val FG = Color.parseColor("#d8eec4")
        val DIM = Color.parseColor("#8fa77a")
        val TITLE = Color.parseColor("#a9ec62")
        val ACCENT = Color.parseColor("#8dd443")
    }
}
