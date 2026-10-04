package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.repository.VaultRepository
import com.example.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.example.R

enum class SortMode {
    NEW,              // New: by scene date (assignedDate ?: createdAt descending)
    OLD,              // Old: by scene date (assignedDate ?: createdAt ascending)
    RECENTLY_ADDED,   // Recently Added: by date added (createdAt descending)
    OLDEST_ADDED      // Oldest Added: by date added (createdAt ascending)
}

enum class ManagementSortOption {
    NAME_AZ,          // A - Z (name.lowercase() ascending)
    NAME_ZA,          // Z - A (name.lowercase() descending)
    RECENTLY_ADDED,   // Recently Added (createdAt descending)
    OLDEST_ADDED      // Oldest Added (createdAt ascending)
}

enum class StashSearchType {
    ACTORS,
    STUDIO,
    SEXMEX
}

enum class SettingsSection {
    MAIN_MENU,
    DISPLAY,
    PRIVACY, // ORG-NEW
    INTEGRATIONS,
    FILTER,
    DATA_BACKUP,
    SAMPLE_DATA
}

sealed class ScreenState {
    object Home : ScreenState()
    object Bookmarks : ScreenState()
    data class AddEditLink(val linkId: String? = null) : ScreenState()
    object Actors : ScreenState()
    data class AddEditActor(val actorId: String? = null) : ScreenState()
    data class ActorScenes(val actorId: String) : ScreenState()
    object Studios : ScreenState()
    data class AddEditStudio(val studioId: String? = null) : ScreenState()
    data class StudioScenes(val studioId: String) : ScreenState()
    object StashDb : ScreenState()
    object Settings : ScreenState()
}

data class ActiveVideoPlayback(
    val title: String,
    val qualities: List<StreamQuality>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val initialPositionMs: Long = 0L,
    val startInLandscape: Boolean = false
)

