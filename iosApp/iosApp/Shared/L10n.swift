import Foundation
import ComposeApp

/// Native SwiftUI access to the shared moko-resources catalog.
/// Every value resolves using the device system language.
enum L10n {
    private static func s(_ resource: StringResource) -> String {
        resource.desc().localized()
    }

    // `MR.strings` is exported by Kotlin/Native as a nested Swift type.
    private static let strings = MR.strings.shared

    private static func f(_ resource: StringResource, _ args: [Any]) -> String {
        resource.format(args_: args).localized()
    }

    // Common
    static var appName: String { s(strings.app_name) }
    static var back: String { s(strings.back) }
    static var healthFallback: String { s(strings.health_fallback) }
    static var healthAllowAccess: String { s(strings.health_allow_access) }
    static var healthReady: String { s(strings.health_ready) }
    static var healthChecking: String { s(strings.health_checking) }
    static func openHealth(_ service: String) -> String { f(strings.open_health, [service]) }

    // Login
    static var loginTitle: String { s(strings.login_title) }
    static var loginSubtitle: String { s(strings.login_subtitle) }
    static var loginEmailOrPhone: String { s(strings.login_email_or_phone) }
    static var loginPassword: String { s(strings.login_password) }
    static var loginSignIn: String { s(strings.login_sign_in) }
    static var loginPrivacy: String { s(strings.login_privacy) }
    static var loginVerifyIdentity: String { s(strings.login_verify_identity) }
    static var loginCheckEmail: String { s(strings.login_check_email) }
    static var loginCodeSent: String { s(strings.login_code_sent) }
    static func loginCodeSentTo(_ target: String) -> String { f(strings.login_code_sent_to, [target]) }
    static var loginVerify: String { s(strings.login_verify) }
    static var loginDidntGet: String { s(strings.login_didnt_get) }
    static var loginResend: String { s(strings.login_resend) }
    static var loginBrowserTrouble: String { s(strings.login_browser_trouble) }
    static var loginBrowserTitle: String { s(strings.login_browser_title) }
    static var loginComplete: String { s(strings.login_complete) }
    static var loginStepOne: String { s(strings.login_step_one) }
    static var loginStepOneDetail: String { s(strings.login_step_one_detail) }
    static var loginOpenXiaomi: String { s(strings.login_open_xiaomi) }
    static var loginLinkCopied: String { s(strings.login_link_copied) }
    static var loginCopyLink: String { s(strings.login_copy_link) }
    static var loginStepTwo: String { s(strings.login_step_two) }
    static var loginStepTwoDetail: String { s(strings.login_step_two_detail) }
    static var loginLinkPasted: String { s(strings.login_link_pasted) }
    static var loginPasteDifferent: String { s(strings.login_paste_different) }
    static var loginRemoveLink: String { s(strings.login_remove_link) }
    static var loginTapPaste: String { s(strings.login_tap_paste) }
    static var loginCopyFromBrowser: String { s(strings.login_copy_from_browser) }
    static var loginReady: String { s(strings.login_ready) }
    static var loginTip: String { s(strings.login_tip) }
    static var loginNothingToPaste: String { s(strings.login_nothing_to_paste) }
    static var loginInvalidLink: String { s(strings.login_invalid_link) }
    static var loginNotWebLink: String { s(strings.login_not_web_link) }
    static var loginVerificationCode: String { s(strings.login_verification_code) }
    static var loginEmpty: String { s(strings.login_empty) }
    static func loginDigits(_ count: Int, _ total: Int) -> String { f(strings.login_digits, [count, total]) }

    // Home
    static var homeAccount: String { s(strings.home_account) }
    static var homeSignedIn: String { s(strings.home_signed_in) }
    static var homeConnected: String { s(strings.home_connected) }
    static var homeLoadingProfile: String { s(strings.home_loading_profile) }
    static var homeSetup: String { s(strings.home_setup) }
    static var homeSettingsAccessibility: String { s(strings.home_settings_accessibility) }
    static func homeHealthAccessibility(_ service: String) -> String { f(strings.home_health_accessibility, [service]) }
    static var homeHealthReadyAccessibility: String { s(strings.home_health_ready_accessibility) }
    static var homeSettingsHint: String { s(strings.home_settings_hint) }
    static var homeActivity: String { s(strings.home_activity) }
    static var homeSyncOptions: String { s(strings.home_sync_options) }
    static var homeNoneSelected: String { s(strings.home_none_selected) }
    static var homeMetricOne: String { s(strings.home_metric_one) }
    static func homeMetricMany(_ n: Int) -> String { f(strings.home_metric_many, [n]) }
    static var homeDayOne: String { s(strings.home_day_one) }
    static func homeDayMany(_ n: Int) -> String { f(strings.home_day_many, [n]) }
    static var homeAutoOn: String { s(strings.home_auto_on) }
    static var homeManual: String { s(strings.home_manual) }
    static var homeNothingSelectedTitle: String { s(strings.home_nothing_selected_title) }
    static func homeNothingSelectedDetail(_ health: String) -> String { f(strings.home_nothing_selected_detail, [health]) }
    static func homeAllowAccessDetail(_ health: String) -> String { f(strings.home_allow_access_detail, [health]) }
    static var homeLastSync: String { s(strings.home_last_sync) }
    static var homeBackgroundSync: String { s(strings.home_background_sync) }
    static var homeNever: String { s(strings.home_never) }
    static var homeSyncing: String { s(strings.home_syncing) }
    static var homeSyncNow: String { s(strings.home_sync_now) }
    static var homeOpenSettings: String { s(strings.home_open_settings) }
    static func homeFailed(_ value: String) -> String { f(strings.home_failed_suffix, [value]) }
    static func homePartial(_ value: String) -> String { f(strings.home_partial_suffix, [value]) }

