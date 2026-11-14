package com.aistudio.youthverse.rvzkmq.ui.navigation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.aistudio.youthverse.rvzkmq.data.repository.ContentRepository
import com.aistudio.youthverse.rvzkmq.data.repository.LibraryRepository
import com.aistudio.youthverse.rvzkmq.data.repository.SocialRepository
import com.aistudio.youthverse.rvzkmq.data.repository.UserRepository
import com.aistudio.youthverse.rvzkmq.ui.admin.AdminChapterEditScreen
import com.aistudio.youthverse.rvzkmq.ui.admin.AdminContentEditScreen
import com.aistudio.youthverse.rvzkmq.ui.admin.AdminDashboardScreen
import com.aistudio.youthverse.rvzkmq.ui.detail.ContentDetailScreen
import com.aistudio.youthverse.rvzkmq.ui.explore.ExploreScreen
import com.aistudio.youthverse.rvzkmq.ui.home.HomeScreen
import com.aistudio.youthverse.rvzkmq.ui.library.LibraryScreen
import com.aistudio.youthverse.rvzkmq.ui.profile.ProfileScreen
import com.aistudio.youthverse.rvzkmq.ui.reader.MangaReaderScreen
import com.aistudio.youthverse.rvzkmq.ui.reader.NovelReaderScreen
import com.aistudio.youthverse.rvzkmq.ui.search.SearchScreen
import com.aistudio.youthverse.rvzkmq.ui.theme.AppThemeMode
import com.aistudio.youthverse.rvzkmq.ui.theme.CyanAccent
import com.aistudio.youthverse.rvzkmq.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Explore : Screen("explore")
    object Library : Screen("library")
    object Profile : Screen("profile")
    object Search : Screen("search")
    data class ContentDetail(val contentId: String) : Screen("detail/$contentId")
    data class NovelReader(val contentId: String, val chapterId: String) : Screen("novel_reader/$contentId/$chapterId")
    data class MangaReader(val contentId: String, val chapterId: String) : Screen("manga_reader/$contentId/$chapterId")
    object AdminDashboard : Screen("admin_dashboard")
    data class AdminContentEdit(val contentId: String? = null) : Screen("admin_content_edit")
    data class AdminChapterEdit(val contentId: String, val chapterId: String? = null) : Screen("admin_chapter_edit")
}

