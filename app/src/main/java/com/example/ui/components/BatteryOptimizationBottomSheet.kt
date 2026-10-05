package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hoja inferior unificada de permisos y ajustes en segundo plano
 * con lista de verificación interactiva de 3 pasos:
 * 1. Notificaciones
 * 2. Batería sin restricciones
 * 3. Inicio automático (Xiaomi, Oppo, Realme, etc.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationBottomSheet(
    isDarkTheme: Boolean,
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    autoStartConfigured: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAutoStart: () -> Unit,
    onDismissClick: (dontShowAgain: Boolean) -> Unit,
    onAllReady: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val readyCount = listOf(notificationsGranted, batteryExempt, autoStartConfigured).count { it }
    val allReady = readyCount == 3

    val sheetBg = if (isDarkTheme) Color(0xFF131D31) else Color.White
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    ModalBottomSheet(
        onDismissRequest = { onDismissClick(false) },
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.5.dp),
                shape = RoundedCornerShape(3.dp),
                color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icono destacado profesional con el color de énfasis del tema activo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.16f else 0.10f))
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.35f else 0.22f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Título
            Text(
                text = "Mantén tus descargas activas",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            // Subtítulo
            Text(
                text = "Ajustes para que no se corten con la pantalla apagada.",
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                color = textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Contador de estado en cápsula: "X de 3 listos"
            Surface(
                shape = RoundedCornerShape(50),
                color = if (allReady) {
                    if (isDarkTheme) Color(0xFF064E3B).copy(alpha = 0.6f) else Color(0xFFE6F9F0)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.20f else 0.12f)
                },
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = "$readyCount de 3 listos",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (allReady) {
                        if (isDarkTheme) Color(0xFF34D399) else Color(0xFF10B981)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 1. Tarjeta: Notificaciones
            PermissionCheckRowCard(
                title = "Notificaciones",
                subtitle = "Ver el progreso y pausar desde la barra.",
                isReady = notificationsGranted,
                pendingIcon = Icons.Default.Notifications,
                isDarkTheme = isDarkTheme,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                actionLabel = "Permitir",
                onClick = { if (!notificationsGranted) onRequestNotifications() }
            )

            // 2. Tarjeta: Batería sin restricciones
            PermissionCheckRowCard(
                title = "Batería sin restricciones",
                subtitle = "Evita que el sistema detenga la descarga.",
                isReady = batteryExempt,
                pendingIcon = Icons.Default.BatteryAlert,
                isDarkTheme = isDarkTheme,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                actionLabel = "Permitir",
                onClick = { if (!batteryExempt) onRequestBatteryExemption() }
            )

            // 3. Tarjeta: Inicio automático
            PermissionCheckRowCard(
                title = "Inicio automático",
                subtitle = "Algunos teléfonos lo piden además.",
                isReady = autoStartConfigured,
                pendingIcon = Icons.Default.Settings,
                isDarkTheme = isDarkTheme,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                actionLabel = "Configurar",
                onClick = onOpenAutoStart
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Botón Principal ("Listo" si todo está completo, "Activar pendientes" si faltan)
            Button(
                onClick = {
                    if (allReady) {
                        onAllReady()
                    } else {
                        when {
                            !notificationsGranted -> onRequestNotifications()
                            !batteryExempt -> onRequestBatteryExemption()
                            !autoStartConfigured -> onOpenAutoStart()
                            else -> onAllReady()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (allReady) "Listo" else "Activar pendientes",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Pie con enlace de texto: "Ahora no"
            TextButton(
                onClick = { onDismissClick(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                Text(
                    text = "Ahora no",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary
                )
            }
        }
    }
}

/**
 * Fila interactiva para cada uno de los 3 permisos/ajustes.
 */
@Composable
fun PermissionCheckRowCard(
    title: String,
    subtitle: String,
    isReady: Boolean,
    pendingIcon: ImageVector,
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    actionLabel: String,
    onClick: () -> Unit
) {
    val cardBg = if (isReady) {
        if (isDarkTheme) Color(0xFF0F2324) else Color(0xFFF0FDF4)
    } else {
        if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    }

    val cardBorder = if (isReady) {
        Color(0xFF34D399).copy(alpha = if (isDarkTheme) 0.50f else 0.40f)
    } else {
        if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.2.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icono Izquierdo
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isReady) {
                            if (isDarkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.22f else 0.12f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isReady) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Listo",
                        tint = if (isDarkTheme) Color(0xFF34D399) else Color(0xFF059669),
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = pendingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Textos centrales
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = textSecondary,
                    lineHeight = 17.sp
                )
            }

            // Indicador / Botón derecho
            if (isReady) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDarkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completado",
                        tint = if (isDarkTheme) Color(0xFF34D399) else Color(0xFF059669),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.25f else 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