    // Sync
    static var syncTitle: String { s(strings.sync_title) }
    static var syncNow: String { s(strings.sync_now) }
    static func syncLast(_ days: Int) -> String { days == 1 ? s(strings.sync_last_one_day) : f(strings.sync_last_n_days, [days]) }
    static var syncNoMetrics: String { s(strings.sync_no_metrics) }
    static var syncPreparing: String { s(strings.sync_preparing) }
    static var syncStatusSyncing: String { s(strings.sync_status_syncing) }
    static var syncStatusDone: String { s(strings.sync_status_done) }
    static var outcomeNotSynced: String { s(strings.outcome_not_synced) }
    static var outcomeSuccessDetail: String { s(strings.outcome_success_detail) }
    static var outcomeCouldNotFinish: String { s(strings.outcome_could_not_finish) }
    static var outcomeIdleDetail: String { s(strings.outcome_idle_detail) }
    static var syncStatusFailed: String { s(strings.sync_status_failed) }
    static var syncStatusWaiting: String { s(strings.sync_status_waiting) }
    static func syncRecords(_ n: Int) -> String { f(strings.sync_records, [n]) }

    // Settings
    static var settingsTitle: String { s(strings.settings_title) }
    static var outcomePartialDetail: String { s(strings.outcome_partial_detail) }
    static var outcomeNothingToDo: String { s(strings.outcome_nothing_to_do) }
    static var outcomePleaseSignIn: String { s(strings.outcome_please_sign_in) }
    static var outcomeHealthUnavailable: String { s(strings.outcome_health_unavailable) }
    static var outcomeStopped: String { s(strings.outcome_stopped) }
    static var settingsSyncOptionsLoading: String { s(strings.settings_sync_options_loading) }
    static var settingsSectionSyncRange: String { s(strings.settings_section_sync_range) }
    static var settingsSectionAutoSync: String { s(strings.settings_section_auto_sync) }
    static var settingsSectionMetrics: String { s(strings.settings_section_metrics) }
    static var settingsSectionStatus: String { s(strings.settings_section_status) }
    static var settingsSectionShortcuts: String { s(strings.settings_section_shortcuts) }
    static var settingsSectionAccount: String { s(strings.settings_section_account) }
    static var settingsSectionAbout: String { s(strings.settings_section_about) }
    static var settingsDaysPicker: String { s(strings.settings_days_picker) }
    static var settingsRange1Day: String { s(strings.settings_range_1_day) }
    static func settingsRangeNDays(_ n: Int) -> String { f(strings.settings_range_n_days, [n]) }
    static var settingsBackgroundAutoSync: String { s(strings.settings_background_auto_sync) }
    static var settingsTestBackgroundRefresh: String { s(strings.settings_test_background_refresh) }
    static var settingsLastSync: String { s(strings.settings_last_sync) }
    static var settingsLastBackgroundSync: String { s(strings.settings_last_background_sync) }
    static var settingsShortcutsHelp: String { s(strings.settings_shortcuts_help) }
    static var settingsLogOut: String { s(strings.settings_log_out) }
    static var settingsVersion: String { s(strings.settings_version) }
    static var settingsCredit: String { s(strings.settings_credit) }
    static var settingsCreditName: String { s(strings.settings_credit_name) }
    static var backgroundSimulator: String { s(strings.background_simulator_status) }
    static var backgroundOn: String { s(strings.background_on) }
    static var backgroundOff: String { s(strings.background_off) }
    static var backgroundRestricted: String { s(strings.background_restricted) }
    static var backgroundUnknown: String { s(strings.background_unknown) }
}
