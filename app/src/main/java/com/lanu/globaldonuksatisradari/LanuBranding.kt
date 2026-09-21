package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LanuNavy = Color(0xFF0B2730)
private val LanuTeal = Color(0xFF087C86)
private val LanuTealDark = Color(0xFF075E66)
private val LanuGold = Color(0xFFE1A53B)
private val LanuIce = Color(0xFFEAF6F7)

@Composable
fun LanuBrandLockup(compact: Boolean = false) {
    val iconSize = if (compact) 38.dp else 58.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .background(
                    brush = Brush.linearGradient(listOf(LanuNavy, LanuTealDark)),
                    shape = RoundedCornerShape(if (compact) 11.dp else 16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "L",
                    color = Color.White,
                    fontSize = if (compact) 19.sp else 29.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )
                Box(
                    modifier = Modifier
                        .size(if (compact) 5.dp else 7.dp)
                        .background(LanuGold, shape = RoundedCornerShape(10.dp)),
                )
            }
        }
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "LANU",
                    color = LanuTeal,
                    fontSize = if (compact) 18.sp else 27.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.size(5.dp))
                Text(
                    "GLOBAL",
                    color = LanuGold,
                    fontSize = if (compact) 8.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
            }
            Text(
                "Donuk Gıda",
                fontSize = if (compact) 10.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = LanuNavy,
            )
            if (!compact) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "SALES • CRM • RADAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = LanuTealDark,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                )
            }
        }
    }
}

@Composable
fun LanuHeroHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LanuIce),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LanuBrandLockup()
        }
    }
}
