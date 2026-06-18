package com.lumecard.app.i18n

interface I18nStrings {
    val appName: String get() = "LumeCard"

    val navHome: String
    val navDecks: String
    val navSettings: String
    val navStats: String

    val actionBack: String
    val actionSave: String
    val actionCancel: String
    val actionDelete: String
    val actionConfirm: String
    val actionEdit: String
    val actionCreate: String
    val actionLearning: String
    val actionOk: String
    val actionDone: String
    val actionSearch: String
    val actionSort: String
    val actionConfigure: String
    val actionSync: String
    val actionRetry: String

    val dashStartLearning: String
    fun dashActivePlans(count: Int): String
    fun dashKBCount(count: Int): String
    val dashManageDecks: String
    val dashQuickActions: String
    val dashTodayStudy: String
    val dashTotalCards: String
    val dashDecksWithCards: String
    val dashDecksLabel: String
    val dashNoCardsAvailable: String
    val dashNoDecksYet: String
    val dashCreateFirstDeck: String
    val dashBeginJourney: String
    val dashJourneyDesc: String
    val dashMyDecks: String
    fun dashDecksAvailable(count: Int): String
    fun dashCardsCount(count: Int): String

    val modeMixed: String
    val modeMixedDesc: String
    val modeSingle: String
    val modeSingleDesc: String
    val modeMulti: String
    val modeMultiDesc: String
    val modeTitle: String
    val modeSelectDecks: String
    val modeNoDecks: String
    val modeCardsTotal: String
    fun modeCardsCount(count: Int): String
    val modeStartMixed: String
    val modeStartSingle: String
    val modeStartMulti: String
    val modeStartLearning: String
    val modeSelectMode: String

    val studyShowAnswer: String
    val studyPreviousCard: String
    val studyRatingForgot: String
    val studyRatingHard: String
    val studyRatingGood: String
    val studyRatingEasy: String
    val studyRatingPrompt: String
    val studyProgress: String
    fun studyProgressText(current: Int, total: Int): String
    val studyNoCards: String
    val studyGoCreateCards: String
    val studyComplete: String
    fun studyCompleteMsg(count: Int): String
    val studySwipeEasy: String
    val studySwipeBack: String
    val studyQuestion: String
    val studyAnswer: String
    val studyRevealed: String
    val studyClozeHint: String
    val studyImageHint: String
    val studyCardTypeBasic: String
    val studyCardTypeReversed: String
    val studyCardTypeCloze: String
    val studyCardTypeChoice: String
    val studyCardTypeOcclusion: String
    val studyCardTypeAudio: String
    val studyCardTypeVideo: String
    val studyCardTypeMarkdown: String
    val studyCardTypeAi: String

    val deckCreate: String
    val deckEdit: String
    val deckDelete: String
    val deckName: String
    val deckDesc: String
    val deckNamePlaceholder: String
    val deckDescPlaceholder: String
    val deckConfirmDelete: String
    fun deckDeleteConfirmText(name: String): String
    val deckDeleted: String
    val deckSortName: String
    val deckSortCreated: String
    val deckSortModified: String
    val deckSortStudyTime: String
    val deckSortTitle: String
    val deckEmpty: String
    val deckEmptyDesc: String
    val deckViewCards: String
    val deckManageTitle: String
    val deckListTitle: String
    fun deckCardsCount(count: Int): String

    val cardCreate: String
    val cardEdit: String
    val cardDelete: String
    val cardType: String
    val cardPreview: String
    val cardQuestionLabel: String
    val cardAnswerLabel: String
    val cardFrontLabel: String
    val cardBackLabel: String
    val cardFrontLabelRev: String
    val cardBackLabelRev: String
    val cardFrontPlaceholder: String
    val cardBackPlaceholder: String
    val cardTags: String
    val cardTagsPlaceholder: String
    val cardTagsHint: String
    val cardTypeHelp: String
    val cardCollapse: String
    val cardExpand: String
    val cardExample: String
    val cardSortTitle: String
    val cardSortName: String
    val cardSortCreated: String
    val cardSortModified: String
    val cardSortStudyTime: String
    val cardEmpty: String
    val cardEmptyDesc: String
    val cardAdd: String
    val cardNoCards: String
    val cardNoCardsDesc: String
    val cardSaved: String

