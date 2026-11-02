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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject

/**
 * Kokpit Kumanda: connect to the unit over Bluetooth — "Bağlan" (the unit asks "İzin ver")
 * or the unit's panel QR — and show the very same panel page the unit serves to a phone's
 * browser. Nothing here can do more than that page: the unit still hands out a one-time
 * token only to someone in the car, then asks for the PIN (or this phone's key, if the owner
 * chose to remember it), and keeps its time limits. Also: a fuel receipt read on the phone
 * (offline OCR) and, if turned on, a parking-spot notification at ignition off.
 */
class KumandaActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val link by lazy { UnitLink(this) }
    private var bridge: LocalBridge? = null
    private var pending: QrResult.Panel? = null
    /** "Bağlan" waits for permissions / Bluetooth like a scanned QR does. */
    private var pendingKnock = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private lateinit var home: View
    private lateinit var status: TextView
    private lateinit var scan: Button
    private lateinit var knock: Button
    private lateinit var park: Button
    private lateinit var parkToggle: Button
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
            knock = button(getString(R.string.knock), primary = true) { startKnock() }
            addView(knock, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
            scan = button(getString(R.string.scan), primary = true) { startScan() }
            addView(scan, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            status = text(17f, TITLE).apply { setPadding(0, dp(20), 0, dp(20)); gravity = Gravity.CENTER_HORIZONTAL }
            addView(status)
            park = button("", primary = false) { openPark() }
            addView(park, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
            parkToggle = button("", primary = false) { togglePark() }
            addView(parkToggle, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(20) })
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

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (primary) 20f else 16f)
        setTextColor(if (primary) BG else FG)
        setBackgroundColor(if (primary) ACCENT else CARD)
        minHeight = dp(if (primary) 72 else 56)
        setOnClickListener { onClick() }
    }

    override fun onResume() {
        super.onResume()
        refreshHome()
    }

    /** "Bağlan" once a unit is known (the QR then becomes the second way); park buttons. */
    private fun refreshHome() {
        val known = link.lastUnit != null
        knock.visibility = if (known) View.VISIBLE else View.GONE
        scan.setTextColor(if (known) FG else BG)
        scan.setBackgroundColor(if (known) CARD else ACCENT)
        val spot = ParkWatch.last(this)
        park.visibility = if (spot != null) View.VISIBLE else View.GONE
        if (spot != null) {
            val time = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("tr")).format(java.util.Date(spot.third))
            park.text = getString(R.string.park_where, time)
        }
        val on = ParkWatch.enabled(this)
        parkToggle.text = getString(
            when {
                !on -> R.string.park_off
                !ParkWatch.hasLocation(this) || !ParkWatch.hasBackgroundLocation(this) -> R.string.park_needs_always
                else -> R.string.park_on
            },
        )
    }

    private fun openPark() {
        val spot = ParkWatch.last(this) ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(parkLink(spot.first, spot.second)))) }
    }

    /** Parking notification: on needs location (Android 10+: "Her zaman") and, on 13+, notifications. */
    private fun togglePark() {
        val on = ParkWatch.enabled(this)
        if (on && ParkWatch.hasLocation(this) && ParkWatch.hasBackgroundLocation(this)) {
            ParkWatch.setEnabled(this, false)
            return refreshHome()
        }
        ParkWatch.setEnabled(this, true)
        val ask = buildList {
            if (!ParkWatch.hasLocation(this@KumandaActivity)) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        when {
            ask.isNotEmpty() -> requestPermissions(ask.toTypedArray(), REQ_PARK)
            !ParkWatch.hasBackgroundLocation(this) -> askBackgroundLocation()
            else -> refreshHome()
        }
    }

    private fun askBackgroundLocation() {
        show(getString(R.string.park_always_help))
        if (Build.VERSION.SDK_INT >= 29) requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_PARK_ALWAYS)
    }

    /** The panel's "Fişten doldur": scan the receipt, read it on the phone (offline), fill the form. */
    private fun scanReceipt() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                runCatching { startIntentSenderForResult(sender, REQ_RECEIPT, null, 0, 0, 0) }
                    .onFailure { fillReceipt(JSONObject().put("error", getString(R.string.receipt_failed))) }
            }
            .addOnFailureListener { fillReceipt(JSONObject().put("error", getString(R.string.receipt_failed))) }
    }

    private fun readReceipt(uri: Uri) {
        val image = runCatching { InputImage.fromFilePath(this, uri) }.getOrNull()
            ?: return fillReceipt(JSONObject().put("error", getString(R.string.receipt_failed)))
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { result ->
                val guess = parseReceipt(result.text, java.time.LocalDate.now())
                fillReceipt(JSONObject().apply {
                    guess.liters?.let { put("liters", it) }
                    guess.price?.let { put("price", it) }
                    guess.date?.let { put("date", it) }
                })
            }
            .addOnFailureListener { fillReceipt(JSONObject().put("error", getString(R.string.receipt_failed))) }
    }

    /** JSONObject quotes everything, so the page gets data, never script. */
    private fun fillReceipt(data: JSONObject) {
        if (web.visibility == View.VISIBLE) web.evaluateJavascript("window.kumandaFis&&kumandaFis($data)", null)
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
                if (u.scheme == "kumanda" && u.host == "fis") { scanReceipt(); return true }
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
                pendingKnock = false
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
        val qr = pending
        val viaKnock = pendingKnock
        if (qr == null && !viaKnock) return
        pending = null
        pendingKnock = false
        closePanel()
        show(getString(R.string.connecting))
        setBusy(true)
        Thread {
            try {
                val name = link.connect(qr?.unitName)
                val token = qr?.token ?: run {
                    main.post { show(getString(R.string.knock_waiting, name)) }
                    link.knock()
                }
                val b = LocalBridge(link).also { it.start() }
                main.post {
                    if (isDestroyed) { b.stop(); return@post }
                    bridge = b
                    show(getString(R.string.connected, name))
                    setBusy(false)
                    home.visibility = View.GONE
                    web.visibility = View.VISIBLE
                    web.loadUrl("http://127.0.0.1:${b.port}/?t=$token")
                }
            } catch (e: LinkException) {
                main.post { show(e.message.orEmpty()); setBusy(false); refreshHome() }
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) {
        scan.isEnabled = !busy
        knock.isEnabled = !busy
    }

    /** "Bağlan": no QR; the unit shows the request and opens the panel when "İzin ver" is tapped there. */
    private fun startKnock() {
        pending = null
        pendingKnock = true
        connect()
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
        refreshHome()
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
        when (requestCode) {
            REQ_BT -> if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) connect()
                else { pending = null; pendingKnock = false; show(getString(R.string.need_permission)) }
            REQ_PARK -> if (ParkWatch.hasLocation(this) && !ParkWatch.hasBackgroundLocation(this)) askBackgroundLocation() else refreshHome()
            REQ_PARK_ALWAYS -> refreshHome()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ENABLE -> if (resultCode == RESULT_OK) connect() else { pending = null; pendingKnock = false; show(getString(R.string.bt_off)) }
            REQ_RECEIPT -> {
                val page = if (resultCode == RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.firstOrNull() else null
                if (page != null) readReceipt(page.imageUri)
            }
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
        const val REQ_RECEIPT = 4
        const val REQ_PARK = 5
        const val REQ_PARK_ALWAYS = 6
        val BG = Color.parseColor("#0b0d0a")
        val FG = Color.parseColor("#d8eec4")
        val DIM = Color.parseColor("#8fa77a")
        val TITLE = Color.parseColor("#a9ec62")
        val ACCENT = Color.parseColor("#8dd443")
        val CARD = Color.parseColor("#1a2214")
    }
}
