package com.lanu.globaldonuksatisradari

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import org.json.JSONObject

private const val APP_USER_AGENT =
    "LANU-Global-Donuk-Satis-Radari/0.1 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BusinessMapPreview(
    businesses: List<VerifiedBusiness>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(320.dp),
) {
    if (businesses.isEmpty()) return

    val html = remember(businesses.map { it.id }) {
        buildMapHtml(businesses)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = APP_USER_AGENT
                setBackgroundColor(0xFFF5F5F5.toInt())
                loadDataWithBaseURL("https://lanumap.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            val currentHash = html.hashCode()
            if (webView.tag != currentHash) {
                webView.tag = currentHash
                webView.loadDataWithBaseURL(
                    "https://lanumap.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

private fun buildMapHtml(businesses: List<VerifiedBusiness>): String {
    val valid = businesses.filter { it.latitude != null && it.longitude != null }
    val points = valid.joinToString(",") { business ->
        """{
            name: ${JSONObject.quote(business.name)},
            lat: ${business.latitude},
            lon: ${business.longitude},
            district: ${JSONObject.quote(business.district)},
            category: ${JSONObject.quote(business.category ?: "")}
        }""".trimIndent()
    }

    return """
        <!doctype html>
        <html lang="tr">
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
                integrity="sha256-p4NxAoJBhIINfQ3iy6DfQvK5QjFQO5w5QzjLkMZ8x0M=" crossorigin=""/>
          <style>
            html, body, #map { height: 100%; margin: 0; }
            body { font-family: sans-serif; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                  integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
          <script>
            const businesses = [$points];
            const map = L.map('map', { zoomControl: true });
            const attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a> · ODbL';
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: attribution
            }).addTo(map);

            const markers = [];
            businesses.forEach((business) => {
              const marker = L.marker([business.lat, business.lon]).addTo(map);
              const category = business.category ? '<br>Kategori: ' + business.category : '';
              marker.bindPopup('<strong>' + business.name + '</strong><br>' + business.district + category);
              markers.push(marker);
            });

            if (businesses.length === 1) {
              map.setView([businesses[0].lat, businesses[0].lon], 15);
            } else if (businesses.length > 1) {
              const group = L.featureGroup(markers);
              map.fitBounds(group.getBounds().pad(0.18), { maxZoom: 15 });
            } else {
              map.setView([41.0082, 28.9784], 10);
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
