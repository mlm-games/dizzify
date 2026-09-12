package app.dizzify.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dizzify.LauncherViewModel
import app.dizzify.ui.theme.LauncherColors
import app.dizzify.ui.theme.LauncherSpacing
import timber.log.Timber

@Composable
fun AppDetailsScreen(
    appKey: String,
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appsAll by viewModel.appsAll.collectAsState()
    
    val app = remember(appKey, appsAll) {
        appsAll.find { it.getKey() == appKey }
    }

    BackHandler { onBack() }

    if (app == null) {
        // App not found, go back
        LaunchedEffect(Unit) {
            Timber.w("App with key $appKey not found")
            onBack()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LauncherColors.DarkSurface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = LauncherColors.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(LauncherSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(LauncherSpacing.lg)
        ) {
            // App Icon and Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LauncherColors.DarkSurface, RoundedCornerShape(12.dp))
                    .padding(LauncherSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.md)
            ) {
                app.appIcon?.let { icon ->
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                }
                
                Column {
                    Text(
                        text = app.appLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = app.appPackage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LauncherColors.TextSecondary
                    )
                }
            }

            // App Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = LauncherColors.DarkSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(LauncherSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(LauncherSpacing.md)
                ) {
                    Text(
                        text = "Information",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    
                    InfoRow("Package Name", app.appPackage)
                    InfoRow("Activity", app.activityClassName ?: "N/A")
                    InfoRow("User", app.userString)
                    InfoRow("Hidden", if (app.isHidden) "Yes" else "No")
                }
            }

            // Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = LauncherColors.DarkSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(LauncherSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(LauncherSpacing.sm)
                ) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )

                    // App Info Button
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.appPackage, null)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to open app info for ${app.appPackage}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LauncherColors.AccentBlue
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open App Info")
                    }

                    // Uninstall Button
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.fromParts("package", app.appPackage, null)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to uninstall ${app.appPackage}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Red
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LauncherColors.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