data class ActiveInlineVideoPlayback(
    val cardId: String,
    val title: String,
    val qualities: List<StreamQuality>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val headers: Map<String, String> = emptyMap()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository: VaultRepository

    private val _isSplashLoading = MutableStateFlow(true)
    val isSplashLoading: StateFlow<Boolean> = _isSplashLoading.asStateFlow()

    private val _splashProgress = MutableStateFlow(0.15f)
    val splashProgress: StateFlow<Float> = _splashProgress.asStateFlow()

    private val _splashStatus = MutableStateFlow("Initializing app & resources...")
    val splashStatus: StateFlow<String> = _splashStatus.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        repository = VaultRepository(db)
        seedInitialDataIfEmpty()
    }

    private fun seedInitialDataIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            _splashProgress.value = 0.20f
            _splashStatus.value = "Setting up database & preferences..."
            kotlinx.coroutines.delay(250L)

            val existingLinks = repository.allLinks.first()
            
            // Delete obsolete demo IDs if present
            val oldSceneIds = listOf(
                "scene_raissa_stepmom", "scene_raissa_double", "scene_raissa_vip",
                "test_scene_1", "test_scene_2", "test_scene_3", "test_scene_4", "test_scene_5",
                "test_scene_6", "test_scene_7", "test_scene_8", "test_scene_9", "test_scene_10",
                "test_scene_11", "test_scene_12", "test_scene_13", "test_scene_14", "test_scene_15",
                "test_scene_16", "test_scene_17", "test_scene_18", "test_scene_19", "test_scene_20"
            )
            oldSceneIds.forEach { oldId ->
                repository.deleteLinkById(oldId)
            }
            existingLinks.filter { it.id.startsWith("demo_") || it.id.startsWith("test_scene_") }.forEach {
                repository.deleteLinkById(it.id)
            }

            _splashProgress.value = 0.50f
            _splashStatus.value = "Verifying API keys & integrations..."
            kotlinx.coroutines.delay(250L)

            // Seed default API Keys ONLY ONCE on first install using defaultKeysSeeded flag
            val currentSett = repository.settings.first() ?: SettingsEntity()
            if (!currentSett.defaultKeysSeeded) {
                var updatedSett = currentSett.copy(defaultKeysSeeded = true)
                if (updatedSett.realDebridApiKey.isBlank()) {
                    updatedSett = updatedSett.copy(realDebridApiKey = com.example.data.TestDefaults.RD_KEY)
                }
                if (updatedSett.stashDbApiKey.isBlank()) {
                    updatedSett = updatedSett.copy(stashDbApiKey = com.example.data.TestDefaults.STASHDB_KEY)
                }
                repository.updateSettings(updatedSett)
            }

            _splashProgress.value = 0.80f
            _splashStatus.value = "Pre-warming assets & video engine..."

            // Pre-warm and cache complete button system assets, full UI drawables (52/52), and Lexend font
            try {
                com.example.ui.components.ResourcePreWarmer.preWarmAll(app)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Pre-warming error", e)
            }

            kotlinx.coroutines.delay(250L)

            // Insert sample test dataset scenes, actors, and studios into the database atomically if empty
            val existingLinkIds = existingLinks.map { it.id }.toSet()
            val existingActors = repository.allActors.first()
            if (existingActors.isEmpty()) {
                repository.insertActors(com.example.data.util.SampleTestDataset.sampleActors)
            }
            val existingStudios = repository.allStudios.first()
            if (existingStudios.isEmpty()) {
                repository.insertStudios(com.example.data.util.SampleTestDataset.sampleStudios)
            }
            val missingSampleScenes = com.example.data.util.SampleTestDataset.sampleScenes.filter { it.id !in existingLinkIds }
            if (missingSampleScenes.isNotEmpty()) {
                repository.insertLinks(missingSampleScenes)
            }

            _splashProgress.value = 1.0f
            _splashStatus.value = "Ready!"
            kotlinx.coroutines.delay(450L)

            _isSplashLoading.value = false
        }
    }

    // Navigation Stack / Current Screen & Direction
    enum class NavigationDirection {
        FORWARD, BACK
    }

    private val _navDirection = MutableStateFlow(NavigationDirection.FORWARD)
    val navDirection: StateFlow<NavigationDirection> = _navDirection.asStateFlow()

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Home)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val screenStack = mutableListOf<ScreenState>(ScreenState.Home)

    // Comprehensive Multi-Key Scroll Position Memory Registry
    private val scrollPositionRegistry = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositionRegistry[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> {
        return scrollPositionRegistry[key] ?: Pair(0, 0)
    }

    fun clearScrollPosition(key: String) {
        scrollPositionRegistry.remove(key)
    }

    // Home Feed Scroll Position Memory Proxies for complete backward compatibility
    var homeScrollIndex: Int
        get() = getScrollPosition("feed_home").first
        set(value) {
            val currentOffset = getScrollPosition("feed_home").second
            saveScrollPosition("feed_home", value, currentOffset)
        }

    var homeScrollOffset: Int
        get() = getScrollPosition("feed_home").second
        set(value) {
            val currentIndex = getScrollPosition("feed_home").first
            saveScrollPosition("feed_home", currentIndex, value)
        }

    var initialSettingsSection: String? = null

    fun navigateTo(screen: ScreenState) {
        if (screen == _screenState.value) return
        val existingIndex = screenStack.indexOf(screen)
        if (existingIndex >= 0 && existingIndex < screenStack.size - 1) {
            _navDirection.value = NavigationDirection.BACK
            while (screenStack.size > existingIndex + 1) {
                screenStack.removeAt(screenStack.size - 1)
            }
        } else {
            _navDirection.value = NavigationDirection.FORWARD
            screenStack.add(screen)
        }
        _screenState.value = screen
    }

    fun navigateBack(): Boolean {
        if (screenStack.size > 1) {
            _navDirection.value = NavigationDirection.BACK
            screenStack.removeAt(screenStack.size - 1)
            _screenState.value = screenStack.last()
            return true
        } else if (_screenState.value != ScreenState.Home) {
            _navDirection.value = NavigationDirection.BACK
            screenStack.clear()
            screenStack.add(ScreenState.Home)
            _screenState.value = ScreenState.Home
            return true
        }
        return false
    }

    // Active Video Player Overlay State (Full Screen / Landscape)
    private val _activeVideo = MutableStateFlow<ActiveVideoPlayback?>(null)
    val activeVideo: StateFlow<ActiveVideoPlayback?> = _activeVideo.asStateFlow()

    // Active Inline Video Card State (Embedded 16:9 Cover Player)
    private val _activeInlineVideo = MutableStateFlow<ActiveInlineVideoPlayback?>(null)
    val activeInlineVideo: StateFlow<ActiveInlineVideoPlayback?> = _activeInlineVideo.asStateFlow()

    // Shared ExoPlayer Manager for seamless transition without stopping video
    val sharedPlayerManager by lazy { SharedPlayerManager(application) }
    private var lastInlineCardId: String? = null

    // Video Resolution Loading & Error States
    private val _resolvingCardId = MutableStateFlow<String?>(null)
    val resolvingCardId: StateFlow<String?> = _resolvingCardId.asStateFlow()

    private val _resolvingVideoStatus = MutableStateFlow<String?>(null)
    val resolvingVideoStatus: StateFlow<String?> = _resolvingVideoStatus.asStateFlow()

    private val _videoResolutionError = MutableStateFlow<String?>(null)
    val videoResolutionError: StateFlow<String?> = _videoResolutionError.asStateFlow()

    private var resolveVideoJob: kotlinx.coroutines.Job? = null

    fun playVideo(rawUrl: String, title: String = "Media Stream", cardId: String? = null) {
        resolveVideoJob?.cancel()
        _videoResolutionError.value = null
        _resolvingCardId.value = cardId

        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            _videoResolutionError.value = "Cannot play empty stream URL."
            _resolvingCardId.value = null
            return
        }

        resolveVideoJob = viewModelScope.launch {
            _resolvingVideoStatus.value = if (com.example.network.torrent.MagnetParser.parseHash(trimmed) != null) {
                "Resolving torrent magnet via Debrid..."
            } else {
                "Resolving media stream..."
            }

            try {
                val settings = repository.getSettingsOnce()
                val orderEnum = when (settings.debridOrder) {
                    "REAL_DEBRID_FIRST" -> com.example.network.debrid.DebridOrder.REAL_DEBRID_FIRST
                    "TORBOX_FIRST" -> com.example.network.debrid.DebridOrder.TORBOX_FIRST
                    else -> com.example.network.debrid.DebridOrder.AUTO
                }

                val resolved = kotlinx.coroutines.withTimeout(45_000L) {
                    VideoResolvers.resolve(
                        rawUrl = trimmed,
                        torboxApiKey = settings.torboxApiKey,
                        realDebridApiKey = settings.realDebridApiKey,
                        debridOrder = orderEnum
                    )
                }

                if (resolved.qualities.isEmpty()) {
                    _videoResolutionError.value = "No playable media qualities found for this source."
                    _resolvingVideoStatus.value = null
                    return@launch
                }

                val primaryQuality = resolved.qualities.firstOrNull { it.isDefault } ?: resolved.qualities.first()
                _resolvingVideoStatus.value = "Verifying media stream..."

                val validation = MediaUrlValidator.validate(
                    primaryQuality.url,
                    resolved.headers + primaryQuality.headers
                )

                val displayTitle = if (resolved.title.isNotBlank() && resolved.title != "Media Stream") resolved.title else title

                when (validation) {
                    is ValidatedMediaResult.Valid -> {
                        if (cardId != null) {
                            // Play directly inside the 16:9 card cover
                            _activeInlineVideo.value = ActiveInlineVideoPlayback(
                                cardId = cardId,
                                title = displayTitle,
                                qualities = resolved.qualities,
                                subtitles = resolved.subtitles,
                                headers = resolved.headers
                            )
                        } else {
                            _activeVideo.value = ActiveVideoPlayback(
                                title = displayTitle,
                                qualities = resolved.qualities,
                                subtitles = resolved.subtitles,
                                headers = resolved.headers
                            )
                        }
                    }
                    is ValidatedMediaResult.Invalid -> {
                        // If it's a valid HTTP/HTTPS URL, don't abort playback on network probe failure
                        if (primaryQuality.url.startsWith("http://", ignoreCase = true) ||
                            primaryQuality.url.startsWith("https://", ignoreCase = true)
                        ) {
                            if (cardId != null) {
                                _activeInlineVideo.value = ActiveInlineVideoPlayback(
                                    cardId = cardId,
                                    title = displayTitle,
                                    qualities = resolved.qualities,
                                    subtitles = resolved.subtitles,
                                    headers = resolved.headers
                                )
                            } else {
                                _activeVideo.value = ActiveVideoPlayback(
                                    title = displayTitle,
                                    qualities = resolved.qualities,
                                    subtitles = resolved.subtitles,
                                    headers = resolved.headers
                                )
                            }
                        } else {
                            _videoResolutionError.value = validation.reason
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _videoResolutionError.value = e.message ?: "Failed to resolve media stream"
            } finally {
                _resolvingVideoStatus.value = null
                _resolvingCardId.value = null
            }
        }
    }

    fun dismissVideoError() {
        _videoResolutionError.value = null
    }

    fun closeVideo() {
        val currentVideo = _activeVideo.value
        val inlineId = lastInlineCardId
        _activeVideo.value = null

        if (inlineId != null && currentVideo != null) {
            // Smoothly return to Inline Video Player mode without stopping playback
            _activeInlineVideo.value = ActiveInlineVideoPlayback(
                cardId = inlineId,
                title = currentVideo.title,
                qualities = currentVideo.qualities,
                subtitles = currentVideo.subtitles,
                headers = currentVideo.headers
            )
            lastInlineCardId = null
        } else {
            sharedPlayerManager.stopPlayer()
        }
    }

    fun closeInlineVideo(cardId: String? = null) {
        if (cardId == null || _activeInlineVideo.value?.cardId == cardId) {
            _activeInlineVideo.value = null
            lastInlineCardId = null
            sharedPlayerManager.stopPlayer()
        }
    }

    var enteredPipFromInlineCard: Boolean = false
        private set

    fun enterPipFromInline(cardId: String, currentPositionMs: Long = 0L) {
        enteredPipFromInlineCard = true
        openFullscreenFromInline(cardId, currentPositionMs, startInLandscape = false)
    }

    fun returnToInlineFromPip() {
        if (enteredPipFromInlineCard) {
            enteredPipFromInlineCard = false
            closeVideo()
        }
    }

    fun openFullscreenFromInline(cardId: String, currentPositionMs: Long = 0L, startInLandscape: Boolean = true) {
        val inline = _activeInlineVideo.value ?: return
        if (inline.cardId == cardId) {
            lastInlineCardId = cardId
            _activeInlineVideo.value = null
            _activeVideo.value = ActiveVideoPlayback(
                title = inline.title,
                qualities = inline.qualities,
                subtitles = inline.subtitles,
                headers = inline.headers,
                initialPositionMs = currentPositionMs,
                startInLandscape = startInLandscape
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPlayerManager.release()
    }

    // Active Photoset Lightbox State
    private val _activeLightbox = MutableStateFlow<Pair<List<String>, Int>?>(null)
    val activeLightbox: StateFlow<Pair<List<String>, Int>?> = _activeLightbox.asStateFlow()

    fun openLightbox(images: List<String>, startIndex: Int = 0) {
        _activeLightbox.value = images to startIndex
    }

    fun closeLightbox() {
        _activeLightbox.value = null
    }

    // ==========================================
    // STASHDB PERSISTENT STATE & OPERATIONS
    // ==========================================
    private val _stashSearchQuery = MutableStateFlow("")
    val stashSearchQuery: StateFlow<String> = _stashSearchQuery.asStateFlow()

    private val _stashActiveType = MutableStateFlow(StashSearchType.ACTORS)
    val stashActiveType: StateFlow<StashSearchType> = _stashActiveType.asStateFlow()

    private val _stashPerformerResults = MutableStateFlow<List<StashPerformer>>(emptyList())
    val stashPerformerResults: StateFlow<List<StashPerformer>> = _stashPerformerResults.asStateFlow()

    private val _stashStudioResults = MutableStateFlow<List<StashStudio>>(emptyList())
    val stashStudioResults: StateFlow<List<StashStudio>> = _stashStudioResults.asStateFlow()

    private val _stashScenesList = MutableStateFlow<List<StashScene>>(emptyList())
    val stashScenesList: StateFlow<List<StashScene>> = _stashScenesList.asStateFlow()

    private val _stashSelectedPerformer = MutableStateFlow<StashPerformer?>(null)
    val stashSelectedPerformer: StateFlow<StashPerformer?> = _stashSelectedPerformer.asStateFlow()

    private val _stashSelectedStudio = MutableStateFlow<StashStudio?>(null)
    val stashSelectedStudio: StateFlow<StashStudio?> = _stashSelectedStudio.asStateFlow()

    private val _stashSelectedSceneIds = MutableStateFlow<Set<String>>(emptySet())
    val stashSelectedSceneIds: StateFlow<Set<String>> = _stashSelectedSceneIds.asStateFlow()

    private val _stashTotalScenesCount = MutableStateFlow(0)
    val stashTotalScenesCount: StateFlow<Int> = _stashTotalScenesCount.asStateFlow()

    private val _stashCurrentPage = MutableStateFlow(1)
    val stashCurrentPage: StateFlow<Int> = _stashCurrentPage.asStateFlow()

    private val _stashCanLoadMore = MutableStateFlow(false)
    val stashCanLoadMore: StateFlow<Boolean> = _stashCanLoadMore.asStateFlow()

    private val _isStashLoadingEntities = MutableStateFlow(false)
    val isStashLoadingEntities: StateFlow<Boolean> = _isStashLoadingEntities.asStateFlow()

    private val _isStashLoadingScenes = MutableStateFlow(false)
    val isStashLoadingScenes: StateFlow<Boolean> = _isStashLoadingScenes.asStateFlow()

    private val _isStashLoadingMore = MutableStateFlow(false)
    val isStashLoadingMore: StateFlow<Boolean> = _isStashLoadingMore.asStateFlow()

    private val _stashSearchError = MutableStateFlow<String?>(null)
    val stashSearchError: StateFlow<String?> = _stashSearchError.asStateFlow()

    private val _isStashSearchExpanded = MutableStateFlow(false)
    val isStashSearchExpanded: StateFlow<Boolean> = _isStashSearchExpanded.asStateFlow()

    private var stashSearchJob: kotlinx.coroutines.Job? = null
    private var stashScenesJob: kotlinx.coroutines.Job? = null
    private var stashActorQuery = ""
    private var stashStudioQuery = ""
    private var stashSexMexQuery = ""
    private var cachedActorScenes = emptyList<StashScene>()
    private var cachedStudioScenes = emptyList<StashScene>()
    private var cachedSexMexScenes = emptyList<StashScene>()
    private var cachedActorPerformerResults = emptyList<StashPerformer>()
    private var cachedStudioResults = emptyList<StashStudio>()
    private var cachedSexMexPerformerResults = emptyList<StashPerformer>()
    private var cachedActorPerformer: StashPerformer? = null
    private var cachedStudioStudio: StashStudio? = null
    private var cachedSexMexPerformer: StashPerformer? = null
    private var cachedActorTotalCount = 0
    private var cachedStudioTotalCount = 0
    private var cachedSexMexTotalCount = 0
    private var cachedActorCurrentPage = 1
    private var cachedStudioCurrentPage = 1
    private var cachedSexMexCurrentPage = 1
    private var cachedActorCanLoadMore = false
    private var cachedStudioCanLoadMore = false
    private var cachedSexMexCanLoadMore = false

    fun setStashSearchQuery(query: String) {
        _stashSearchQuery.value = query
        when (_stashActiveType.value) {
            StashSearchType.ACTORS -> stashActorQuery = query
            StashSearchType.STUDIO -> stashStudioQuery = query
            StashSearchType.SEXMEX -> stashSexMexQuery = query
        }
    }

    fun setStashSearchExpanded(expanded: Boolean) {
        _isStashSearchExpanded.value = expanded
    }

    fun setStashActiveType(type: StashSearchType, apiKey: String) {
        if (_stashActiveType.value == type) return

        // 1. Cache current state before switching
        when (_stashActiveType.value) {
            StashSearchType.ACTORS -> {
                cachedActorScenes = _stashScenesList.value
                cachedActorTotalCount = _stashTotalScenesCount.value
                cachedActorCurrentPage = _stashCurrentPage.value
                cachedActorCanLoadMore = _stashCanLoadMore.value
                cachedActorPerformer = _stashSelectedPerformer.value
                cachedActorPerformerResults = _stashPerformerResults.value
            }
            StashSearchType.STUDIO -> {
                cachedStudioScenes = _stashScenesList.value
                cachedStudioTotalCount = _stashTotalScenesCount.value
                cachedStudioCurrentPage = _stashCurrentPage.value
                cachedStudioCanLoadMore = _stashCanLoadMore.value
                cachedStudioStudio = _stashSelectedStudio.value
                cachedStudioResults = _stashStudioResults.value
            }
            StashSearchType.SEXMEX -> {
                cachedSexMexScenes = _stashScenesList.value
                cachedSexMexTotalCount = _stashTotalScenesCount.value
                cachedSexMexCurrentPage = _stashCurrentPage.value
                cachedSexMexCanLoadMore = _stashCanLoadMore.value
                cachedSexMexPerformer = _stashSelectedPerformer.value
                cachedSexMexPerformerResults = _stashPerformerResults.value
            }
        }

        _stashActiveType.value = type
        _stashSearchError.value = null
        _stashSelectedSceneIds.value = emptySet()

        // 2. Restore cached query, scenes, and entity selections for newly selected tab
        when (type) {
            StashSearchType.ACTORS -> {
                _stashSearchQuery.value = stashActorQuery
                _stashScenesList.value = cachedActorScenes
                _stashTotalScenesCount.value = cachedActorTotalCount
                _stashCurrentPage.value = cachedActorCurrentPage
                _stashCanLoadMore.value = cachedActorCanLoadMore
                _stashSelectedPerformer.value = cachedActorPerformer
                _stashPerformerResults.value = cachedActorPerformerResults
                _stashStudioResults.value = emptyList()
                _stashSelectedStudio.value = null
            }
            StashSearchType.STUDIO -> {
                _stashSearchQuery.value = stashStudioQuery
                _stashScenesList.value = cachedStudioScenes
                _stashTotalScenesCount.value = cachedStudioTotalCount
                _stashCurrentPage.value = cachedStudioCurrentPage
                _stashCanLoadMore.value = cachedStudioCanLoadMore
                _stashSelectedStudio.value = cachedStudioStudio
                _stashStudioResults.value = cachedStudioResults
                _stashPerformerResults.value = emptyList()
                _stashSelectedPerformer.value = null
            }
            StashSearchType.SEXMEX -> {
                _stashSearchQuery.value = stashSexMexQuery
                _stashScenesList.value = cachedSexMexScenes
                _stashTotalScenesCount.value = cachedSexMexTotalCount
                _stashCurrentPage.value = cachedSexMexCurrentPage
                _stashCanLoadMore.value = cachedSexMexCanLoadMore
                _stashSelectedPerformer.value = cachedSexMexPerformer
                _stashPerformerResults.value = cachedSexMexPerformerResults
                _stashStudioResults.value = emptyList()
                _stashSelectedStudio.value = null
            }
        }
    }

    fun toggleStashSceneSelection(sceneId: String) {
        val current = _stashSelectedSceneIds.value
        _stashSelectedSceneIds.value = if (current.contains(sceneId)) current - sceneId else current + sceneId
    }

    fun clearStashSelection() {
        _stashSelectedSceneIds.value = emptySet()
    }

    fun resetStashState() {
        stashSearchJob?.cancel()
        stashScenesJob?.cancel()
        stashActorQuery = ""
        stashStudioQuery = ""
        stashSexMexQuery = ""
        cachedActorScenes = emptyList()
        cachedStudioScenes = emptyList()
        cachedSexMexScenes = emptyList()
        cachedActorPerformerResults = emptyList()
        cachedStudioResults = emptyList()
        cachedSexMexPerformerResults = emptyList()
        cachedActorPerformer = null
        cachedStudioStudio = null
        cachedSexMexPerformer = null
        cachedActorTotalCount = 0
        cachedStudioTotalCount = 0
        cachedSexMexTotalCount = 0
        cachedActorCurrentPage = 1
        cachedStudioCurrentPage = 1
        cachedSexMexCurrentPage = 1
        cachedActorCanLoadMore = false
        cachedStudioCanLoadMore = false
        cachedSexMexCanLoadMore = false
        _stashSearchQuery.value = ""
        _isStashSearchExpanded.value = false
        _stashPerformerResults.value = emptyList()
        _stashStudioResults.value = emptyList()
        _stashScenesList.value = emptyList()
        _stashSelectedPerformer.value = null
        _stashSelectedStudio.value = null
        _stashSelectedSceneIds.value = emptySet()
        _stashSearchError.value = null
        _stashCurrentPage.value = 1
        _stashCanLoadMore.value = false
        _isStashLoadingEntities.value = false
        _isStashLoadingScenes.value = false
        _isStashLoadingMore.value = false
    }

    fun performStashSearch(apiKey: String, query: String? = null) {
        val q = (query ?: _stashSearchQuery.value).trim()
        if (q.isBlank()) return

        stashSearchJob?.cancel()
        stashScenesJob?.cancel()
        val searchTargetType = _stashActiveType.value

        stashSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingEntities.value = true
            _stashSearchError.value = null
            _stashSelectedPerformer.value = null
            _stashSelectedStudio.value = null
            _stashSelectedSceneIds.value = emptySet()
            _stashScenesList.value = emptyList()
            _stashCurrentPage.value = 1
            _stashCanLoadMore.value = false

            when (searchTargetType) {
                StashSearchType.ACTORS -> {
                    _stashStudioResults.value = emptyList()
                    val res = StashDbApiService.searchPerformers(q, apiKey)
                    res.onSuccess { rawPerformers ->
                        val sorted = rawPerformers.sortedWith(
                            compareByDescending<StashPerformer> { performer ->
                                val name = performer.name.trim().lowercase()
                                val aliases = performer.aliases.map { it.trim().lowercase() }
                                when {
                                    name == q.lowercase() -> 100
                                    aliases.contains(q.lowercase()) -> 90
                                    name.startsWith(q.lowercase()) -> 80
                                    aliases.any { it.startsWith(q.lowercase()) } -> 70
                                    name.contains(q.lowercase()) -> 60
                                    aliases.any { it.contains(q.lowercase()) } -> 50
                                    else -> 10
                                }
                            }.thenByDescending {
                                if (!it.imageUrl.isNullOrBlank()) 1 else 0
                            }.thenBy {
                                it.name.lowercase()
                            }
                        )
                        _stashPerformerResults.value = sorted
                        cachedActorPerformerResults = sorted
                        _isStashLoadingEntities.value = false
                        if (sorted.isNotEmpty()) {
                            selectStashPerformer(sorted.first(), apiKey)
                        }
                    }.onFailure { err ->
                        _stashSearchError.value = err.message ?: "Failed to search actors"
                        _stashPerformerResults.value = emptyList()
                        cachedActorPerformerResults = emptyList()
                        _isStashLoadingEntities.value = false
                    }
                }
                StashSearchType.STUDIO -> {
                    _stashPerformerResults.value = emptyList()
                    val res = StashDbApiService.searchStudios(q, apiKey)
                    res.onSuccess { rawStudios ->
                        val sorted = rawStudios.sortedWith(
                            compareByDescending<StashStudio> { studio ->
                                val name = studio.name.trim().lowercase()
                                when {
                                    name == q.lowercase() -> 100
                                    name.startsWith(q.lowercase()) -> 80
                                    name.contains(q.lowercase()) -> 60
                                    else -> 10
                                }
                            }.thenByDescending {
                                if (!it.logoUrl.isNullOrBlank()) 1 else 0
                            }.thenBy {
                                it.name.lowercase()
                            }
                        )
                        _stashStudioResults.value = sorted
                        cachedStudioResults = sorted
                        _isStashLoadingEntities.value = false
                        if (sorted.isNotEmpty()) {
                            selectStashStudio(sorted.first(), apiKey)
                        }
                    }.onFailure { err ->
                        _stashSearchError.value = err.message ?: "Failed to search studios"
                        _stashStudioResults.value = emptyList()
                        cachedStudioResults = emptyList()
                        _isStashLoadingEntities.value = false
                    }
                }
                StashSearchType.SEXMEX -> {
                    _stashStudioResults.value = emptyList()
                    try {
                        val result = com.example.network.SexMexScraper.searchSexMex(q)
                        _stashPerformerResults.value = result.models
                        cachedSexMexPerformerResults = result.models
                        _stashSelectedPerformer.value = result.models.firstOrNull()
                        cachedSexMexPerformer = result.models.firstOrNull()
                        _stashScenesList.value = result.scenes
                        cachedSexMexScenes = result.scenes
                        _stashTotalScenesCount.value = result.scenes.size
                        cachedSexMexTotalCount = result.scenes.size
                        _stashCurrentPage.value = 1
                        _stashCanLoadMore.value = false
                        _isStashLoadingEntities.value = false
                        _isStashLoadingScenes.value = false

                        // Speculatively prefetch remaining models in background for 0ms instant click
                        if (result.models.size > 1) {
                            viewModelScope.launch(Dispatchers.IO) {
                                result.models.drop(1).take(3).forEach { m ->
                                    try {
                                        com.example.network.SexMexScraper.scrapeSexMexPage(m.id)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        _stashSearchError.value = e.message ?: "Failed to search SexMex"
                        _stashPerformerResults.value = emptyList()
                        cachedSexMexPerformerResults = emptyList()
                        _isStashLoadingEntities.value = false
                        _isStashLoadingScenes.value = false
                    }
                }
            }
        }
    }

    fun exploreLatestSexMex() {
        stashSearchJob?.cancel()
        stashScenesJob?.cancel()
        stashSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingScenes.value = true
            _stashSearchError.value = null
            _stashPerformerResults.value = emptyList()
            cachedSexMexPerformerResults = emptyList()
            _stashSelectedPerformer.value = null
            cachedSexMexPerformer = null
            _stashSelectedSceneIds.value = emptySet()
            _stashScenesList.value = emptyList()
            _stashCurrentPage.value = 1
            _stashCanLoadMore.value = false
            _stashSearchQuery.value = ""
            stashSexMexQuery = ""
            try {
                val latest = com.example.network.SexMexScraper.getLatestSexMexScenes()
                _stashScenesList.value = latest
                _stashTotalScenesCount.value = latest.size
                cachedSexMexScenes = latest
                cachedSexMexTotalCount = latest.size
                _isStashLoadingScenes.value = false
            } catch (e: Exception) {
                _stashSearchError.value = e.message ?: "Failed to fetch latest SexMex scenes"
                _isStashLoadingScenes.value = false
            }
        }
    }

    private val sexmexCoverRefreshTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val sexmexRefreshSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    fun autoRefreshSexMexCoverIfNeeded(link: LinkEntity) {
        val sceneUrl = link.stashDbId?.trim() ?: return
        if (!sceneUrl.contains("sexmex.xxx", ignoreCase = true) && !sceneUrl.contains("sexmex.com", ignoreCase = true)) {
            return
        }

        val now = System.currentTimeMillis()
        val lastAttempt = sexmexCoverRefreshTimestamps[link.id] ?: 0L
        // Ultra-low resource consumption: Max 1 attempt per 10 minutes per link
        if (now - lastAttempt < 10 * 60 * 1000L) {
            return
        }
        sexmexCoverRefreshTimestamps[link.id] = now

        viewModelScope.launch(Dispatchers.IO) {
            sexmexRefreshSemaphore.acquire()
            try {
                val freshUrl = com.example.network.SexMexScraper.fetchFreshCoverUrl(sceneUrl)
                if (!freshUrl.isNullOrBlank() && freshUrl != link.coverImage) {
                    val updated = link.copy(coverImage = freshUrl)
                    repository.updateLink(updated)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to auto-refresh SexMex cover for ${link.id}", e)
            } finally {
                sexmexRefreshSemaphore.release()
            }
        }
    }

    fun isStudioBlocked(studioId: String?, studioName: String?): Boolean {
        val current = settings.value
        if (!current.enableStudioFilter) return false
        val sName = studioName?.trim()
        val sId = studioId?.trim()
        if (!sId.isNullOrBlank() && current.blockedStudioIds.contains(sId)) return true
        if (!sName.isNullOrBlank() && current.blockedStudioNames.any { it.equals(sName, ignoreCase = true) }) return true
        return false
    }

    fun blockStudio(studioId: String?, studioName: String?) {
        val name = studioName?.trim() ?: return
        if (name.isBlank()) return
        val current = settings.value
        val updatedNames = (current.blockedStudioNames + name).distinct()
        val updatedIds = if (!studioId.isNullOrBlank()) (current.blockedStudioIds + studioId.trim()).distinct() else current.blockedStudioIds
        updateSettings(current.copy(blockedStudioNames = updatedNames, blockedStudioIds = updatedIds))

        // Immediately filter out from active scene results
        _stashScenesList.value = _stashScenesList.value.filterNot { scene ->
            (scene.studioId != null && updatedIds.contains(scene.studioId)) ||
            (scene.studioName != null && updatedNames.any { it.equals(scene.studioName.trim(), ignoreCase = true) })
        }
    }

    fun unblockStudio(studioName: String) {
        val name = studioName.trim()
        val current = settings.value
        val updatedNames = current.blockedStudioNames.filterNot { it.equals(name, ignoreCase = true) }
        updateSettings(current.copy(blockedStudioNames = updatedNames))
    }

    fun clearAllBlockedStudios() {
        val current = settings.value
        updateSettings(current.copy(blockedStudioNames = emptyList(), blockedStudioIds = emptyList()))
    }

    fun selectStashPerformer(performer: StashPerformer, apiKey: String) {
        _stashSelectedPerformer.value = performer
        _stashSelectedStudio.value = null
        _stashSelectedSceneIds.value = emptySet()

        if (_stashActiveType.value == StashSearchType.SEXMEX) {
            stashScenesJob?.cancel()
            stashScenesJob = viewModelScope.launch(Dispatchers.IO) {
                _isStashLoadingScenes.value = true
                _stashCurrentPage.value = 1
                _stashScenesList.value = emptyList()
                _stashSearchError.value = null
                try {
                    val scenes = com.example.network.SexMexScraper.scrapeSexMexPage(performer.id)
                    _stashTotalScenesCount.value = scenes.size
                    _stashScenesList.value = scenes
                    _stashCanLoadMore.value = false
                    _isStashLoadingScenes.value = false
                } catch (e: Exception) {
                    _stashSearchError.value = e.message ?: "Failed to load SexMex scenes"
                    _isStashLoadingScenes.value = false
                }
            }
            return
        }

        stashScenesJob?.cancel()
        stashScenesJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingScenes.value = true
            _stashCurrentPage.value = 1
            _stashScenesList.value = emptyList()
            _stashSearchError.value = null

            val res = StashDbApiService.queryPerformerScenes(
                performerId = performer.id,
                apiKey = apiKey,
                page = 1,
                perPage = 30
            )
            res.onSuccess { queryResult ->
                val currentSettings = settings.value
                val filteredScenes = if (currentSettings.enableStudioFilter) {
                    queryResult.scenes.filterNot { isStudioBlocked(it.studioId, it.studioName) }
                } else {
                    queryResult.scenes
                }
                _stashTotalScenesCount.value = queryResult.count
                _stashScenesList.value = filteredScenes
                _stashCanLoadMore.value = queryResult.scenes.isNotEmpty() && (1 * 30 < queryResult.count)
                _isStashLoadingScenes.value = false
            }.onFailure { err ->
                _stashSearchError.value = err.message ?: "Failed to load scenes"
                _isStashLoadingScenes.value = false
            }
        }
    }

    fun selectStashStudio(studio: StashStudio, apiKey: String) {
        _stashSelectedStudio.value = studio
        _stashSelectedPerformer.value = null
        _stashSelectedSceneIds.value = emptySet()
        stashScenesJob?.cancel()
        stashScenesJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingScenes.value = true
            _stashCurrentPage.value = 1
            _stashScenesList.value = emptyList()
            _stashSearchError.value = null

            val res = StashDbApiService.queryStudioScenes(
                studioId = studio.id,
                apiKey = apiKey,
                page = 1,
                perPage = 30,
                providedChildIds = studio.childIds
            )
            res.onSuccess { queryResult ->
                val currentSettings = settings.value
                val filteredScenes = if (currentSettings.enableStudioFilter) {
                    queryResult.scenes.filterNot { isStudioBlocked(it.studioId, it.studioName) }
                } else {
                    queryResult.scenes
                }
                _stashTotalScenesCount.value = queryResult.count
                _stashScenesList.value = filteredScenes
                _stashCanLoadMore.value = queryResult.scenes.isNotEmpty() && (1 * 30 < queryResult.count)
                _isStashLoadingScenes.value = false
            }.onFailure { err ->
                _stashSearchError.value = err.message ?: "Failed to load studio scenes"
                _isStashLoadingScenes.value = false
            }
        }
    }

    fun loadMoreStashScenes(apiKey: String) {
        if (_isStashLoadingMore.value || !_stashCanLoadMore.value) return
        val currentType = _stashActiveType.value
        val selectedPerf = _stashSelectedPerformer.value
        val selectedStud = _stashSelectedStudio.value

        // Guard: do not load more if entity type does not match active tab
        if (currentType == StashSearchType.ACTORS && selectedPerf == null) return
        if (currentType == StashSearchType.STUDIO && selectedStud == null) return
        if (currentType == StashSearchType.SEXMEX) return

        val nextPage = _stashCurrentPage.value + 1

        viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingMore.value = true

            if (currentType == StashSearchType.ACTORS && selectedPerf != null) {
                val res = StashDbApiService.queryPerformerScenes(
                    performerId = selectedPerf.id,
                    apiKey = apiKey,
                    page = nextPage,
                    perPage = 30
                )
                res.onSuccess { queryResult ->
                    _stashCurrentPage.value = nextPage
                    val current = _stashScenesList.value
                    val currentSettings = settings.value
                    val filteredResult = if (currentSettings.enableStudioFilter) {
                        queryResult.scenes.filterNot { isStudioBlocked(it.studioId, it.studioName) }
                    } else {
                        queryResult.scenes
                    }
                    val newUnique = filteredResult.filter { ns -> current.none { it.id == ns.id } }
                    val updated = current + newUnique
                    _stashScenesList.value = updated
                    _stashCanLoadMore.value = queryResult.scenes.isNotEmpty() && (nextPage * 30 < queryResult.count)
                }.onFailure { err ->
                    _stashSearchError.value = err.message ?: "Failed to load more scenes"
                    _stashCanLoadMore.value = false
                }
            } else if (currentType == StashSearchType.STUDIO && selectedStud != null) {
                val res = StashDbApiService.queryStudioScenes(
                    studioId = selectedStud.id,
                    apiKey = apiKey,
                    page = nextPage,
                    perPage = 30,
                    providedChildIds = selectedStud.childIds
                )
                res.onSuccess { queryResult ->
                    _stashCurrentPage.value = nextPage
                    val current = _stashScenesList.value
                    val currentSettings = settings.value
                    val filteredResult = if (currentSettings.enableStudioFilter) {
                        queryResult.scenes.filterNot { isStudioBlocked(it.studioId, it.studioName) }
                    } else {
                        queryResult.scenes
                    }
                    val newUnique = filteredResult.filter { ns -> current.none { it.id == ns.id } }
                    val updated = current + newUnique
                    _stashScenesList.value = updated
                    _stashCanLoadMore.value = queryResult.scenes.isNotEmpty() && (nextPage * 30 < queryResult.count)
                }.onFailure { err ->
                    _stashSearchError.value = err.message ?: "Failed to load more studio scenes"
                    _stashCanLoadMore.value = false
                }
            }
            _isStashLoadingMore.value = false
        }
    }

    // Batch save progress state
    private val _isSavingWithProgress = MutableStateFlow(false)
    val isSavingWithProgress: StateFlow<Boolean> = _isSavingWithProgress.asStateFlow()

    private val _saveProgressCurrent = MutableStateFlow(0)
    val saveProgressCurrent: StateFlow<Int> = _saveProgressCurrent.asStateFlow()

    private val _saveProgressTotal = MutableStateFlow(0)
    val saveProgressTotal: StateFlow<Int> = _saveProgressTotal.asStateFlow()

    private val _saveCurrentTitle = MutableStateFlow("")
    val saveCurrentTitle: StateFlow<String> = _saveCurrentTitle.asStateFlow()

    private val _saveCurrentPhase = MutableStateFlow("")
    val saveCurrentPhase: StateFlow<String> = _saveCurrentPhase.asStateFlow()

    private val _saveSavedWithTorrentsCount = MutableStateFlow(0)
    val saveSavedWithTorrentsCount: StateFlow<Int> = _saveSavedWithTorrentsCount.asStateFlow()

    private var batchSaveJob: kotlinx.coroutines.Job? = null

    fun cancelBatchSave() {
        batchSaveJob?.cancel()
        _isSavingWithProgress.value = false
    }

    fun saveSelectedStashScenesWithProgress(
        fetchTorrents: Boolean = true,
        onComplete: (savedCount: Int, torrentsCount: Int) -> Unit
    ) {
        batchSaveJob?.cancel()
        batchSaveJob = viewModelScope.launch(Dispatchers.IO) {
            val selectedIds = _stashSelectedSceneIds.value
            if (selectedIds.isEmpty()) return@launch

            val scenesToSave = _stashScenesList.value.filter { selectedIds.contains(it.id) }
            if (scenesToSave.isEmpty()) return@launch

            _isSavingWithProgress.value = true
            _saveProgressCurrent.value = 0
            _saveProgressTotal.value = scenesToSave.size
            _saveSavedWithTorrentsCount.value = 0
            _saveCurrentTitle.value = ""
            _saveCurrentPhase.value = ""

            var totalSaved = 0
            var torrentsCount = 0

            val selectedPerf = _stashSelectedPerformer.value

            try {
                for ((index, scene) in scenesToSave.withIndex()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    _saveProgressCurrent.value = index + 1
                    _saveCurrentTitle.value = scene.title
                    _saveCurrentPhase.value = "Fetching torrent..."

                    val allExistingActors = repository.allActors.first()
                    val allExistingStudios = repository.allStudios.first()
                    val allExistingLinks = repository.allLinks.first()

                    // 1. Process female performers
                    val sortedPerformers = if (selectedPerf != null) {
                        val matching = scene.femalePerformers.filter {
                            it.id == selectedPerf.id || it.name.trim().equals(selectedPerf.name.trim(), ignoreCase = true)
                        }
                        val others = scene.femalePerformers.filterNot {
                            it.id == selectedPerf.id || it.name.trim().equals(selectedPerf.name.trim(), ignoreCase = true)
                        }
                        matching + others
                    } else {
                        scene.femalePerformers
                    }

                    val actorEntitiesForSearch = mutableListOf<ActorEntity>()
                    val actorIds = mutableListOf<String>()
                    val newActors = mutableListOf<ActorEntity>()

                    for (perf in sortedPerformers) {
                        val pName = perf.name.trim()
                        if (pName.isBlank()) continue

                        val existing = allExistingActors.find {
                            (it.stashDbId != null && it.stashDbId == perf.id) ||
                            it.name.trim().equals(pName, ignoreCase = true)
                        } ?: newActors.find {
                            (it.stashDbId != null && it.stashDbId == perf.id) ||
                            it.name.trim().equals(pName, ignoreCase = true)
                        }

                        if (existing != null) {
                            actorIds.add(existing.id)
                            actorEntitiesForSearch.add(existing)
                        } else {
                            val newActorId = UUID.randomUUID().toString()
                            val actor = ActorEntity(
                                id = newActorId,
                                stashDbId = perf.id,
                                name = perf.name,
                                imageUrl = perf.imageUrl ?: "",
                                originalImageUrl = perf.imageUrl
                            )
                            newActors.add(actor)
                            actorIds.add(newActorId)
                            actorEntitiesForSearch.add(actor)
                        }
                    }

                    // 2. Process Studio
                    val studioEntitiesForSearch = mutableListOf<StudioEntity>()
                    val studioIds = mutableListOf<String>()
                    val newStudios = mutableListOf<StudioEntity>()

                    if (!scene.studioName.isNullOrBlank()) {
                        val sName = scene.studioName.trim()
                        val existingStudio = allExistingStudios.find {
                            (scene.studioId != null && it.stashDbId == scene.studioId) ||
                            it.name.trim().equals(sName, ignoreCase = true)
                        } ?: newStudios.find {
                            (scene.studioId != null && it.stashDbId == scene.studioId) ||
                            it.name.trim().equals(sName, ignoreCase = true)
                        }

                        if (existingStudio != null) {
                            studioIds.add(existingStudio.id)
                            studioEntitiesForSearch.add(existingStudio)
                        } else {
                            val newStudioId = UUID.randomUUID().toString()
                            val studio = StudioEntity(
                                id = newStudioId,
                                stashDbId = scene.studioId,
                                name = scene.studioName,
                                logoUrl = scene.studioLogo,
                                imageUrl = scene.studioLogo
                            )
                            newStudios.add(studio)
                            studioIds.add(newStudioId)
                            studioEntitiesForSearch.add(studio)
                        }
                    }

                    // 3. Parse date
                    val parsedDate = StashDbApiService.parseDateToMillis(scene.date)

                    // 4. Fetch torrent magnet using shared fetchMagnetInternal logic
                    val torrentResult = try {
                        fetchMagnetInternal(
                            title = scene.title,
                            selectedActors = actorEntitiesForSearch,
                            selectedStudios = studioEntitiesForSearch,
                            assignedDate = parsedDate,
                            urlHD = scene.coverUrl ?: "",
                            url4K = ""
                        )
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        null
                    }

                    val hasTorrent = torrentResult != null && torrentResult.hasResult
                    if (hasTorrent) {
                        torrentsCount++
                        _saveSavedWithTorrentsCount.value = torrentsCount
                    }

                    _saveCurrentPhase.value = "Saving..."

                    // 5. Build & insert LinkEntity
                    val existingLink = allExistingLinks.find {
                        (it.stashDbId != null && it.stashDbId == scene.id) ||
                        (it.title.trim().equals(scene.title.trim(), ignoreCase = true) && it.assignedDate == parsedDate)
                    }

                    val linkToSave = if (existingLink != null) {
                        existingLink.copy(
                            stashDbId = scene.id,
                            title = scene.title,
                            coverImage = if (existingLink.coverImage.isBlank()) (scene.coverUrl ?: "") else existingLink.coverImage,
                            actorIds = (actorIds + existingLink.actorIds).distinct(),
                            studioIds = (studioIds + existingLink.studioIds).distinct(),
                            assignedDate = existingLink.assignedDate ?: parsedDate,
                            magnet = torrentResult?.magnet1080p ?: existingLink.magnet,
                            magnet4K = torrentResult?.magnet2160p ?: existingLink.magnet4K,
                            torrentUrlHD = torrentResult?.url1080p ?: existingLink.torrentUrlHD,
                            torrentUrl4K = torrentResult?.url2160p ?: existingLink.torrentUrl4K,
                            torrentSiteName = torrentResult?.sourceSite ?: existingLink.torrentSiteName
                        )
                    } else {
                        LinkEntity(
                            id = UUID.randomUUID().toString(),
                            stashDbId = scene.id,
                            title = scene.title,
                            coverImage = scene.coverUrl ?: "",
                            actorIds = actorIds.distinct(),
                            studioIds = studioIds.distinct(),
                            assignedDate = parsedDate,
                            magnet = torrentResult?.magnet1080p,
                            magnet4K = torrentResult?.magnet2160p,
                            torrentUrlHD = torrentResult?.url1080p,
                            torrentUrl4K = torrentResult?.url2160p,
                            torrentSiteName = torrentResult?.sourceSite
                        )
                    }

                    if (newActors.isNotEmpty()) repository.insertActors(newActors)
                    if (newStudios.isNotEmpty()) repository.insertStudios(newStudios)
                    repository.insertLink(linkToSave)

                    totalSaved++
                }

                _stashSelectedSceneIds.value = emptySet()
                withContext(Dispatchers.Main) {
                    onComplete(totalSaved, torrentsCount)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                } else {
                    android.util.Log.e("MainViewModel", "Batch save error", e)
                }
            } finally {
                _isSavingWithProgress.value = false
            }
        }
    }

    fun saveSelectedStashScenesWithProgress(
        fetchTorrents: Boolean = true,
        onComplete: (Int) -> Unit
    ) {
        saveSelectedStashScenesWithProgress(fetchTorrents) { savedCount, _ ->
            onComplete(savedCount)
        }
    }

    fun saveSelectedStashScenes(onComplete: (Int) -> Unit) {
        saveSelectedStashScenesWithProgress { savedCount, _ ->
            onComplete(savedCount)
        }
    }

    // Search, Tabs, Filter and Sort
    val searchQuery = MutableStateFlow("")
    val sortMode = MutableStateFlow<SortMode>(SortMode.NEW)
    val homeTab = MutableStateFlow(0) // 0: Videos, 1: Channels/Studios, 2: Bookmarks
    val bookmarkedIds = MutableStateFlow<Set<String>>(emptySet())
    val lastFeedRefresh = MutableStateFlow(System.currentTimeMillis())
    val viewFilter = MutableStateFlow("ALL") // ALL, 4K, HD

    fun toggleBookmark(id: String) {
        val curr = bookmarkedIds.value
        bookmarkedIds.value = if (curr.contains(id)) curr - id else curr + id
    }

    fun refreshFeed() {
        lastFeedRefresh.value = System.currentTimeMillis()
    }

    // Data Flows
    val allLinks = repository.allLinks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allActors = repository.allActors.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allStudios = repository.allStudios.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isInitialDataLoaded: StateFlow<Boolean> = repository.allLinks
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val actorSceneCounts: StateFlow<Map<String, Int>> = allLinks
        .map { links ->
            withContext(Dispatchers.Default) {
                links.flatMap { it.actorIds }.groupingBy { it }.eachCount()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val studioSceneCounts: StateFlow<Map<String, Int>> = allLinks
        .map { links ->
            withContext(Dispatchers.Default) {
                links.flatMap { it.studioIds }.groupingBy { it }.eachCount()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private val _settingsState = MutableStateFlow<SettingsEntity?>(null)
    val settings: StateFlow<SettingsEntity> = repository.settings
        .map { it ?: SettingsEntity() }
        .onEach { dbSettings ->
            if (_settingsState.value == null) {
                _settingsState.value = dbSettings
            }
        }
        .combine(_settingsState) { dbSettings, localOverride ->
            localOverride ?: dbSettings
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    // Filtered scenes based on search, tabs, filter and sort
    val filteredLinks: StateFlow<List<LinkEntity>> = combine(
        combine(allLinks, searchQuery, allActors, allStudios) { links, query, actors, studios ->
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val matchingActorIds = actors.filter { it.name.lowercase().contains(q) }.map { it.id }.toSet()
                val matchingStudioIds = studios.filter { it.name.lowercase().contains(q) }.map { it.id }.toSet()
                links.filter { link ->
                    link.title.lowercase().contains(q) ||
                    link.actorIds.any { matchingActorIds.contains(it) } ||
                    link.studioIds.any { matchingStudioIds.contains(it) }
                }
            } else {
                links
            }
        },
        combine(sortMode, viewFilter) { sort, filter -> sort to filter },
        combine(homeTab, bookmarkedIds) { tab, bookmarks -> tab to bookmarks }
    ) { searchedLinks, (sort, filter), (tab, bookmarks) ->
        var list: List<LinkEntity> = searchedLinks

        if (filter == "4K") {
            list = list.filter { it.url4K != null || it.magnet4K != null }
        } else if (filter == "HD") {
            list = list.filter { it.urlHD != null || it.magnet != null }
        }

        if (tab == 2) {
            list = list.filter { bookmarks.contains(it.id) }
        }

        when (sort) {
            SortMode.NEW -> list.sortedByDescending { it.assignedDate ?: it.createdAt }
            SortMode.OLD -> list.sortedBy { it.assignedDate ?: it.createdAt }
            SortMode.RECENTLY_ADDED -> list.sortedByDescending { it.createdAt }
            SortMode.OLDEST_ADDED -> list.sortedBy { it.createdAt }
        }
    }.distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // CRUD operations
    fun saveLink(link: LinkEntity) {
        viewModelScope.launch {
            repository.insertLink(link)
        }
    }

    fun deleteLink(id: String) {
        viewModelScope.launch {
            repository.deleteLinkById(id)
        }
    }

    fun saveActor(actor: ActorEntity) {
        viewModelScope.launch {
            repository.insertActor(actor)
        }
    }

    fun deleteActor(id: String) {
        viewModelScope.launch {
            repository.deleteActorById(id)
        }
    }

    fun deleteActorWithCascade(actorId: String) {
        viewModelScope.launch {
            try {
                val links = repository.allLinks.first()
                links.forEach { link ->
                    if (link.actorIds.contains(actorId)) {
                        if (link.actorIds.size > 1) {
                            // Link has other actors tagged: keep link, un-tag this actor
                            val updatedActors = link.actorIds.filter { it != actorId }
                            repository.updateLink(link.copy(actorIds = updatedActors))
                        } else {
                            // Sole actor: delete link completely
                            repository.deleteLinkById(link.id)
                        }
                    }
                }
                repository.deleteActorById(actorId)
            } catch (e: Exception) {
                repository.deleteActorById(actorId)
            }
        }
    }

    fun saveStudio(studio: StudioEntity) {
        viewModelScope.launch {
            repository.insertStudio(studio)
        }
    }

    fun deleteStudio(id: String) {
        viewModelScope.launch {
            repository.deleteStudioById(id)
        }
    }

    fun deleteStudioWithCascade(studioId: String) {
        viewModelScope.launch {
            try {
                val links = repository.allLinks.first()
                links.filter { it.studioIds.contains(studioId) }.forEach { link ->
                    repository.deleteLinkById(link.id)
                }
                repository.deleteStudioById(studioId)
            } catch (e: Exception) {
                repository.deleteStudioById(studioId)
            }
        }
    }

    fun updateSettings(newSettings: SettingsEntity) {
        _settingsState.value = newSettings
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    // Auto-match actors & studios by scene title (System Check)
    fun runSystemCheck(onComplete: (matched: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val links = repository.allLinks.first()
            val actors = repository.allActors.first()
            val studios = repository.allStudios.first()
            var matchCount = 0

            for (link in links) {
                val foundActors = actors.filter {
                    it.name.isNotBlank() && link.title.contains(it.name, ignoreCase = true)
                }.map { it.id }

                val foundStudios = studios.filter {
                    it.name.isNotBlank() && link.title.contains(it.name, ignoreCase = true)
                }.map { it.id }

                val newActorIds = (link.actorIds + foundActors).distinct()
                val newStudioIds = (link.studioIds + foundStudios).distinct()

                if (newActorIds != link.actorIds || newStudioIds != link.studioIds) {
                    repository.updateLink(
                        link.copy(actorIds = newActorIds, studioIds = newStudioIds)
                    )
                    matchCount++
                }
            }
            onComplete(matchCount)
        }
    }

    // Export JSON string
    suspend fun exportDataJson(): String {
        val root = JSONObject()
        val linksArr = JSONArray()
        repository.allLinks.first().forEach { l ->
            val obj = JSONObject()
            obj.put("id", l.id)
            obj.put("title", l.title)
            obj.put("coverImage", l.coverImage)
            obj.put("urlHD", l.urlHD ?: JSONObject.NULL)
            obj.put("url4K", l.url4K ?: JSONObject.NULL)
            obj.put("aspectRatio", l.aspectRatio)
            linksArr.put(obj)
        }
        root.put("links", linksArr)
        return root.toString(2)
    }

    suspend fun importJsonData(jsonString: String): Result<Int> {
        return try {
            val root = JSONObject(jsonString.trim())
            val linksArr = root.optJSONArray("links") ?: JSONArray()
            val importedList = mutableListOf<com.example.data.local.entity.LinkEntity>()
            for (i in 0 until linksArr.length()) {
                val obj = linksArr.getJSONObject(i)
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val title = obj.optString("title", "Imported Scene")
                val coverImage = obj.optString("coverImage", "")
                val urlHD = if (obj.isNull("urlHD")) null else obj.optString("urlHD")
                val url4K = if (obj.isNull("url4K")) null else obj.optString("url4K")
                val aspectRatio = obj.optString("aspectRatio", "16:9")
                importedList.add(
                    com.example.data.local.entity.LinkEntity(
                        id = id,
                        title = title,
                        coverImage = coverImage,
                        urlHD = urlHD,
                        url4K = url4K,
                        aspectRatio = aspectRatio
                    )
                )
            }
            if (importedList.isNotEmpty()) {
                repository.insertLinks(importedList)
            }
            Result.success(importedList.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importSampleDataset(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            com.example.data.util.SampleTestDataset.sampleActors.forEach { repository.insertActor(it) }
            com.example.data.util.SampleTestDataset.sampleStudios.forEach { repository.insertStudio(it) }
            repository.insertLinks(com.example.data.util.SampleTestDataset.sampleScenes)
            onDone(com.example.data.util.SampleTestDataset.sampleScenes.size)
        }
    }

    fun clearSampleDataset(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val sceneIds = com.example.data.util.SampleTestDataset.sampleScenes.map { it.id }
            var count = 0
            sceneIds.forEach { id ->
                repository.deleteLinkById(id)
                count++
            }
            onDone(count)
        }
    }

    // ==========================================
    // TORRENT MAGNET FETCHING
    // ==========================================
    private val _isFetchingMagnet = MutableStateFlow(false)
    val isFetchingMagnet: StateFlow<Boolean> = _isFetchingMagnet.asStateFlow()

    private var fetchMagnetJob: kotlinx.coroutines.Job? = null

    fun cancelMagnetFetch() {
        fetchMagnetJob?.cancel()
        _isFetchingMagnet.value = false
    }

    suspend fun fetchMagnetInternal(
        title: String,
        selectedActors: List<ActorEntity>,
        selectedStudios: List<StudioEntity>,
        assignedDate: Long?,
        urlHD: String = "",
        url4K: String = ""
    ): com.example.network.torrent.TorrentSearchResult? {
        val cleanTitle = com.example.network.torrent.QueryBuilder.cleanTitle(title)
        val javMatch = com.example.network.torrent.QueryBuilder.extractJavMatch(title)

        val directExtractCandidate = listOf(title, urlHD, url4K)
            .firstOrNull { it.contains("xxxclub.to/torrents/details/", ignoreCase = true) }

        if (directExtractCandidate == null &&
            selectedActors.isEmpty() &&
            selectedStudios.isEmpty() &&
            assignedDate == null &&
            cleanTitle.isBlank() &&
            javMatch == null
        ) {
            return null
        }

        // Step 1: Direct extract shortcut
        if (directExtractCandidate != null) {
            val directUrlMatch = Regex("https?://[^\\s<>\"']*(?:xxxclub\\.to/torrents/details/\\d+[^\\s<>\"']*)")
                .find(directExtractCandidate)?.value ?: directExtractCandidate
            val directRes = com.example.network.torrent.TorrentScraper.directExtract(directUrlMatch, getApplication())
            if (directRes != null && directRes.hasResult) {
                return directRes
            }
        }

        // Step 2: JAV mode
        if (javMatch != null) {
            val sukebeiRes = com.example.network.torrent.TorrentScraper.searchSukebei(javMatch, getApplication())
            return if (sukebeiRes.hasResult) sukebeiRes else null
        }

        // Step 3: XXXClub smart search (western)
        val actorNames = selectedActors.map { it.name }
        val studioNames = selectedStudios.map { it.name }
        val dateStr = com.example.network.torrent.DatePatterns.formatDateStr(assignedDate)
        val datePatterns = com.example.network.torrent.DatePatterns.generatePatterns(
            dateStr = dateStr,
            targetDateMs = assignedDate
        )
        val queries = com.example.network.torrent.QueryBuilder.buildCascadeQueries(
            title = title,
            actors = actorNames,
            studios = studioNames,
            dateStr = dateStr,
            extraSearchText = "$urlHD $url4K".trim()
        )

        var searchRes = com.example.network.torrent.TorrentScraper.executeQueriesLoop(
            queries = queries,
            datePatterns = datePatterns,
            studios = studioNames,
            actors = actorNames,
            context = getApplication()
        )

        // Step 4: StashDB parent/child fallback if nothing found
        if ((searchRes == null || !searchRes.hasResult) && selectedStudios.isNotEmpty()) {
            val currentSettings = repository.getSettingsOnce()
            val stashApiKey = currentSettings.stashDbApiKey
            if (stashApiKey.isNotBlank()) {
                searchRes = com.example.network.torrent.TorrentScraper.tryStashDbStudioFallback(
                    primaryActor = actorNames.firstOrNull(),
                    dateStr = dateStr,
                    selectedStudios = selectedStudios,
                    stashDbApiKey = stashApiKey,
                    datePatterns = datePatterns,
                    actors = actorNames,
                    context = getApplication()
                )
            }
        }

        return if (searchRes != null && searchRes.hasResult) searchRes else null
    }

    fun fetchMagnet(
        title: String,
        selectedActors: List<ActorEntity>,
        selectedStudios: List<StudioEntity>,
        assignedDate: Long?,
        urlHD: String,
        url4K: String,
        onSuccess: (com.example.network.torrent.TorrentSearchResult) -> Unit,
        onNoResult: () -> Unit,
        onError: (String) -> Unit
    ) {
        fetchMagnetJob?.cancel()

        val cleanTitle = com.example.network.torrent.QueryBuilder.cleanTitle(title)
        val javMatch = com.example.network.torrent.QueryBuilder.extractJavMatch(title)
        val directExtractCandidate = listOf(title, urlHD, url4K)
            .firstOrNull { it.contains("xxxclub.to/torrents/details/", ignoreCase = true) }

        // Section 2: Validation
        if (directExtractCandidate == null &&
            selectedActors.isEmpty() &&
            selectedStudios.isEmpty() &&
            assignedDate == null &&
            cleanTitle.isBlank() &&
            javMatch == null
        ) {
            onError("Please select a studio, actor, date, or enter a title to search")
            return
        }

        fetchMagnetJob = viewModelScope.launch(Dispatchers.IO) {
            _isFetchingMagnet.value = true
            try {
                val res = fetchMagnetInternal(
                    title = title,
                    selectedActors = selectedActors,
                    selectedStudios = selectedStudios,
                    assignedDate = assignedDate,
                    urlHD = urlHD,
                    url4K = url4K
                )
                withContext(Dispatchers.Main) {
                    if (res != null && res.hasResult) {
                        onSuccess(res)
                    } else {
                        onNoResult()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    onError("Torrent search failed: ${e.message}")
                }
            } finally {
                _isFetchingMagnet.value = false
            }
        }
    }
}

class SharedPlayerManager(private val context: android.content.Context) {
    private var _exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getPlayer(): androidx.media3.exoplayer.ExoPlayer {
        val existing = _exoPlayer
        if (existing != null) return existing

        val newPlayer = com.example.ui.components.PlayerFactory.createPlayer(context)
        _exoPlayer = newPlayer
        return newPlayer
    }

    fun stopPlayer() {
        _exoPlayer?.stop()
        _exoPlayer?.clearMediaItems()
    }

    fun release() {
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