    val cardTypeBasic: String
    val cardTypeReversed: String
    val cardTypeCloze: String
    val cardTypeChoice: String
    val cardTypeOcclusion: String
    val cardTypeAudio: String
    val cardTypeVideo: String
    val cardTypeMarkdown: String
    val cardTypeAi: String
    val cardTypeBasicDesc: String
    val cardTypeReversedDesc: String
    val cardTypeClozeDesc: String
    val cardTypeChoiceDesc: String
    val cardTypeOcclusionDesc: String
    val cardTypeAudioDesc: String
    val cardTypeVideoDesc: String
    val cardTypeMarkdownDesc: String
    val cardTypeAiDesc: String
    val cardTypeBasicHelp: String
    val cardTypeReversedHelp: String
    val cardTypeClozeHelp: String
    val cardTypeChoiceHelp: String
    val cardTypeOcclusionHelp: String
    val cardTypeAudioHelp: String
    val cardTypeVideoHelp: String
    val cardTypeMarkdownHelp: String
    val cardTypeAiHelp: String
    val cardTypeBasicExample: String
    val cardTypeReversedExample: String
    val cardTypeClozeExample: String
    val cardTypeChoiceExample: String
    val cardTypeOcclusionExample: String
    val cardTypeAudioExample: String
    val cardTypeVideoExample: String
    val cardTypeMarkdownExample: String
    val cardTypeAiExample: String
    val cardClozeContent: String
    val cardClozeFormatHint: String
    val cardClozePlaceholder: String
    val cardClozeFullText: String
    val cardClozeBackPlaceholder: String
    val cardChoiceQuestion: String
    val cardChoiceQuestionPlaceholder: String
    val cardChoiceOptions: String
    val cardChoiceFormatHint: String
    val cardChoicePlaceholder: String
    val cardOcclusionImage: String
    val cardOcclusionImagePlaceholder: String
    val cardOcclusionImageHint: String
    val cardOcclusionContent: String
    val cardOcclusionContentPlaceholder: String
    val cardAudioRef: String
    val cardAudioPlaceholder: String
    val cardAudioHint: String
    val cardAudioContent: String
    val cardAudioContentPlaceholder: String
    val cardVideoRef: String
    val cardVideoPlaceholder: String
    val cardVideoHint: String
    val cardVideoContent: String
    val cardVideoContentPlaceholder: String
    val cardMarkdownHint: String

    val settingsTitle: String
    val settingsLearning: String
    val settingsDailyGoal: String
    val settingsDailyGoalDesc: String
    fun settingsDailyGoalValue(count: Int): String
    val settingsNewCards: String
    val settingsNewCardsDesc: String
    val settingsReviewMode: String
    val settingsAnswerDisplay: String
    val settingsAnswerMode: String
    val settingsAppearance: String
    val settingsDarkMode: String
    val settingsDarkModeDesc: String
    val settingsNotifications: String
    val settingsDailyReminder: String
    val settingsDailyReminderDesc: String
    val settingsDataManagement: String
    val settingsExport: String
    val settingsExportDesc: String
    fun settingsExportSuccess(length: Int): String
    fun settingsExportError(msg: String): String
    val settingsImport: String
    val settingsImportDesc: String
    val settingsImportHint: String
    fun settingsImportError(msg: String): String
    val settingsCloudSync: String
    val settingsCloudSyncDesc: String
    val settingsTodayProgress: String
    fun settingsProgressText(current: Int, target: Int): String
    val settingsTodayCompleted: String
    val settingsNewCardsLearned: String
    val settingsAbout: String
    val settingsVersion: String
    val settingsAboutApp: String

    val updateChecking: String
    val updateCheckingDesc: String
    val updateAvailable: String
    val updateUpToDate: String
    val updateUpToDateDesc: String
    val updateCurrentVersion: String
    val updateLatestVersion: String
    val updateReleaseNotes: String
    val updateDownload: String
    val updateError: String
    val actionClose: String
    val settingsCheckUpdate: String
    val updateDownloading: String
    val updateInstalling: String
    val updateComplete: String
    val updateCompleteDesc: String
    val updatePublishedAt: String
    val updateCopyError: String
    val updateCopySuccess: String
    val exportErrorPermission: String
    val exportErrorDiskSpace: String
    val exportErrorWrite: String

