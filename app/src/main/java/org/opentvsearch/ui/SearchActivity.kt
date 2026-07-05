package org.opentvsearch.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.opentvsearch.sources.tvprovider.TvProviderSearchSource
import org.opentvsearch.ui.theme.OpenTvSearchTheme

/**
 * Single, EXPORTED, searchable entry point. Can be launched:
 *  - from the TV launcher,
 *  - by a remote button (mapped via Button Mapper etc.) firing ACTION_SEARCH,
 *  - by the system search key.
 *
 * Hosts the Compose-for-TV [SearchScreen], wires [SearchViewModel] via Hilt, requests the
 * runtime permissions the sources need (READ_TV_LISTINGS for TV-Provider rows; RECORD_AUDIO
 * for voice), and — when the "voice on launch" setting is on and there is no incoming query —
 * fires the voice recognizer immediately.
 */
@AndroidEntryPoint
class SearchActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
                if (spoken.isNotBlank()) viewModel.onVoiceResult(spoken)
            }
        }

    private val tvListingsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Regardless of grant/deny, re-run any pending query: a grant now unlocks
            // TV-Provider INLINE rows; a denial simply leaves hand-off results.
            if (viewModel.state.value.query.isNotBlank()) viewModel.submit()
        }

    private val recordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchVoice()
            else Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpenTvSearchTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SearchScreen(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onSubmit = viewModel::submit,
                    onVoice = ::startVoiceFlow,
                    onResultClick = ::launchResult,
                    onOpenSettings = ::openSettings,
                )
            }
        }

        // Request READ_TV_LISTINGS so the TV-Provider source can read other apps' rows.
        if (!hasPermission(TvProviderSearchSource.PERMISSION_READ_TV_LISTINGS)) {
            tvListingsPermission.launch(TvProviderSearchSource.PERMISSION_READ_TV_LISTINGS)
        }

        handleSearchIntent(intent)
    }

    /**
     * The activity is `singleTop`, so a SEARCH/ASSIST intent delivered to an already-running
     * instance goes to [onNewIntent], NOT [onCreate]. Route both through here so a repeated
     * remote-search press re-applies the incoming query and re-evaluates the voice auto-launch.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSearchIntent(intent)
    }

    /**
     * Apply an incoming ACTION_SEARCH / GMS SEARCH_ACTION / ACTION_ASSIST intent: seed + submit
     * any query it carries, then decide whether to auto-launch the voice recognizer. A NEW intent
     * is allowed to fire voice again (desired for a repeated remote-search press); the launch fires
     * at most once per intent because this runs once per delivered intent.
     */
    private fun handleSearchIntent(intent: Intent?) {
        // Handle an incoming ACTION_SEARCH / GMS SEARCH_ACTION / ACTION_ASSIST
        // (remote button / global search / assistant-style "search this app").
        val incomingQuery = when (intent?.action) {
            Intent.ACTION_SEARCH,
            "com.google.android.gms.actions.SEARCH_ACTION",
            Intent.ACTION_ASSIST ->
                intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty()
            else -> ""
        }

        // "Launched as a search intent" = the launcher/remote-mapper asked us to start a
        // search (with or without a query), as opposed to a plain launcher open.
        val isSearchIntent = intent?.action in SEARCH_INTENT_ACTIONS

        if (incomingQuery.isNotBlank()) {
            viewModel.onQueryChange(incomingQuery)
            viewModel.submit() // show hand-off results immediately, even before permission
        }

        lifecycleScope.launch {
            val voiceOnLaunchPref = viewModel.voiceOnLaunch()
            if (shouldAutoLaunchVoice(
                    isSearchIntent = isSearchIntent,
                    hasQuery = incomingQuery.isNotBlank(),
                    voiceOnLaunchPref = voiceOnLaunchPref,
                )
            ) {
                startVoiceFlow()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            startVoiceFlow()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startVoiceFlow() {
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) launchVoice()
        else recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search your content apps")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { Toast.makeText(this, "Voice search unavailable", Toast.LENGTH_SHORT).show() }
    }

    private fun openSettings() {
        runCatching { startActivity(Intent(this, SettingsActivity::class.java)) }
            .onFailure { Toast.makeText(this, "Couldn't open Settings", Toast.LENGTH_SHORT).show() }
    }

    private fun launchResult(result: org.opentvsearch.core.search.SearchResult) {
        runCatching { startActivity(result.launch) }
            .onFailure {
                Toast.makeText(this, "Couldn't open ${result.sourceLabel}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

internal val SEARCH_INTENT_ACTIONS = setOf(
    Intent.ACTION_SEARCH,
    "com.google.android.gms.actions.SEARCH_ACTION",
    Intent.ACTION_ASSIST,
)

internal fun shouldAutoLaunchVoice(
    isSearchIntent: Boolean,
    hasQuery: Boolean,
    voiceOnLaunchPref: Boolean,
): Boolean {
    if (hasQuery) return false
    return isSearchIntent || voiceOnLaunchPref
}
