package com.nononsenseapps.feeder.ui

import android.os.Bundle
import android.view.Menu
import android.webkit.WebView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.nononsenseapps.feeder.R
import com.nononsenseapps.feeder.coroutines.CoroutineScopedActivity
import com.nononsenseapps.feeder.model.FeedUnreadCount
import com.nononsenseapps.feeder.model.configurePeriodicSync
import com.nononsenseapps.feeder.model.getFeedListViewModel
import com.nononsenseapps.feeder.model.getSettingsViewModel
import com.nononsenseapps.feeder.util.PrefUtils
import com.nononsenseapps.feeder.util.bundle
import kotlinx.android.synthetic.main.activity_navigation.*
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.navdrawer_for_ab_overlay.*

class NavigationActivity : CoroutineScopedActivity() {

    private lateinit var navAdapter: FeedsAdapter
    private val navController: NavController by lazy {
        findNavController(R.id.nav_host_fragment)
    }
    private val settingsViewModel by lazy { getSettingsViewModel() }
    var fabOnClickListener: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
        setSupportActionBar(toolbar)

        // Write default setting if method has never been called before
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)

        // There is a bug in webview which will reset the night mode on first start
        // So initialize it before we set night mode
        try {
            WebView(applicationContext)
        } catch (ignored: Exception) {
            // ignored
        }

        // Not persisted so set nightmode every time we start
        AppCompatDelegate.setDefaultNightMode(settingsViewModel.themePreference)

        settingsViewModel.liveThemePreference.observe(this, androidx.lifecycle.Observer {
            delegate.setLocalNightMode(it)
        })

        // Enable periodic sync
        configurePeriodicSync(applicationContext, forceReplace = false)

        fab.setOnClickListener {
            fabOnClickListener()
        }

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        val appBarConfiguration = AppBarConfiguration(navController.graph, drawer_layout)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        setupNavDrawer()

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            when (destination.id) {
                R.id.feedFragment -> {
                    fab.setImageResource(R.drawable.ic_done_all_white_24dp)
                    fab.show()
                }
                else -> {
                    fab.hide()
                    fabOnClickListener = {}
                }
            }
        }
    }

    private fun setupNavDrawer() {
        // Recycler view stuff
        navdrawer_list.setHasFixedSize(true)
        navdrawer_list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        navAdapter = FeedsAdapter(object : OnNavigationItemClickListener {
            override fun onNavigationItemClick(id: Long, displayTitle: String?, url: String?, tag: String?) {
                drawer_layout.closeDrawer(GravityCompat.START)

                navController.navigate(R.id.action_feedFragment_self, bundle {
                    putLong(ARG_FEED_ID, id)
                    putString(ARG_FEEDTITLE, displayTitle)
                    putString(ARG_FEED_URL, url)
                    putString(ARG_FEED_TAG, tag)
                })
            }
        })
        navdrawer_list.adapter = navAdapter

        getFeedListViewModel().liveFeedsAndTagsWithUnreadCounts
                .observe(this, androidx.lifecycle.Observer<List<FeedUnreadCount>> {
                    navAdapter.submitList(it)
                })

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!PrefUtils.isWelcomeDone(this)) {
            // first run of the app starts with the nav drawer open
            PrefUtils.markWelcomeDone(this)
            openNavDrawer()
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //menuInflater.inflate(R.menu.feed, menu)
        return true
    }

    fun openNavDrawer() {
        drawer_layout.openDrawer(GravityCompat.START)
    }
}
