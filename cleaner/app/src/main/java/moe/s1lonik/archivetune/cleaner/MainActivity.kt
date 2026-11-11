package moe.s1lonik.archivetune.cleaner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val packageNames: List<String> = listOf(
        "moe.rukamori.archivetune",
        "moe.rukamori.archivetune.debug",
        "moe.rukamori.archivetune.nightly",
    )

    private lateinit var statusView: TextView
    private lateinit var detectedView: TextView
    private lateinit var uninstallButton: Button

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun makeText(sizeSp: Float, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.textSize = sizeSp
        tv.setTextColor(Color.parseColor("#212121"))
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.topMargin = dp(12)
        tv.layoutParams = lp
        return tv
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(20))

        val title = makeText(24f, true)
        title.text = getString(R.string.app_name)
        root.addView(title)

        val explanation = makeText(15f)
        explanation.text = getString(R.string.explanation)
        root.addView(explanation)

        val warning = makeText(15f, true)
        warning.text = getString(R.string.warning)
        warning.setTextColor(Color.parseColor("#B71C1C"))
        root.addView(warning)

        detectedView = makeText(15f)
        root.addView(detectedView)

        statusView = makeText(17f, true)
        root.addView(statusView)

        uninstallButton = Button(this)
        uninstallButton.text = getString(R.string.button_uninstall)
        uninstallButton.textSize = 18f
        val btnLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(64),
        )
        btnLp.topMargin = dp(20)
        uninstallButton.layoutParams = btnLp
        uninstallButton.setOnClickListener { uninstallAll() }
        root.addView(uninstallButton)

        val releasesButton = Button(this)
        releasesButton.text = getString(R.string.button_releases)
        val relLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        relLp.topMargin = dp(12)
        releasesButton.layoutParams = relLp
        releasesButton.setOnClickListener { openReleases() }
        root.addView(releasesButton)

        val note = makeText(13f)
        note.text = getString(R.string.profiles_note)
        note.setTextColor(Color.parseColor("#616161"))
        root.addView(note)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        scroll.setBackgroundColor(Color.WHITE)
        // Edge-to-edge is enforced on targetSdk 35+: keep content out of system bars.
        scroll.setOnApplyWindowInsetsListener { v: View, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    /** Returns (packageName, versionName) for every installed ArchiveTune variant. */
    private fun findInstalled(): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>()
        for (name in packageNames) {
            try {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(name, 0)
                result.add(Pair(name, info.versionName ?: "?"))
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed.
            }
        }
        return result
    }

    private fun refreshStatus() {
        val installed = findInstalled()
        if (installed.isEmpty()) {
            detectedView.visibility = View.GONE
            statusView.text = getString(R.string.status_clean)
            statusView.setTextColor(Color.parseColor("#1B5E20"))
            uninstallButton.isEnabled = false
        } else {
            val sb = StringBuilder(getString(R.string.detected_title))
            for (item in installed) {
                sb.append("\n• ").append(getString(R.string.variant_line, item.first, item.second))
            }
            detectedView.text = sb.toString()
            detectedView.visibility = View.VISIBLE
            statusView.text = getString(R.string.status_found)
            statusView.setTextColor(Color.parseColor("#E65100"))
            uninstallButton.isEnabled = true
        }
    }

    private fun uninstallAll() {
        // Several uninstall dialogs queued at once; the system shows them one after another.
        for (item in findInstalled()) {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + item.first))
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, item.first, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openReleases() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/S1LOnik/ArchiveTune/releases/latest"),
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
