package com.bettermifitness.sync.i18n

import androidx.compose.runtime.Composable
import com.bettermifitness.sync.MR
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource

/**
 * Single localization surface for Compose, shared Kotlin state, and native SwiftUI.
 * The catalog lives in moko-resources and follows the system language.
 */
object L10n {
    @Composable
    fun string(res: StringResource): String = stringResource(res)

    @Composable
    fun stringFmt(res: StringResource, vararg args: Any): String = stringResource(res, *args)

    /** Non-Compose access for ViewModels and shared platform bridges. */
    fun text(res: StringResource): String = L10nPlatform.text(res)

    fun textFmt(res: StringResource, vararg args: Any): String = L10nPlatform.format(res, args)

    fun metric(key: String): String = text(metricResource(key))

    fun metricResource(key: String): StringResource = when (key) {
        "heart_rate" -> MR.strings.metric_heart_rate
        "resting_heart_rate" -> MR.strings.metric_resting_heart_rate
        "sleep" -> MR.strings.metric_sleep
        "hrv" -> MR.strings.metric_hrv
        "steps" -> MR.strings.metric_steps
        "distance" -> MR.strings.metric_distance
        "active_calories" -> MR.strings.metric_active_calories
        "spo2" -> MR.strings.metric_spo2
        "weight" -> MR.strings.metric_weight
        "workouts" -> MR.strings.metric_workouts
        "blood_pressure" -> MR.strings.metric_blood_pressure
        "temperature" -> MR.strings.metric_temperature
        "vo2_max" -> MR.strings.metric_vo2_max
        else -> MR.strings.metric_workouts
    }

    // Common
    val appName get() = MR.strings.app_name
    val back get() = MR.strings.back
    val openHealth get() = MR.strings.open_health
    val healthFallback get() = MR.strings.health_fallback
    val healthAllowAccess get() = MR.strings.health_allow_access
    val healthReady get() = MR.strings.health_ready
    val healthChecking get() = MR.strings.health_checking
    val healthNotAvailable get() = MR.strings.health_not_available
    val healthPermissionsIncomplete get() = MR.strings.health_permissions_incomplete
    val healthStatusCheckFailed get() = MR.strings.health_status_check_failed
    val healthWriteFailed get() = MR.strings.health_write_failed

    // Login
    val loginTitle get() = MR.strings.login_title
    val loginSubtitle get() = MR.strings.login_subtitle
    val loginEmailOrPhone get() = MR.strings.login_email_or_phone
    val loginPassword get() = MR.strings.login_password
    val loginSignIn get() = MR.strings.login_sign_in
    val loginPrivacy get() = MR.strings.login_privacy
    val loginVerifyIdentity get() = MR.strings.login_verify_identity
    val loginCheckEmail get() = MR.strings.login_check_email
    val loginCodeSent get() = MR.strings.login_code_sent
    val loginCodeSentTo get() = MR.strings.login_code_sent_to
    val loginVerify get() = MR.strings.login_verify
    val loginDidntGet get() = MR.strings.login_didnt_get
    val loginResend get() = MR.strings.login_resend
    val loginBrowserTrouble get() = MR.strings.login_browser_trouble
    val loginBrowserTitle get() = MR.strings.login_browser_title
    val loginComplete get() = MR.strings.login_complete
    val loginStepOne get() = MR.strings.login_step_one
    val loginStepOneDetail get() = MR.strings.login_step_one_detail
    val loginOpenXiaomi get() = MR.strings.login_open_xiaomi
    val loginLinkCopied get() = MR.strings.login_link_copied
    val loginCopyLink get() = MR.strings.login_copy_link
    val loginStepTwo get() = MR.strings.login_step_two
    val loginStepTwoDetail get() = MR.strings.login_step_two_detail
    val loginLinkPasted get() = MR.strings.login_link_pasted
    val loginPasteDifferent get() = MR.strings.login_paste_different
    val loginRemoveLink get() = MR.strings.login_remove_link
    val loginTapPaste get() = MR.strings.login_tap_paste
    val loginCopyFromBrowser get() = MR.strings.login_copy_from_browser
    val loginReady get() = MR.strings.login_ready
    val loginTip get() = MR.strings.login_tip
    val loginNothingToPaste get() = MR.strings.login_nothing_to_paste
    val loginInvalidLink get() = MR.strings.login_invalid_link
    val loginNotWebLink get() = MR.strings.login_not_web_link
    val loginVerificationCode get() = MR.strings.login_verification_code
    val loginEmpty get() = MR.strings.login_empty
    val loginDigits get() = MR.strings.login_digits
    val loginSendCodeFailed get() = MR.strings.login_send_code_failed
    val loginFailed get() = MR.strings.login_failed
    val loginVerificationFailed get() = MR.strings.login_verification_failed
    val loginBrowserRequired get() = MR.strings.login_browser_required
    val loginCodeResent get() = MR.strings.login_code_resent
    val loginResendFailed get() = MR.strings.login_resend_failed
    val loginPasteRedirect get() = MR.strings.login_paste_redirect
    val loginBrowserFinishFailed get() = MR.strings.login_browser_finish_failed
    val loginMissingPassToken get() = MR.strings.login_missing_pass_token
    val loginMissingDeviceId get() = MR.strings.login_missing_device_id