@Composable
fun MainAppNavigation(
    userRepository: UserRepository,
    contentRepository: ContentRepository,
    libraryRepository: LibraryRepository,
    socialRepository: SocialRepository,
    currentThemeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onSignOut: () -> Unit,
    authNotice: String? = null,
    onDismissAuthNotice: () -> Unit = {}
) {
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isOpeningReader by remember { mutableStateOf(false) }

    LaunchedEffect(authNotice) {
        if (!authNotice.isNullOrBlank()) {
            snackbarHostState.showSnackbar(authNotice)
            onDismissAuthNotice()
        }
    }

    fun navigateTo(screen: Screen) {
        backStack = backStack + screen
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }
    }

    fun switchTab(screen: Screen) {
        backStack = listOf(screen)
    }

    fun openReaderForContent(contentId: String, chapterId: String) {
        if (isOpeningReader) return
        isOpeningReader = true
        coroutineScope.launch {
            try {
                val result = contentRepository.getContent(contentId)
                val content = result.getOrNull()
                if (content == null) {
                    val err = result.exceptionOrNull()
                    Log.e("YouthVerseNav", "stage=NAVIGATION_OPEN_READER status=FAILED category=NAVIGATION_FAILURE errorType=${err?.javaClass?.simpleName ?: "NOT_FOUND"}")
                    snackbarHostState.showSnackbar("Unable to open chapter. Please check your connection and try again.")
                } else if (content.contentType in setOf("MANGA", "MANHWA", "MANHUA")) {
                    Log.d("YouthVerseNav", "stage=NAVIGATION_OPEN_READER status=SUCCESS target=MangaReader")
                    navigateTo(Screen.MangaReader(contentId, chapterId))
                } else {
                    Log.d("YouthVerseNav", "stage=NAVIGATION_OPEN_READER status=SUCCESS target=NovelReader")
                    navigateTo(Screen.NovelReader(contentId, chapterId))
                }
            } finally {
                isOpeningReader = false
            }
        }
    }

    // Handle system back button
    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    val isBottomBarVisible = currentScreen in listOf(Screen.Home, Screen.Explore, Screen.Library, Screen.Profile)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (currentThemeMode == AppThemeMode.LIGHT) VioletPrimary else CyanAccent,
                        selectedTextColor = if (currentThemeMode == AppThemeMode.LIGHT) VioletPrimary else CyanAccent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = VioletPrimary.copy(alpha = 0.2f)
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Home,
                        onClick = { switchTab(Screen.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Explore,
                        onClick = { switchTab(Screen.Explore) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                        label = { Text("Explore") },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_item_explore")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Library,
                        onClick = { switchTab(Screen.Library) },
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = "Library") },
                        label = { Text("Library") },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_item_library")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Profile,
                        onClick = { switchTab(Screen.Profile) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_item_profile")
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        userRepository = userRepository,
                        contentRepository = contentRepository,
                        libraryRepository = libraryRepository,
                        onNavigateToContent = { id -> navigateTo(Screen.ContentDetail(id)) },
                        onNavigateToReader = ::openReaderForContent,
                        onNavigateToSearch = { navigateTo(Screen.Search) },
                        onNavigateToAdminCreate = { navigateTo(Screen.AdminContentEdit(null)) }
                    )
                }
                is Screen.Explore -> {
                    ExploreScreen(
                        contentRepository = contentRepository,
                        onNavigateToContent = { id -> navigateTo(Screen.ContentDetail(id)) },
                        onNavigateToSearch = { navigateTo(Screen.Search) }
                    )
                }
                is Screen.Library -> {
                    LibraryScreen(
                        userRepository = userRepository,
                        contentRepository = contentRepository,
                        libraryRepository = libraryRepository,
                        onNavigateToContent = { id -> navigateTo(Screen.ContentDetail(id)) }
                    )
                }
                is Screen.Profile -> {
                    ProfileScreen(
                        userRepository = userRepository,
                        currentThemeMode = currentThemeMode,
                        onThemeChange = onThemeChange,
                        onNavigateToAdmin = { navigateTo(Screen.AdminDashboard) },
                        onSignOut = onSignOut
                    )
                }
                is Screen.Search -> {
                    SearchScreen(
                        contentRepository = contentRepository,
                        onNavigateToContent = { id -> navigateTo(Screen.ContentDetail(id)) },
                        onBackClick = { navigateBack() }
                    )
                }
                is Screen.ContentDetail -> {
                    ContentDetailScreen(
                        contentId = currentScreen.contentId,
                        userRepository = userRepository,
                        contentRepository = contentRepository,
                        libraryRepository = libraryRepository,
                        socialRepository = socialRepository,
                        onNavigateToReader = ::openReaderForContent,
                        onNavigateToAdminEdit = { id -> navigateTo(Screen.AdminContentEdit(id)) },
                        onNavigateToAdminAddChapter = { id -> navigateTo(Screen.AdminChapterEdit(id, null)) },
                        onBackClick = { navigateBack() }
                    )
                }
                is Screen.NovelReader -> {
                    NovelReaderScreen(
                        contentId = currentScreen.contentId,
                        initialChapterId = currentScreen.chapterId,
                        userRepository = userRepository,
                        contentRepository = contentRepository,
                        libraryRepository = libraryRepository,
                        onBackClick = { navigateBack() }
                    )
                }
                is Screen.MangaReader -> {
                    MangaReaderScreen(
                        contentId = currentScreen.contentId,
                        initialChapterId = currentScreen.chapterId,
                        userRepository = userRepository,
                        contentRepository = contentRepository,
                        libraryRepository = libraryRepository,
                        onBackClick = { navigateBack() }
                    )
                }
                is Screen.AdminDashboard -> {
                    AdminDashboardScreen(
                        contentRepository = contentRepository,
                        onNavigateToCreateContent = { navigateTo(Screen.AdminContentEdit(null)) },
                        onNavigateToEditContent = { id -> navigateTo(Screen.AdminContentEdit(id)) },
                        onNavigateToAddChapter = { id -> navigateTo(Screen.AdminChapterEdit(id, null)) },
                        onBackClick = { navigateBack() }
                    )
                }
                is Screen.AdminContentEdit -> {
                    AdminContentEditScreen(
                        contentId = currentScreen.contentId,
                        contentRepository = contentRepository,
                        onSuccess = { navigateBack() },
                        onBackClick = { navigateBack() }
                    )
                }
                is Screen.AdminChapterEdit -> {
                    AdminChapterEditScreen(
                        contentId = currentScreen.contentId,
                        chapterId = currentScreen.chapterId,
                        contentRepository = contentRepository,
                        onSuccess = { navigateBack() },
                        onBackClick = { navigateBack() }
                    )
                }
            }
        }
    }
}
