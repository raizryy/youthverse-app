package com.aistudio.youthverse.rvzkmq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.SocialRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.auth.AuthScreen
import com.aistudio.youthverse.rvzkmq.ui.navigation.MainAppNavigation
import com.aistudio.youthverse.rvzkmq.ui.theme.AppThemeMode
import com.aistudio.youthverse.rvzkmq.ui.theme.YouthVerseTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appCheck = FirebaseAppCheck.getInstance()
    if (BuildConfig.DEBUG) appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    else appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())

    val userRepository = UserRepository(this)
    val contentRepository = ContentRepository(this)
    val libraryRepository = LibraryRepository(this)
    val socialRepository = SocialRepository(this)

    val prefs = getSharedPreferences("youthverse_prefs", MODE_PRIVATE)
    val initialThemeMode = runCatching {
      AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name)
    }.getOrDefault(AppThemeMode.DARK)

    setContent {
      var currentThemeMode by remember { mutableStateOf(initialThemeMode) }
      var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
      val coroutineScope = rememberCoroutineScope()

      DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
          currentUser = auth.currentUser
          if (auth.currentUser != null) {
            coroutineScope.launch {
              userRepository.getOrCreateProfile()
            }
          }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
          Firebase.auth.removeAuthStateListener(listener)
        }
      }

      YouthVerseTheme(themeMode = currentThemeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
          if (currentUser == null) {
            AuthScreen(
              onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
              }
            )
          } else {
            val uid = currentUser!!.uid
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
                  currentUser = null
                }
              )
            }
          }
        }
      }
    }
  }
}
