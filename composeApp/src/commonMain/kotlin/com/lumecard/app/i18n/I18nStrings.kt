package com.lumecard.app.i18n

interface I18nStrings {
    val appName: String get() = "LumeCard"

    val navHome: String
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

    val errorTitle: String
    val errorDesc: String
    val errorUnknown: String
    val crashAppError: String
    val crashAppErrorDesc: String
    val crashCompositionError: String
    val crashRenderErrorDesc: String
    val timeHours: String
    val timeMinutes: String
    val timeSeconds: String

    val dashStartLearning: String
    fun dashActivePlans(count: Int): String
    val dashPendingReview: String
    fun dashDuePlans(count: Int): String
    val dashDueCardsLabel: String
    fun dashDueCardsCount(count: Int): String
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
    val studyCompleteReviewed: String
    val studyCompleteTimeSpent: String
    val studyCompleteXpEarned: String
    val studySwipeEasy: String
    val studySwipeBack: String
    val studyQuestion: String
    val studyAnswer: String
    val studyRevealed: String
    val studyClozeHint: String

    val studyCardTypeBasic: String
    val studyCardTypeReversed: String
    val studyCardTypeCloze: String
    val studyCardTypeChoice: String
    val studyCardTypeMarkdown: String
    val studyCardTypeAi: String
    val studyCardTypeRichText: String
    val studyModeTitle: String
    val studyModeDesc: String
    val studyContinueAll: String
    fun studyNewCards(count: Int): String
    fun studyRandom(count: Int): String

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
    fun deckSortByLabel(field: String): String
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
    val cardHorizontalCenter: String
    val cardVerticalCenter: String
    val cardFont: String
    val cardFontSize: String
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
    fun cardSortByLabel(field: String): String
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
    val cardTypeMarkdown: String
    val cardTypeAi: String
    val cardTypeRichText: String
    val cardTypeBasicDesc: String
    val cardTypeReversedDesc: String
    val cardTypeClozeDesc: String
    val cardTypeChoiceDesc: String
    val cardTypeMarkdownDesc: String
    val cardTypeAiDesc: String
    val cardTypeRichTextDesc: String
    val cardTypeBasicHelp: String
    val cardTypeReversedHelp: String
    val cardTypeClozeHelp: String
    val cardTypeChoiceHelp: String
    val cardTypeMarkdownHelp: String
    val cardTypeAiHelp: String
    val cardTypeRichTextHelp: String
    val cardTypeBasicExample: String
    val cardTypeReversedExample: String
    val cardTypeClozeExample: String
    val cardTypeChoiceExample: String
    val cardTypeMarkdownExample: String
    val cardTypeAiExample: String
    val cardTypeRichTextExample: String
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
    val settingsFontTitle: String
    val settingsFontImport: String
    val settingsFontImportSuccess: String
    val settingsFontImportFailed: String
    val settingsFontDeleteConfirm: String
    val settingsExport: String
    val settingsExportDesc: String
    fun settingsExportSuccess(length: Int): String
    fun settingsExportError(msg: String): String
    val settingsImport: String
    val settingsImportDesc: String
    val settingsImportHint: String
    fun settingsImportError(msg: String): String
    val settingsImportErrorReadFile: String
    val settingsImportErrorInvalidJson: String
    fun settingsImportSuccess(kbs: Int, decks: Int, cards: Int): String
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
    val updateInstallFailed: String
    val updateDownloadFailed: String
    val updatePublishedAt: String
    val updateCopyError: String
    val updateCopySuccess: String
    fun updateErrorCopyFormat(version: String, error: String, time: String): String
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
    val settingsSyncConfigSuccess: String
    val settingsSyncAddConfig: String
    val settingsSyncConfigName: String
    val settingsSyncConfigNamePlaceholder: String
    val settingsSyncLastSync: String
    val settingsSyncNever: String
    val settingsSyncTestConnection: String
    val settingsSyncTestConnecting: String
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
    val syncNoHistoryFound: String
    val syncFailedToLoadHistory: String
    val syncRestoreHistory: String
    val syncNoHistoryAvailable: String
    fun syncHistoryEntryFormat(timestamp: String, deviceId: String): String
    fun syncIntervalMinutes(minutes: Int): String
    val webdavProviderLabel: String
    val webdavProviderCustom: String
    val webdavProviderJianguoyun: String
    val webdavProviderNextcloud: String
    val webdavProviderOwncloud: String
    val webdavProviderSyncthing: String
    val settingsLanguage: String
    val settingsLanguageDesc: String
    val settingsFontScale: String