    val settingsDeveloper: String
    val settingsLicense: String
    val settingsOpenSource: String
    val settingsGoalDialogTitle: String
    val settingsCardCount: String
    val settingsSyncDialogTitle: String
    val settingsWebdavUrl: String
    val settingsWebdavUser: String
    val settingsWebdavPass: String
    val settingsSyncing: String
    fun settingsSyncSuccess(decksCount: Int): String
    fun settingsSyncError(msg: String): String
    val settingsSyncAddConfig: String
    val settingsSyncConfigName: String
    val settingsSyncConfigNamePlaceholder: String
    val settingsSyncLastSync: String
    val settingsSyncNever: String
    val settingsSyncTestConnection: String
    val settingsSyncTestSuccess: String
    fun settingsSyncTestError(msg: String): String
    val settingsSyncDeleteConfirm: String
    val settingsSyncSetDefault: String
    val settingsSyncDefault: String
    val settingsSyncUpload: String
    val settingsSyncDownload: String
    val settingsSyncBiDirectional: String
    val settingsSyncNotConfigured: String
    val settingsAutoSync: String
    val settingsAutoSyncDesc: String
    val settingsAutoSyncInterval: String
    val settingsAutoSyncMin15: String
    val settingsAutoSyncMin30: String
    val settingsAutoSyncMin60: String
    val settingsAutoSyncMin120: String
    val settingsAutoSyncRunning: String
    val settingsAutoSyncStopped: String
    val settingsSyncScope: String
    val settingsSyncScopeAll: String
    val settingsSyncScopeSettings: String
    val settingsSyncScopeData: String
    val settingsSyncNow: String
    val settingsSyncData: String
    val settingsSyncConfig: String
    val settingsRestoreFromCloud: String
    val settingsRestoreConfirm: String
    val settingsRestoreConfirmDesc: String
    val settingsLastSyncTime: String
    val settingsConnectionStatus: String
    val settingsConnected: String
    val settingsDisconnected: String
    val webdavProviderLabel: String
    val webdavProviderCustom: String
    val webdavProviderJianguoyun: String
    val webdavProviderNextcloud: String
    val webdavProviderOwncloud: String
    val webdavProviderSyncthing: String
    val settingsLanguage: String
    val settingsLanguageDesc: String

    val kbTitle: String
    val kbCreate: String
    val kbEdit: String
    val kbEmpty: String
    val kbDeleteConfirm: String
    val kbDeleteConfirmDesc: String

    val planTitle: String
    val planCreate: String
    val planEdit: String
    val planEmpty: String
    val planDeleteConfirm: String
    val planDeleteConfirmDesc: String
    val planStatusNotStarted: String
    val planStatusInProgress: String
    val planStatusCompleted: String
    val planDefault: String
    val planRandom: String
    val planRandomDesc: String
    val planSelectToStart: String
    val planProgress: String
    val planResetConfirm: String
    val planResetConfirmDesc: String

    val fieldName: String
    val fieldDescription: String

    val warehouseTitle: String
    val warehouseSearch: String
    val warehouseEmpty: String
    val warehouseAdd: String
    val warehouseCreateKB: String
    val warehouseCreateDeck: String
    val warehouseCreateCard: String
    val warehouseEdit: String
    val warehouseCardContent: String
    val warehouseDeleteConfirm: String
    val warehouseDeleteDesc: String

    val statsTitle: String
    val statsOverview: String
    val statsTotalCards: String
    val statsTotalDecks: String
    val statsTotalReviews: String
    val statsTodayLearning: String
    val statsTodayReviews: String
    val statsRetentionRate: String
    fun statsRetentionValue(rate: Double): String
    val statsStudyTime: String
    fun statsStudyTimeValue(minutes: Int): String
    val statsStreak: String
    fun statsStreakValue(days: Int): String
    val statsTimeStats: String
    val statsThisWeek: String
    fun statsThisWeekValue(count: Int): String
    val statsThisMonth: String
    fun statsThisMonthValue(count: Int): String
    val statsTotal: String
    fun statsTotalValue(count: Int): String
    val statsAvgRetention: String
    val statsHabits: String
    val statsStreakDays: String
    val statsCardDistribution: String
    val statsNewCards: String
    val statsDueCards: String
    val statsUpcomingCards: String

    val algoFsrs: String
    val algoFsrsDesc: String
    val algoSm2: String
    val algoSm2Desc: String
    val algoLeitner: String
    val algoLeitnerDesc: String
    val algoSimple: String
    val algoSimpleDesc: String

    val displayFlip: String
    val displayFlipDesc: String
    val displaySplit: String
    val displaySplitDesc: String

    val langSystem: String
    val langZhCn: String
    val langZhTw: String
    val langEn: String
    val langJa: String
    val langEs: String

    val actionPreview: String
    val editorPlaceholder: String
    val editorEmptyPreview: String
    val mermaidChartTitle: String
    fun deckNameLabel(name: String): String

    val noteMarkdownSupport: String
    val noteOptional: String
}
