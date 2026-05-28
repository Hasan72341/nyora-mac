package com.nyora.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SortDirection { ASC, DESC }

@Serializable
enum class ListSortOrder {
    UPDATED, RATING, POPULARITY, DATE, NAME, NEWEST, ALPHABETICAL,
}

@Serializable
enum class ZoomMode {
    FIT_CENTER, FIT_HEIGHT, FIT_WIDTH, KEEP_START,
}

@Serializable
enum class QuickFilter {
    ALL, READING, UNREAD, COMPLETED, DOWNLOADED, NEW,
}