    val aiTitle: String
    val aiConfig: String
    val aiConfigDesc: String
    val aiProvider: String
    val aiProviderCustom: String
    val aiProtocol: String
    val aiModel: String
    val aiFetchModels: String
    val aiModelListNotSupported: String
    val aiApiKey: String
    val aiBaseUrl: String
    val aiSystemPrompt: String
    val aiTemperature: String
    val aiContextWindow: String
    val aiTopP: String
    val aiFrequencyPenalty: String
    val aiPresencePenalty: String
    val aiTestConnection: String
    val aiTestConnecting: String
    val aiTestSuccess: String
    fun aiTestError(msg: String): String
    val aiAddConfig: String
    val aiConfigName: String
    val aiConfigNamePlaceholder: String
    val aiDeleteConfirm: String
    val aiSetDefault: String
    val aiDefault: String
    val aiNotConfigured: String
    val aiConnectionStatus: String
    val aiConnected: String
    val aiDisconnected: String
    val aiSaveSuccess: String
    val aiDeleteSuccess: String

    // AI Card Generation
    val aiCardGeneration: String
    val aiCardGenerationDesc: String
    val aiCardModeAuto: String
    val aiCardModeAutoDesc: String
    val aiCardModeKb: String
    val aiCardModeKbDesc: String
    val aiCardModeBoth: String
    val aiCardModeBothDesc: String
    val aiCardSelectKb: String
    val aiCardSelectDeck: String
    val aiCardSelectConfig: String
    val aiCardSelectConfigLabel: String
    val aiCardNoConfig: String
    val aiCardNoConfigDesc: String
    val aiCardNoKb: String
    val aiCardNoDeck: String
    val aiCardTopic: String
    val aiCardTopicPlaceholder: String
    val aiCardMaterials: String
    val aiCardMaterialsPlaceholder: String
    val aiCardImportFile: String
    val aiCardImportFileError: String
    val aiCardSupportedFormats: String
    val aiCardCount: String
    val aiCardCountDesc: String
    val aiCardAdditionalReqs: String
    val aiCardAdditionalReqsPlaceholder: String
    fun aiCardLargeCountConfirm(count: Int): String
    val aiCardPrompt: String
    val aiCardPromptCopy: String
    val aiCardPromptCopied: String
    val aiCardPromptRestore: String
    val aiCardPromptRestoreConfirm: String
    val aiCardPromptRestored: String
    val aiCardGenerate: String
    val aiCardClear: String
    val aiCardGenerating: String
    fun aiCardResultCreated(count: Int): String
    val aiCardResultDesc: String
    val aiCardConfirmLargeCountTitle: String
    val aiCardErrorNoConfig: String
    val aiCardErrorConnection: String
    val aiCardErrorAuth: String
    val aiCardErrorApi: String
    val aiCardErrorParse: String
    val aiCardErrorRateLimit: String
    val aiCardErrorTimeout: String
    val aiCardErrorNoContent: String
    val aiCardAutoClassify: String
    val aiCardAutoClassifyDesc: String
    val aiCardTopicRequired: String
    val aiCardCancelled: String
    fun aiCardErrorLoadData(message: String): String
    fun aiCardErrorBatch(batch: Int, message: String): String
    fun aiCardErrorGeneration(message: String): String

