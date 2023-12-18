package com.mentalmachines.droidconboston.views.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.mentalmachines.droidconboston.R
import com.mentalmachines.droidconboston.data.FirebaseDatabase
import com.mentalmachines.droidconboston.data.Schedule
import com.mentalmachines.droidconboston.data.UserAgendaRepo
import com.mentalmachines.droidconboston.firebase.FirebaseHelper
import com.mentalmachines.droidconboston.views.rating.RatingRepo
import timber.log.Timber

class AgendaDetailViewModel(
    private val scheduleRowItem: Schedule.ScheduleRow,
    private val userAgendaRepo: UserAgendaRepo,
    private val ratingRepo: RatingRepo,
    private val firebaseHelper: FirebaseHelper = FirebaseHelper.instance
) : ViewModel() {
    private val eventSpeakers: HashMap<String, FirebaseDatabase.EventSpeaker> = hashMapOf()

    private val _scheduleDetail = MutableLiveData<Schedule.ScheduleDetail>()
    val scheduleDetail: LiveData<Schedule.ScheduleDetail> = _scheduleDetail

    private val _ratingValue = MutableLiveData<Int>()
    val ratingValue: LiveData<Int> = _ratingValue

    private val speakerDataListener: ValueEventListener = object : ValueEventListener {
        override fun onCancelled(databaseError: DatabaseError) {
            Timber.e(databaseError.toException())
        }

        override fun onDataChange(databaseSnapshot: DataSnapshot) {
            databaseSnapshot.children.forEach { speakerSnapshot ->
                val speaker = speakerSnapshot.getValue(FirebaseDatabase.EventSpeaker::class.java)
                speaker?.let {
                    eventSpeakers[speaker.name] = speaker

                    if (scheduleRowItem.primarySpeakerName == speaker.name) {
                        _scheduleDetail.value = speaker.toScheduleDetail(scheduleRowItem)
                    }
                }
            }
        }
    }

    val talkTitle: String
        get() = scheduleRowItem.talkTitle

    val room: String?
        get() = scheduleRowItem.room

    val startTime: String
        get() = scheduleRowItem.startTime

    val endTime: String
        get() = scheduleRowItem.endTime

    val schedulerowId: String
        get() = scheduleRowItem.id

    val speakerNames: List<String>
        get() = scheduleRowItem.speakerNames

    private val sessionId: String
        get() = scheduleDetail.value?.id.orEmpty()

    val isBookmarked: Boolean
        get() {
            return sessionId.isNotEmpty() && userAgendaRepo.isSessionBookmarked(sessionId)
        }

    val bookmarkSnackbarRes: Int
        get() = if (isBookmarked) R.string.saved_agenda_item else R.string.removed_agenda_item

    val bookmarkColorRes: Int
        get() = if (isBookmarked) R.color.colorAccent else R.color.colorLightGray

    fun loadData() {
        if (scheduleRowItem.hasSpeaker()) {
            // need to get speaker social media links
            firebaseHelper.speakerDatabase.orderByChild("name").addValueEventListener(speakerDataListener)
        } else {
            _scheduleDetail.value = Schedule.ScheduleDetail(scheduleRowItem)
        }

        ratingRepo.getSessionFeedback(scheduleRowItem.id) { sessionFeedback ->
            sessionFeedback?.let {
                _ratingValue.value = it.rating
            }
        }
    }

    fun removeListener() {
        firebaseHelper.speakerDatabase.removeEventListener(speakerDataListener)
    }

    fun getSpeaker(speakerName: String): FirebaseDatabase.EventSpeaker? {
        return eventSpeakers[speakerName]
    }

    fun getOrganizationForSpeaker(speakerName: String): String? {
        return scheduleRowItem.speakerNameToOrgName[speakerName]
    }

    fun getPhotoForSpeaker(speakerName: String): String? {
        return scheduleRowItem.photoUrlMap[speakerName]
    }

    /**
     * Returns the twitter handle for a user if we have one, otherwise returns the speaker name.
     */
    private fun getTwitterHandleForSpeaker(speakerName: String): String {
        val handle = eventSpeakers[speakerName]?.socialProfiles?.get("twitter")

        return if (handle.isNullOrEmpty()) {
            speakerName
        } else {
            "@$handle"
        }
    }

    /**
     * Builds the twitter handle string for multiple speakers.
     */
    fun getTwitterHandleForAllSpeakers(detail: Schedule.ScheduleDetail): String {
        return detail.listRow.speakerNames.joinToString(
            separator = " and ",
            transform = this::getTwitterHandleForSpeaker
        )
    }

    fun toggleBookmark() {
        val nextBookmarkStatus = !isBookmarked
        userAgendaRepo.bookmarkSession(sessionId, nextBookmarkStatus)
    }
}
