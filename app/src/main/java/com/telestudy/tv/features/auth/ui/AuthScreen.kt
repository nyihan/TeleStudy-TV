package com.telestudy.tv.features.auth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telestudy.tv.core.device.DeviceType
import com.telestudy.tv.core.tdlib.AuthState
import com.telestudy.tv.features.auth.AuthTab
import com.telestudy.tv.features.auth.AuthUiState
import com.telestudy.tv.features.auth.AuthViewModel

private val BackgroundDark = Color(0xFF0F141C)
private val SurfaceDark = Color(0xFF1B2230)
private val AccentGold = Color(0xFFFFD54F)
private val BluePrimary = Color(0xFF3B82F6)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val ErrorRed = Color(0xFFEF4444)
private val SuccessGreen = Color(0xFF22C55E)

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    deviceType: DeviceType,
    onAuthSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.authState) {
        if (uiState.authState is AuthState.Ready) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (deviceType == DeviceType.TV) {
            TvAuthScreen(viewModel = viewModel, uiState = uiState)
        } else {
            PhoneAuthScreen(viewModel = viewModel, uiState = uiState)
        }
    }
}

@Composable
private fun PhoneAuthScreen(
    viewModel: AuthViewModel,
    uiState: AuthUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Branding
        Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            tint = AccentGold,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "TeleStudy TV",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Log in to browse private educational channels",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tabs: Phone Number vs QR Code
        if (uiState.authState !is AuthState.WaitCode && uiState.authState !is AuthState.WaitPassword && uiState.authState !is AuthState.Ready) {
            TabRow(
                selectedTabIndex = if (uiState.selectedTab == AuthTab.PHONE) 0 else 1,
                containerColor = SurfaceDark,
                contentColor = AccentGold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = uiState.selectedTab == AuthTab.PHONE,
                    onClick = { viewModel.selectTab(AuthTab.PHONE) },
                    text = { Text("Phone Login", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = uiState.selectedTab == AuthTab.QR,
                    onClick = { viewModel.selectTab(AuthTab.QR) },
                    text = { Text("QR Code Login", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Error Banner
        if (uiState.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = uiState.errorMessage, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Content by auth state or tab
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    uiState.authState is AuthState.Ready -> {
                        AuthorizedCardContent(uiState = uiState, onLogOut = { viewModel.logOut() })
                    }
                    uiState.authState is AuthState.WaitPassword -> {
                        WaitPasswordContent(viewModel = viewModel, uiState = uiState)
                    }
                    uiState.authState is AuthState.WaitCode -> {
                        WaitCodeContent(viewModel = viewModel, uiState = uiState)
                    }
                    uiState.selectedTab == AuthTab.QR -> {
                        QrLoginContent(viewModel = viewModel, uiState = uiState)
                    }
                    else -> {
                        PhoneInputContent(viewModel = viewModel, uiState = uiState)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAuthScreen(
    viewModel: AuthViewModel,
    uiState: AuthUiState
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Branding and instructions
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = AccentGold, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("TeleStudy TV", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Educational Video Player for Telegram",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = AccentGold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log in to stream private subject lessons directly on your TV. All video content is securely streamed through your own Telegram account.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Navigation selector on TV
            if (uiState.authState !is AuthState.WaitCode && uiState.authState !is AuthState.WaitPassword && uiState.authState !is AuthState.Ready) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = "Scan QR Code",
                        icon = Icons.Default.QrCode,
                        isSelected = uiState.selectedTab == AuthTab.QR,
                        onClick = { viewModel.selectTab(AuthTab.QR) }
                    )
                    TvButton(
                        text = "Phone Number",
                        icon = Icons.Default.Phone,
                        isSelected = uiState.selectedTab == AuthTab.PHONE,
                        onClick = { viewModel.selectTab(AuthTab.PHONE) }
                    )
                }
            }
        }

        // Right Column: Active authentication form or QR
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                when {
                    uiState.authState is AuthState.Ready -> {
                        AuthorizedCardContent(uiState = uiState, onLogOut = { viewModel.logOut() })
                    }
                    uiState.authState is AuthState.WaitPassword -> {
                        WaitPasswordContent(viewModel = viewModel, uiState = uiState)
                    }
                    uiState.authState is AuthState.WaitCode -> {
                        WaitCodeContent(viewModel = viewModel, uiState = uiState)
                    }
                    uiState.selectedTab == AuthTab.QR -> {
                        QrLoginContent(viewModel = viewModel, uiState = uiState)
                    }
                    else -> {
                        PhoneInputContent(viewModel = viewModel, uiState = uiState)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneInputContent(
    viewModel: AuthViewModel,
    uiState: AuthUiState
) {
    Text(
        text = "Enter Phone Number",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Include country code (e.g. +1 555 123 4567)",
        fontSize = 13.sp,
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = uiState.phoneNumber,
        onValueChange = { viewModel.onPhoneNumberChanged(it) },
        label = { Text("Phone Number") },
        placeholder = { Text("+1234567890") },
        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = AccentGold) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.submitPhoneNumber() }),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGold,
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )

    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = { viewModel.submitPhoneNumber() },
        enabled = !uiState.isLoading && uiState.phoneNumber.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text("Send Code", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun WaitCodeContent(
    viewModel: AuthViewModel,
    uiState: AuthUiState
) {
    Text(
        text = "Enter Verification Code",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Check your Telegram app on another device for the login code",
        fontSize = 13.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = uiState.code,
        onValueChange = { viewModel.onCodeChanged(it) },
        label = { Text("5-digit Code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.submitCode() }),
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 6.sp,
            color = TextPrimary
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGold,
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = { viewModel.submitCode() },
        enabled = !uiState.isLoading && uiState.code.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text("Verify Code", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (uiState.resendCountdown > 0) {
        Text(
            text = "Resend code in ${uiState.resendCountdown}s",
            color = TextSecondary,
            fontSize = 13.sp
        )
    } else {
        TextButton(onClick = { viewModel.resendCode() }) {
            Text("Resend Code", color = AccentGold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WaitPasswordContent(
    viewModel: AuthViewModel,
    uiState: AuthUiState
) {
    val passwordState = uiState.authState as? AuthState.WaitPassword

    Text(
        text = "Two-Step Verification",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Your Telegram account is protected by an additional cloud password.",
        fontSize = 13.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center
    )

    if (!passwordState?.passwordHint.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Password Hint: ${passwordState?.passwordHint}",
            fontSize = 12.sp,
            color = AccentGold,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = uiState.password,
        onValueChange = { viewModel.onPasswordChanged(it) },
        label = { Text("2FA Password") },
        singleLine = true,
        visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.submitPassword() }),
        trailingIcon = {
            IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                Icon(
                    imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility",
                    tint = TextSecondary
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGold,
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = { viewModel.submitPassword() },
        enabled = !uiState.isLoading && uiState.password.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text("Verify Password", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun QrLoginContent(
    viewModel: AuthViewModel,
    uiState: AuthUiState
) {
    Text(
        text = "Quick QR Code Login",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Scan with Telegram: Settings > Devices > Link Desktop Device",
        fontSize = 12.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))

    val bitmap = uiState.qrBitmap
    if (bitmap != null) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(4.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Telegram Login QR Code",
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = AccentGold)
            } else {
                Text(text = "Requesting QR...", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = { viewModel.requestQrCode() },
        enabled = !uiState.isLoading,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Refresh QR Code", fontSize = 13.sp)
    }
}

@Composable
private fun AuthorizedCardContent(
    uiState: AuthUiState,
    onLogOut: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = SuccessGreen,
        modifier = Modifier.size(56.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Telegram Account Connected",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))

    val user = uiState.currentUser
    val name = listOfNotNull(user?.firstName, user?.lastName).joinToString(" ")
    Text(
        text = if (name.isNotBlank()) name else "User ID: ${user?.id ?: 0}",
        fontSize = 15.sp,
        color = AccentGold,
        fontWeight = FontWeight.SemiBold
    )
    if (!user?.usernames?.activeUsernames.isNullOrEmpty()) {
        Text(
            text = "@${user?.usernames?.activeUsernames?.firstOrNull()}",
            fontSize = 13.sp,
            color = TextSecondary
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onLogOut,
        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Log Out", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) BluePrimary else SurfaceDark
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) AccentGold else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (isSelected) Color.White else AccentGold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