    // Home
    val homeAccount get() = MR.strings.home_account
    val homeSignedIn get() = MR.strings.home_signed_in
    val homeConnected get() = MR.strings.home_connected
    val homeLoadingProfile get() = MR.strings.home_loading_profile
    val homeSetup get() = MR.strings.home_setup
    val homeActivity get() = MR.strings.home_activity
    val homeSyncOptions get() = MR.strings.home_sync_options
    val homeNoneSelected get() = MR.strings.home_none_selected
    val homeMetricOne get() = MR.strings.home_metric_one
    val homeMetricMany get() = MR.strings.home_metric_many
    val homeDayOne get() = MR.strings.home_day_one
    val homeDayMany get() = MR.strings.home_day_many
    val homeAutoOn get() = MR.strings.home_auto_on
    val homeManual get() = MR.strings.home_manual
    val homeNothingSelectedTitle get() = MR.strings.home_nothing_selected_title
    val homeNothingSelectedDetail get() = MR.strings.home_nothing_selected_detail
    val homeAllowAccessDetail get() = MR.strings.home_allow_access_detail
    val homeLastSync get() = MR.strings.home_last_sync
    val homeBackgroundSync get() = MR.strings.home_background_sync
    val homeNever get() = MR.strings.home_never
    val homeSyncing get() = MR.strings.home_syncing
    val homeSyncNow get() = MR.strings.home_sync_now
    val homeOpenSettings get() = MR.strings.home_open_settings
    val homeFailedSuffix get() = MR.strings.home_failed_suffix
    val homePartialSuffix get() = MR.strings.home_partial_suffix
    val homeSettingsAccessibility get() = MR.strings.home_settings_accessibility
    val homeHealthAccessibility get() = MR.strings.home_health_accessibility
    val homeHealthReadyAccessibility get() = MR.strings.home_health_ready_accessibility
    val homeSettingsHint get() = MR.strings.home_settings_hint

    // Sync
    val syncTitle get() = MR.strings.sync_title
    val syncLastOneDay get() = MR.strings.sync_last_one_day
    val syncLastNDays get() = MR.strings.sync_last_n_days
    val syncNow get() = MR.strings.sync_now
    val syncOpenHealth get() = MR.strings.sync_open_health
    val syncNoMetrics get() = MR.strings.sync_no_metrics
    val syncPreparing get() = MR.strings.sync_preparing
    val syncStatusSyncing get() = MR.strings.sync_status_syncing
    val syncStatusDone get() = MR.strings.sync_status_done
    val syncStatusFailed get() = MR.strings.sync_status_failed
    val syncStatusWaiting get() = MR.strings.sync_status_waiting
    val syncRecords get() = MR.strings.sync_records
    val syncFailed get() = MR.strings.sync_failed
    val syncNotLoggedIn get() = MR.strings.sync_not_logged_in
    val syncNothingToSync get() = MR.strings.sync_nothing_to_sync
    val syncAllMetricsSynced get() = MR.strings.sync_all_metrics_synced
    val syncHealthUnavailable get() = MR.strings.sync_health_unavailable

    // Relative time
    val timeJustNow get() = MR.strings.time_just_now
    val timeMinuteAgo get() = MR.strings.time_minute_ago
    val timeHourOneAgo get() = MR.strings.time_hour_one_ago
    val timeHourManyAgo get() = MR.strings.time_hour_many_ago
    val timeYesterday get() = MR.strings.time_yesterday
    val timeDayOneAgo get() = MR.strings.time_day_one_ago
    val timeDayManyAgo get() = MR.strings.time_day_many_ago
    val timeUnknown get() = MR.strings.time_unknown

