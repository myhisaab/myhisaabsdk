package pk.myhisaab.sdk.model

/**
 * Environment the SDK points the embedded MyHisaab web app at.
 *
 * Only [PRODUCTION] exists today. A `STAGING` entry can be added here (with its
 * own base URL) once a staging host is available — the public API shape does not
 * change.
 */
enum class MyHisaabEnvironment(val baseLoginUrl: String) {
    PRODUCTION("https://simosa.myhisaab.pk/login"),
}
