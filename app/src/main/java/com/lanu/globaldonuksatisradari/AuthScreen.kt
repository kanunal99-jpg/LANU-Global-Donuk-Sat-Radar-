package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.SupabaseAuthClient
import kotlinx.coroutines.launch

@Composable
fun SupabaseAuthScreen(auth: SupabaseAuthClient) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LANU Global Donuk Satış Radarı", style = MaterialTheme.typography.headlineSmall)
        Text("CRM senkronizasyonu için güvenli Supabase hesabınızla giriş yapın.")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("E-posta") },
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Şifre") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            enabled = !loading,
            onClick = {
                loading = true
                message = null
                scope.launch {
                    val result = auth.signIn(email, password)
                    loading = false
                    message = result.exceptionOrNull()?.message ?: "Giriş başarılı."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loading) "İşleniyor…" else "Giriş yap")
        }
        OutlinedButton(
            enabled = !loading,
            onClick = {
                loading = true
                message = null
                scope.launch {
                    val result = auth.signUp(email, password)
                    loading = false
                    message = result.exceptionOrNull()?.message
                        ?: "Kayıt başarılı. E-posta doğrulaması gerekiyorsa gelen kutunuzu kontrol edin."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Yeni hesap oluştur")
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