    // Outcome/status
    val outcomeUpToDate get() = MR.strings.outcome_up_to_date
    val outcomeAlmostDone get() = MR.strings.outcome_almost_done
    val outcomeCouldNotFinish get() = MR.strings.outcome_could_not_finish
    val outcomePleaseSignIn get() = MR.strings.outcome_please_sign_in
    val outcomePartialLabel get() = MR.strings.outcome_partial_label
    val outcomeHealthNeeded get() = MR.strings.outcome_health_needed
    val outcomeNothingToDo get() = MR.strings.outcome_nothing_to_do
    val outcomeStopped get() = MR.strings.outcome_stopped
    val outcomeInProgress get() = MR.strings.outcome_in_progress
    val outcomeNotSynced get() = MR.strings.outcome_not_synced
    val outcomeSuccessDetail get() = MR.strings.outcome_success_detail
    val outcomePartialDetail get() = MR.strings.outcome_partial_detail
    val outcomeFailedDetail get() = MR.strings.outcome_failed_detail
    val outcomeLoginDetail get() = MR.strings.outcome_login_detail
    val outcomeHealthDetail get() = MR.strings.outcome_health_detail
    val outcomeSkippedDetail get() = MR.strings.outcome_skipped_detail
    val outcomeCancelledDetail get() = MR.strings.outcome_cancelled_detail
    val outcomeRunningDetail get() = MR.strings.outcome_running_detail
    val outcomeIdleDetail get() = MR.strings.outcome_idle_detail
    val outcomeNothingNeeded get() = MR.strings.outcome_nothing_needed
    val outcomeSignInAgain get() = MR.strings.outcome_sign_in_again
    val outcomeHealthUnavailable get() = MR.strings.outcome_health_unavailable

    // Settings
    val settingsTitle get() = MR.strings.settings_title
    val settingsSyncOptionsLoading get() = MR.strings.settings_sync_options_loading
    val settingsSectionSyncRange get() = MR.strings.settings_section_sync_range
    val settingsSectionAutoSync get() = MR.strings.settings_section_auto_sync
    val settingsSectionMetrics get() = MR.strings.settings_section_metrics
    val settingsSectionStatus get() = MR.strings.settings_section_status
    val settingsSectionShortcuts get() = MR.strings.settings_section_shortcuts
    val settingsSectionAccount get() = MR.strings.settings_section_account
    val settingsSectionAbout get() = MR.strings.settings_section_about
    val settingsDaysPicker get() = MR.strings.settings_days_picker
    val settingsRange1Day get() = MR.strings.settings_range_1_day
    val settingsRangeNDays get() = MR.strings.settings_range_n_days
    val settingsBackgroundAutoSync get() = MR.strings.settings_background_auto_sync
    val settingsTestBackgroundRefresh get() = MR.strings.settings_test_background_refresh
    val settingsRunningRefresh get() = MR.strings.settings_running_refresh
    val settingsTestOk get() = MR.strings.settings_test_ok
    val settingsPartialOk get() = MR.strings.settings_partial_ok
    val settingsSkipped get() = MR.strings.settings_skipped
    val settingsNotSignedIn get() = MR.strings.settings_not_signed_in
    val settingsCancelled get() = MR.strings.settings_cancelled
    val settingsFailedStatus get() = MR.strings.settings_failed_status
    val settingsLastSync get() = MR.strings.settings_last_sync
    val settingsLastBackgroundSync get() = MR.strings.settings_last_background_sync
    val settingsShortcutsHelp get() = MR.strings.settings_shortcuts_help
    val settingsLogOut get() = MR.strings.settings_log_out
    val settingsVersion get() = MR.strings.settings_version
    val settingsCredit get() = MR.strings.settings_credit
    val settingsCreditName get() = MR.strings.settings_credit_name
    val backgroundAndroidStatus get() = MR.strings.background_android_status
    val backgroundSimulatorStatus get() = MR.strings.background_simulator_status
    val backgroundOn get() = MR.strings.background_on
    val backgroundOff get() = MR.strings.background_off
    val backgroundRestricted get() = MR.strings.background_restricted
    val backgroundUnknown get() = MR.strings.background_unknown
    val backgroundUnknownStatus get() = MR.strings.background_unknown_status
}
