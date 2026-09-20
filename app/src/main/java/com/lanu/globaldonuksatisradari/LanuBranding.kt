package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LanuNavy = Color(0xFF0B2F3A)
private val LanuTeal = Color(0xFF087F8C)
private val LanuIce = Color(0xFFE9F7F8)
private val LanuGold = Color(0xFFD59A2A)

@Composable
private fun LanuLogoMark(compact: Boolean = false) {
    val markSize = if (compact) 38.dp else 62.dp
    Surface(
        modifier = Modifier.size(markSize),
        shape = MaterialTheme.shapes.large,
        color = LanuNavy,
        contentColor = Color.White,
    ) {
        Canvas(modifier = Modifier.padding(if (compact) 7.dp else 10.dp)) {
            val inset = this.size.minDimension * 0.08f
            drawRoundRect(
                color = LanuIce,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    width = this.size.width - inset * 2,
                    height = this.size.height - inset * 2,
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = this.size.minDimension * 0.08f,
                ),
            )
            val midX = this.size.width / 2f
            val midY = this.size.height / 2f
            drawLine(
                color = LanuGold,
                start = Offset(midX, inset * 2),
                end = Offset(midX, this.size.height - inset * 2),
                strokeWidth = this.size.minDimension * 0.10f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = LanuTeal,
                start = Offset(inset * 2, midY),
                end = Offset(this.size.width - inset * 2, midY),
                strokeWidth = this.size.minDimension * 0.10f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color.White,
                radius = this.size.minDimension * 0.12f,
                center = Offset(midX, midY),
            )
        }
    }
}

@Composable
fun LanuBrandLockup(compact: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
    ) {
        LanuLogoMark(compact)
        Column {
            Text(
                "LANU",
                color = LanuTeal,
                fontSize = if (compact) 18.sp else 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp,
            )
            Text(
                "GLOBAL DONUK GIDA",
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (compact) 0.6.sp else 0.9.sp,
            )
            Text(
                "Satış • CRM • Gerçek Veri Radarı",
                style = MaterialTheme.typography.labelSmall,
                color = LanuGold,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun LanuHeroHeader() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LanuBrandLockup()
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "İstanbul Odaklı",
                    style = MaterialTheme.typography.labelLarge,
                    color = LanuTeal,
                    fontWeight = FontWeight.Bold,
                )
                Text("39 ilçe • gerçek kaynak", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
