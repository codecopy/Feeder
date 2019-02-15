package com.nononsenseapps.feeder.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.text.BidiFormatter
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.nononsenseapps.feeder.R
import com.nononsenseapps.feeder.coroutines.CoroutineScopedFragment
import com.nononsenseapps.feeder.db.room.AppDatabase
import com.nononsenseapps.feeder.db.room.FeedItemWithFeed
import com.nononsenseapps.feeder.db.room.ID_UNSET
import com.nononsenseapps.feeder.model.cancelNotification
import com.nononsenseapps.feeder.model.getFeedItemViewModel
import com.nononsenseapps.feeder.model.maxImageSize
import com.nononsenseapps.feeder.ui.text.toSpannedWithNoImages
import com.nononsenseapps.feeder.util.TabletUtils
import com.nononsenseapps.feeder.util.asFeedItemFoo
import com.nononsenseapps.feeder.util.bundle
import com.nononsenseapps.feeder.util.openLinkInBrowser
import com.nononsenseapps.feeder.views.ObservableScrollView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.util.*

const val ARG_TITLE = "title"
const val ARG_CUSTOMTITLE = "customtitle"
const val ARG_DESCRIPTION = "body"
const val ARG_LINK = "link"
const val ARG_ENCLOSURE = "enclosure"
const val ARG_IMAGEURL = "imageUrl"
const val ARG_ID = "dbid"
const val ARG_FEEDTITLE = "feedtitle"
const val ARG_AUTHOR = "author"
const val ARG_DATE = "date"

class ReaderFragment : CoroutineScopedFragment() {

    private val dateTimeFormat = DateTimeFormat.forStyle("FM").withLocale(Locale.getDefault())

    private var _id: Long = ID_UNSET
    // All content contained in RssItem
    private var rssItem: FeedItemWithFeed? = null
    private lateinit var bodyTextView: TextView
    private lateinit var scrollView: ObservableScrollView
    private lateinit var titleTextView: TextView
    private lateinit var mAuthorTextView: TextView
    private lateinit var mFeedTitleTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let { arguments ->
            _id = arguments.getLong(ARG_ID, ID_UNSET)
        }

        if (_id > ID_UNSET) {
            val itemId = _id
            val appContext = context?.applicationContext
            appContext?.let {
                val db = AppDatabase.getInstance(appContext)
                launch(Dispatchers.Default) {
                    db.feedItemDao().markAsReadAndNotified(itemId)
                    cancelNotification(it, itemId)
                }
            }
        }

        setHasOptionsMenu(true)

        val viewModel = getFeedItemViewModel(_id)
        viewModel.liveItem.observe(this, androidx.lifecycle.Observer {
            rssItem = it

            rssItem?.let { rssItem ->
                setViewTitle()

                mFeedTitleTextView.text = rssItem.feedDisplayTitle

                rssItem.pubDate.let { pubDate ->
                    rssItem.author.let { author ->
                        when {
                            author == null && pubDate != null ->
                                mAuthorTextView.text = getString(R.string.on_date,
                                        pubDate.withZone(DateTimeZone.getDefault())
                                                .toString(dateTimeFormat))
                            author != null && pubDate != null ->
                                mAuthorTextView.text = getString(R.string.by_author_on_date,
                                        // Must wrap author in unicode marks to ensure it formats
                                        // correctly in RTL
                                        unicodeWrap(author),
                                        pubDate.withZone(DateTimeZone.getDefault())
                                                .toString(dateTimeFormat))
                            else -> mAuthorTextView.visibility = View.GONE
                        }
                    }
                }
            }
        })

        viewModel.liveImageText.observe(this, androidx.lifecycle.Observer {
            bodyTextView.text = it
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val theLayout = if (TabletUtils.isTablet(activity)) {
            R.layout.fragment_reader_tablet
        } else {
            R.layout.fragment_reader
        }
        val rootView = inflater.inflate(theLayout, container, false)

        scrollView = rootView.findViewById<View>(R.id.scroll_view) as ObservableScrollView
        titleTextView = rootView.findViewById<View>(R.id.story_title) as TextView
        bodyTextView = rootView.findViewById<View>(R.id.story_body) as TextView
        mAuthorTextView = rootView.findViewById<View>(R.id.story_author) as TextView
        mFeedTitleTextView = rootView.findViewById<View>(R.id
                .story_feedtitle) as TextView

        return rootView
    }

    private fun setViewTitle() {
        rssItem?.let { rssItem ->
            if (rssItem.title.isEmpty()) {
                titleTextView.text = rssItem.plainTitle
            } else {
                titleTextView.text = toSpannedWithNoImages(activity!!, rssItem.title, rssItem.feedUrl, activity!!.maxImageSize())
            }
        }
    }

    override fun onActivityCreated(bundle: Bundle?) {
        super.onActivityCreated(bundle)
        // TODO
/*        scrollView.let {
            (activity as BaseActivity).enableActionBarAutoHide(it)
        }*/
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rssItem?.storeInBundle(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.reader, menu)

        // Locate MenuItem with ShareActionProvider
        val shareItem = menu!!.findItem(R.id.action_share)

        // Fetch and store ShareActionProvider
        val shareActionProvider = MenuItemCompat.getActionProvider(shareItem) as ShareActionProvider

        // Set intent
        rssItem?.let { rssItem ->

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, rssItem.link)
            shareActionProvider.setShareIntent(shareIntent)

            // Show/Hide enclosure
            menu.findItem(R.id.action_open_enclosure).isVisible = rssItem.enclosureLink != null
            // Add filename to tooltip
            if (rssItem.enclosureLink != null) {
                val filename = rssItem.enclosureFilename
                if (filename != null) {
                    menu.findItem(R.id.action_open_enclosure).title = filename
                }

            }
        }

        // Don't forget super call here
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem?): Boolean {
        return when (menuItem!!.itemId) {
            R.id.action_open_in_webview -> {
                // Open in web view
                rssItem?.let { rssItem ->
                    rssItem.link?.let { link ->
                        findNavController().navigate(
                                R.id.action_readerFragment_to_readerWebViewFragment,
                                bundle {
                                    putString(ARG_URL, link)
                                    putString(ARG_ENCLOSURE, rssItem.enclosureLink)
                                }
                        )
                    }
                }
                true
            }
            R.id.action_open_in_browser -> {
                val link = rssItem?.link
                if (link != null) {
                    context?.let { context ->
                        openLinkInBrowser(context, link)
                    }
                }

                true
            }
            R.id.action_open_enclosure -> {
                val link = rssItem?.enclosureLink
                if (link != null) {
                    context?.let { context ->
                        openLinkInBrowser(context, link)
                    }
                }

                true
            }
            R.id.action_mark_as_unread -> {
                context?.applicationContext?.let {
                    val db = AppDatabase.getInstance(it)
                    launch(Dispatchers.Default) {
                        db.feedItemDao().markAsRead(_id, unread = true)
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(menuItem)
        }
    }
}

fun Fragment.unicodeWrap(text: String): String =
        BidiFormatter.getInstance(getLocale()).unicodeWrap(text)

fun Fragment.getLocale(): Locale? =
        context?.getLocale()

fun Context.unicodeWrap(text: String): String =
        BidiFormatter.getInstance(getLocale()).unicodeWrap(text)

fun Context.getLocale(): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
