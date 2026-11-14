package com.aistudio.youthverse.rvzkmq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.youthverse.rvzkmq.data.repository.AuthRepository
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.SocialRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.auth.AuthFlow
import com.aistudio.youthverse.rvzkmq.ui.auth.AuthLoadingScreen
import com.aistudio.youthverse.rvzkmq.ui.auth.AuthState
import com.aistudio.youthverse.rvzkmq.ui.auth.AuthViewModel
import com.aistudio.youthverse.rvzkmq.ui.navigation.MainAppNavigation
import com.aistudio.youthverse.rvzkmq.ui.theme.AppThemeMode
import com.aistudio.youthverse.rvzkmq.ui.theme.YouthVerseTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (FirebaseApp.getApps(this).isEmpty()) {
      runCatching { FirebaseApp.initializeApp(this) }
    }
    val appCheck = FirebaseAppCheck.getInstance()
    if (BuildConfig.DEBUG) appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    else appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())

    val userRepository = UserRepository(this)
    val authRepository = AuthRepository(userRepository)
    val contentRepository = ContentRepository(this)
    val libraryRepository = LibraryRepository(this)
    val socialRepository = SocialRepository(this)
    val credentialManager = CredentialManager.create(this)

    val prefs = getSharedPreferences("youthverse_prefs", MODE_PRIVATE)
    val initialThemeMode = runCatching {
      AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name)
    }.getOrDefault(AppThemeMode.DARK)

    setContent {
      var currentThemeMode by remember { mutableStateOf(initialThemeMode) }
      val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(authRepository))
      val authState by authViewModel.authState.collectAsStateWithLifecycle()
      val authNotice by authViewModel.authNotice.collectAsStateWithLifecycle()

      YouthVerseTheme(themeMode = currentThemeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
          when (val state = authState) {
            is AuthState.Loading -> {
              AuthLoadingScreen()
            }
            is AuthState.SignedOut,
            is AuthState.SigningIn,
            is AuthState.SigningUp,
            is AuthState.Error -> {
              AuthFlow(
                authViewModel = authViewModel,
                authState = state
              )
            }
            is AuthState.SignedIn -> {
              val uid = state.user.uid
              key(uid) {
                MainAppNavigation(
                  userRepository = userRepository,
                  contentRepository = contentRepository,
                  libraryRepository = libraryRepository,
                  socialRepository = socialRepository,
                  currentThemeMode = currentThemeMode,
                  onThemeChange = { mode ->
                    currentThemeMode = mode
                    prefs.edit().putString("theme_mode", mode.name).apply()
                  },
                  onSignOut = {
                    authViewModel.signOut(credentialManager)
                  },
                  authNotice = authNotice,
                  onDismissAuthNotice = { authViewModel.clearAuthNotice() }
                )
              }
            }
          }
        }
      }
    }
  }
}