    // Move / Merge
    val actionMove: String
    val actionMerge: String
    val moveDeckTitle: String
    val moveCardTitle: String
    val mergeKbTitle: String
    val mergeDeckTitle: String
    fun mergeKbConfirm(source: String, target: String): String
    fun mergeDeckConfirm(source: String, target: String): String
    fun moveDeckConfirm(deck: String, targetKb: String): String
    fun moveCardConfirm(card: String, targetDeck: String): String
    val moveMergeSuccess: String
    fun moveMergeResult(items: Int, conflicts: Int): String
    val mergeDeckConflictDesc: String
    val mergeKbConflictDesc: String
    val confirmNoPrompt60s: String
    val moveDeckNoTarget: String
    val selectTargetKb: String
    val selectTargetDeck: String
    val operationMove: String
    val operationMerge: String

    val kbTitle: String
    val kbCreate: String
    val kbEdit: String
    val kbEmpty: String
    val kbDeleteConfirm: String
    val kbDeleteConfirmDesc: String

    val planTitle: String
    val planCreate: String
    val planEdit: String
    val planCreated: String
    val planUpdated: String
    val planSavedDescCreate: String
    val planSavedDescUpdate: String
    val planEmpty: String
    val planDeleteConfirm: String
    val planDeleteConfirmDesc: String
    val planStatusNotStarted: String
    val planStatusInProgress: String
    val planStatusCompleted: String
    val planDefault: String
    fun planAutoName(deckCount: Int): String
    val planRandom: String
    val planRandomDesc: String
    val planSelectToStart: String
    val planProgress: String
    val planResetConfirm: String
    val planResetConfirmDesc: String
    val planReview: String
    val planLearn: String
    val planAllDoneTitle: String
    val planAllDoneDesc: String
    val planReviewAgain: String
    fun planCardsCount(count: Int): String

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
    val warehouseExpandAll: String
    val warehouseCollapseAll: String
    fun warehouseSelectedCount(count: Int): String
    fun warehouseChildCount(count: Int): String

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
    val statsForecast: String
    val statsForecastBacklog: String
    fun statsForecastBacklogValue(count: Int): String
    val statsForecastDueToday: String
    val statsForecastDue1Month: String
    val statsForecastDue3Months: String
    val statsForecastDue6Months: String
    val statsForecastDue1Year: String
    val statsForecastDueAllTime: String
    val statsReviewIntervals: String
    val statsReviewIntervalAvg: String
    fun statsReviewIntervalAvgValue(days: Double): String
    val statsReviewIntervalMax: String
    fun statsReviewIntervalMaxValue(days: Int): String
    val statsRetention: String
    val statsRetentionYoung: String
    val statsRetentionMature: String
    val statsRetentionOverall: String
    val statsRetentionPeriodToday: String
    val statsRetentionPeriodYesterday: String
    val statsRetentionPeriodWeek: String
    val statsRetentionPeriodMonth: String
    val statsRetentionPeriodYear: String
    val statsRetentionPeriodAllTime: String
    val statsCardCountsNew: String
    val statsCardCountsLearning: String
    val statsCardCountsYoung: String
    val statsCardCountsMature: String

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

    val actionCopy: String
    val actionSelectAll: String
    val actionPreview: String
    val editorPlaceholder: String
    val editorEmptyPreview: String
    val editorColorTitle: String
    val editorColorNone: String
    val editorColorRed: String
    val editorColorBlue: String
    val editorColorGreen: String
    val editorColorOrange: String
    val editorColorPurple: String
    val editorColorTeal: String
    val editorColorBrown: String
    val editorColorGray: String
    val editorColorCustom: String
    fun editorFontSizePx(size: Int): String
    val mermaidChartTitle: String
    fun deckNameLabel(name: String): String
    fun deckListTitle(name: String): String
    fun cardListTitle(name: String): String

    val noteMarkdownSupport: String
    val noteOptional: String

    val pasteMedia: String
    val browseMedia: String
}
