package com.shizuku.arranger.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shizuku.arranger.data.ContactResolver
import com.shizuku.arranger.data.Direction
import com.shizuku.arranger.data.Recording
import com.shizuku.arranger.data.RecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DirectionFilter { ALL, INCOMING, OUTGOING }
enum class SortOrder(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), LONGEST("Longest first"), NUMBER("By number") }

data class PlayerState(
    val recording: Recording? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null,
)

class RecordingsViewModel(app: Application) : AndroidViewModel(app) {

    val repository = RecordingRepository(app)

    private val all = MutableStateFlow<List<Recording>>(emptyList())
    private val durations = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val contacts = ContactResolver(app)
    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Phone number (as in the file name) -> saved contact name. */
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(DirectionFilter.ALL)
    val sort = MutableStateFlow(SortOrder.NEWEST)
    /** When set, only recordings for this exact number are shown (tap a number to drill in). */
    val numberFocus = MutableStateFlow<String?>(null)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _folder = MutableStateFlow(repository.folderPath)
    val folder: StateFlow<String> = _folder.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    val durationMap: StateFlow<Map<String, Long>> = durations.asStateFlow()

    val visible: StateFlow<List<Recording>> =
        combine(all, query, filter, sort, combine(numberFocus, durations, _contactNames, ::Triple)) { list, q, f, s, (focus, durs, names) ->
            list.asSequence()
                .filter { focus == null || it.phoneNumber == focus }
                .filter {
                    when (f) {
                        DirectionFilter.ALL -> true
                        DirectionFilter.INCOMING -> it.direction == Direction.INCOMING
                        DirectionFilter.OUTGOING -> it.direction == Direction.OUTGOING
                    }
                }
                .filter { matches(it, q, names[it.phoneNumber]) }
                .let { seq ->
                    when (s) {
                        SortOrder.NEWEST -> seq.sortedByDescending { it.timestamp }
                        SortOrder.OLDEST -> seq.sortedBy { it.timestamp }
                        SortOrder.LONGEST -> seq.sortedByDescending { durs[it.file.path] ?: 0L }
                        SortOrder.NUMBER -> seq.sortedWith(
                            // Unknown numbers sort last.
                            compareBy<Recording> { (names[it.phoneNumber] ?: it.phoneNumber).lowercase().ifEmpty { "\uFFFF" } }
                                .thenByDescending { it.timestamp },
                        )
                    }
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalCount: StateFlow<Int> =
        all.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var durationJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val list = withContext(Dispatchers.IO) { repository.loadRecordings() }
            all.value = list
            _loading.value = false
            loadContactNames()
            loadDurations(list)
        }
    }

    /** Also called after the user grants the contacts permission. */
    fun loadContactNames() {
        viewModelScope.launch {
            val numbers = all.value.map { it.phoneNumber }.toSet()
            _contactNames.value = withContext(Dispatchers.IO) { contacts.resolve(numbers) }
        }
    }

    fun hasContactsPermission(): Boolean = contacts.hasPermission()

    private fun loadDurations(list: List<Recording>) {
        durationJob?.cancel()
        durationJob = viewModelScope.launch(Dispatchers.IO) {
            val missing = list.filter { it.file.path !in durations.value }
            // Publish in batches so the list fills in progressively without recomposing per file.
            missing.chunked(20).forEach { chunk ->
                val found = chunk.mapNotNull { r -> repository.durationOf(r)?.let { r.file.path to it } }
                durations.update { it + found }
            }
        }
    }

    fun setFolder(path: String) {
        repository.folderPath = path
        _folder.value = repository.folderPath
        refresh()
    }

    // ---- playback ----

    fun playOrToggle(recording: Recording) {
        val current = _player.value
        if (current.recording?.file == recording.file && mediaPlayer != null) {
            togglePause()
            return
        }
        releasePlayer()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(recording.file.path)
                setOnCompletionListener {
                    _player.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
                    progressJob?.cancel()
                }
                prepare()
                start()
            }
            mediaPlayer = mp
            _player.value = PlayerState(recording, true, 0, mp.duration)
            startProgress()
        } catch (e: Exception) {
            releasePlayer()
            _player.value = PlayerState(recording, error = "Can't play this file: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun togglePause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            progressJob?.cancel()
            _player.update { it.copy(isPlaying = false, positionMs = mp.currentPosition) }
        } else {
            if (mp.currentPosition >= mp.duration - 100) mp.seekTo(0)
            mp.start()
            _player.update { it.copy(isPlaying = true) }
            startProgress()
        }
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
        _player.update { it.copy(positionMs = ms) }
    }

    fun skip(deltaMs: Int) {
        val mp = mediaPlayer ?: return
        seekTo((mp.currentPosition + deltaMs).coerceIn(0, mp.duration))
    }

    fun stop() {
        releasePlayer()
        _player.value = PlayerState()
    }

    fun delete(recording: Recording) {
        if (_player.value.recording?.file == recording.file) stop()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.delete(recording) }
            if (ok) all.update { list -> list.filterNot { it.file == recording.file } }
        }
    }

    private fun startProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp -> _player.update { it.copy(positionMs = mp.currentPosition) } }
                delay(250)
            }
        }
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onCleared() {
        releasePlayer()
    }

    companion object {
        private val SEARCH_DATE_FORMATS = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "d MMM yyyy", "MMMM", "MMM", "EEEE", "HH:mm")

        /**
         * Free-text search: matches the contact name, phone number (ignoring spaces, dashes, +), the direction
         * ("in"/"incoming"/"out"/"outgoing"), dates like "2024-01-31", "31/01", "jan", "monday",
         * or the raw file name. Several words must all match.
         */
        fun matches(r: Recording, query: String, contactName: String? = null): Boolean {
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return true
            val date = Date(r.timestamp)
            val haystack = buildString {
                contactName?.let { append(it.lowercase()).append(' ') }
                append(r.phoneNumber.lowercase()).append(' ')
                append(r.file.name.lowercase()).append(' ')
                append(
                    when (r.direction) {
                        Direction.INCOMING -> "incoming received in "
                        Direction.OUTGOING -> "outgoing dialed made out "
                        Direction.UNKNOWN -> ""
                    }
                )
                SEARCH_DATE_FORMATS.forEach { append(SimpleDateFormat(it, Locale.getDefault()).format(date).lowercase()).append(' ') }
            }
            return words.all { w ->
                // "+1-555" should match a number stored as "1555..." regardless of punctuation.
                val looksLikeNumber = w.any { it.isDigit() } && w.all { it.isDigit() || it in "+-()." }
                haystack.contains(w) ||
                    (looksLikeNumber && r.numberDigits.contains(w.filter { it.isDigit() }))
            }
        }
    }
}
