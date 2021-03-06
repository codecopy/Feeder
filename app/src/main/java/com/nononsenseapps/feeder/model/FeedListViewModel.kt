package com.nononsenseapps.feeder.model

import android.app.Application
import androidx.collection.ArrayMap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModelProviders
import com.nononsenseapps.feeder.coroutines.CoroutineScopedViewModel
import com.nononsenseapps.feeder.db.room.AppDatabase
import com.nononsenseapps.feeder.db.room.ID_ALL_FEEDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedListViewModel(application: Application): CoroutineScopedViewModel(application) {
    private val dao = AppDatabase.getInstance(application).feedDao()
    private val liveFeedsWithUnreadCounts = dao.loadLiveFeedsWithUnreadCounts()

    val liveFeedsAndTagsWithUnreadCounts = MediatorLiveData<List<FeedUnreadCount>>()

    init {
        liveFeedsAndTagsWithUnreadCounts.addSource(liveFeedsWithUnreadCounts) { feeds ->
            launch(Dispatchers.Default) {
                val topTag = FeedUnreadCount(id = ID_ALL_FEEDS)
                val tags: MutableMap<String, FeedUnreadCount> = ArrayMap()
                val data: MutableList<FeedUnreadCount> = mutableListOf(topTag)

                feeds.forEach { feed ->
                    if (feed.tag.isNotEmpty()) {
                        if (!tags.contains(feed.tag)) {
                            val tag = FeedUnreadCount(tag = feed.tag)
                            data.add(tag)
                            tags[feed.tag] = tag
                        }

                        tags[feed.tag]?.let { tag ->
                            tag.unreadCount += feed.unreadCount
                        }
                    }

                    topTag.unreadCount += feed.unreadCount

                    data.add(feed)
                }

                data.sortWith(Comparator { a, b -> a.compareTo(b) })

                liveFeedsAndTagsWithUnreadCounts.postValue(data)
            }
        }
    }
}

fun FragmentActivity.getFeedListViewModel(): FeedListViewModel {
    return ViewModelProviders.of(this).get(FeedListViewModel::class.java)
}
