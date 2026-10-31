package io.github.yorulmazsinan.kokpit.drive

import androidx.annotation.StringRes
import io.github.yorulmazsinan.kokpit.R

enum class CompassPoint(@StringRes val label: Int, @StringRes val fullLabel: Int) {
    N(R.string.compass_n, R.string.compass_full_n), NE(R.string.compass_ne, R.string.compass_full_ne),
    E(R.string.compass_e, R.string.compass_full_e), SE(R.string.compass_se, R.string.compass_full_se),
    S(R.string.compass_s, R.string.compass_full_s), SW(R.string.compass_sw, R.string.compass_full_sw),
    W(R.string.compass_w, R.string.compass_full_w), NW(R.string.compass_nw, R.string.compass_full_nw);

    companion object {
        /** Eight 45° sectors, N covering [-22.5°, 22.5°). */
        fun fromBearing(deg: Float): CompassPoint {
            val normalized = ((deg % 360f) + 360f) % 360f
            return entries[((normalized + 22.5f) / 45f).toInt() % 8]
        }
    }
}
