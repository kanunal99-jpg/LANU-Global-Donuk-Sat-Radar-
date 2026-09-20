package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LanuNavy = Color(0xFF0F3440)
private val LanuTeal = Color(0xFF0F6B73)
private val LanuGold = Color(0xFFD69A3A)

@Composable
fun LanuBrandLockup(compact: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        Surface(
            modifier = Modifier.size(if (compact) 36.dp else 52.dp),
            shape = MaterialTheme.shapes.medium,
            color = LanuNavy,
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("L", fontSize = if (compact) 19.sp else 28.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column {
            Text(
                "LANU",
                color = LanuTeal,
                fontSize = if (compact) 17.sp else 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
            )
            Text(
                "Global Donuk Gıda",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!compact) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Satış & CRM Radarı",
                    style = MaterialTheme.typography.labelSmall,
                    color = LanuGold,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun LanuHeroHeader() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LanuBrandLockup()
        }
    }
}
