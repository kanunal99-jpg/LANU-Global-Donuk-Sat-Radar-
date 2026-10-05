package com.lanu.globaldonuksatisradari

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val APP_USER_AGENT =
    "LANU-Global-Donuk-Satis-Radari/0.3 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)"
private const val MAP_HEIGHT_DP = 320
private const val MAP_READY_TIMEOUT_MS = 7_000L
private const val MAX_FALLBACK_POINTS = 750

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BusinessMapPreview(
    businesses: List<VerifiedBusiness>,
    modifier: Modifier = Modifier,
) {
    val validBusinesses = remember(businesses) {
        businesses.filter(::hasValidMapCoordinate)
    }
    if (validBusinesses.isEmpty()) return

    val htmlKey = remember(validBusinesses) {
        validBusinesses.joinToString("|") {
            listOf(
                it.id,
                it.name,
                it.latitude,
                it.longitude,
                it.district,
                it.category,
            ).joinToString("~")
        }.hashCode()
    }
    val html = remember(htmlKey) { buildMapHtml(validBusinesses) }

    var mapReady by remember(htmlKey) { mutableStateOf(false) }
    var timedOut by remember(htmlKey) { mutableStateOf(false) }
    val bridge = remember(htmlKey) {
        MapJavascriptBridge(
            onReady = {
                mapReady = true
                timedOut = false
            },
            onError = {
                mapReady = false
                timedOut = true
            },
        )
    }

    LaunchedEffect(htmlKey, mapReady) {
        if (!mapReady) {
            delay(MAP_READY_TIMEOUT_MS)
            if (!mapReady) timedOut = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT_DP.dp)
            .testTag("business_map_container"),
    ) {
        OfflinePointDistributionFallback(
            businesses = validBusinesses,
            timedOut = timedOut,
            modifier = Modifier.fillMaxSize(),
        )

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mapReady) 1f else 0f)
                .testTag("business_map_webview"),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.userAgentString = APP_USER_AGENT
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(bridge, "AndroidMapBridge")
                    tag = htmlKey
                    loadDataWithBaseURL(
                        "https://lanumap.local/",
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            update = { webView ->
                if (webView.tag != htmlKey) {
                    mapReady = false
                    timedOut = false
                    webView.tag = htmlKey
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
}

@Composable
private fun OfflinePointDistributionFallback(
    businesses: List<VerifiedBusiness>,
    timedOut: Boolean,
    modifier: Modifier = Modifier,
) {
    val valid = remember(businesses) {
        businesses
            .filter(::hasValidMapCoordinate)
            .take(MAX_FALLBACK_POINTS)
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val pointColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    Box(
        modifier = modifier.background(backgroundColor),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val paddingPx = 24.dp.toPx()
            val drawableWidth = (size.width - paddingPx * 2).coerceAtLeast(1f)
            val drawableHeight = (size.height - paddingPx * 2).coerceAtLeast(1f)

            repeat(5) { index ->
                val fraction = index / 4f
                val x = paddingPx + drawableWidth * fraction
                val y = paddingPx + drawableHeight * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(x, paddingPx),
                    end = Offset(x, paddingPx + drawableHeight),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = gridColor,
                    start = Offset(paddingPx, y),
                    end = Offset(paddingPx + drawableWidth, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val latitudes = valid.mapNotNull(VerifiedBusiness::latitude)
            val longitudes = valid.mapNotNull(VerifiedBusiness::longitude)
            if (latitudes.isEmpty() || longitudes.isEmpty()) return@Canvas

            var minLat = latitudes.min()
            var maxLat = latitudes.max()
            var minLon = longitudes.min()
            var maxLon = longitudes.max()

            if (maxLat - minLat < 0.0001) {
                minLat -= 0.01
                maxLat += 0.01
            }
            if (maxLon - minLon < 0.0001) {
                minLon -= 0.01
                maxLon += 0.01
            }

            valid.forEach { business ->
                val latitude = business.latitude ?: return@forEach
                val longitude = business.longitude ?: return@forEach
                val xFraction = ((longitude - minLon) / (maxLon - minLon)).toFloat()
                val yFraction = ((maxLat - latitude) / (maxLat - minLat)).toFloat()
                drawCircle(
                    color = pointColor,
                    radius = 4.dp.toPx(),
                    center = Offset(
                        x = paddingPx + drawableWidth * xFraction,
                        y = paddingPx + drawableHeight * yFraction,
                    ),
                )
            }
        }

        Text(
            text = if (timedOut) {
                "Çevrimiçi harita yüklenemedi. Nokta dağılımı yerel fallback ile gösteriliyor."
            } else {
                "Harita yükleniyor… Nokta dağılımı hazır."
            },
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class MapJavascriptBridge(
    private val onReady: () -> Unit,
    private val onError: () -> Unit,
) {
    @JavascriptInterface
    fun onReady() = onReady.invoke()

    @JavascriptInterface
    fun onError(@Suppress("UNUSED_PARAMETER") reason: String) = onError.invoke()
}

private fun hasValidMapCoordinate(business: VerifiedBusiness): Boolean {
    val latitude = business.latitude
    val longitude = business.longitude
    return latitude != null &&
        longitude != null &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0
}

internal fun buildMapHtml(businesses: List<VerifiedBusiness>): String {
    val valid = businesses.filter(::hasValidMapCoordinate)
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
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
          <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css">
          <style>
            html, body, #map { height: 100%; margin: 0; background: #eef1f2; }
            body { font-family: sans-serif; }
            #map { min-height: ${MAP_HEIGHT_DP}px; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            const businesses = [$points];
            let mapStarted = false;

            function bridgeReady() {
              try { AndroidMapBridge.onReady(); } catch (_) {}
            }

            function bridgeError(reason) {
              try { AndroidMapBridge.onError(String(reason || 'unknown')); } catch (_) {}
            }

            function initializeMap() {
              if (mapStarted || typeof L === 'undefined') return;
              mapStarted = true;

              try {
                const map = L.map('map', { zoomControl: true });
                const attribution =
                  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a> · ODbL';
                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                  maxZoom: 19,
                  attribution: attribution
                }).addTo(map);

                const markers = [];
                businesses.forEach((business) => {
                  const marker = L.circleMarker(
                    [business.lat, business.lon],
                    {
                      radius: 7,
                      weight: 2,
                      fillOpacity: 0.85
                    }
                  ).addTo(map);
                  const popup = document.createElement('div');
                  const title = document.createElement('strong');
                  title.textContent = business.name;
                  popup.appendChild(title);
                  popup.appendChild(document.createElement('br'));
                  popup.appendChild(document.createTextNode(business.district));
                  if (business.category) {
                    popup.appendChild(document.createElement('br'));
                    popup.appendChild(document.createTextNode('Kategori: ' + business.category));
                  }
                  marker.bindPopup(popup);
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

                setTimeout(() => map.invalidateSize(), 50);
                bridgeReady();
              } catch (error) {
                bridgeError(error && error.message ? error.message : 'leaflet-init');
              }
            }

            function loadLeafletFallback() {
              const fallback = document.createElement('script');
              fallback.src = 'https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js';
              fallback.onload = initializeMap;
              fallback.onerror = () => bridgeError('leaflet-cdn-unavailable');
              document.head.appendChild(fallback);
            }
          </script>
          <script
            src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
            onload="initializeMap()"
            onerror="loadLeafletFallback()">
          </script>
        </body>
        </html>
    """.trimIndent()
}
