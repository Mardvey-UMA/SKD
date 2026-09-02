package com.contentagg.config.enums

/**
 * Enumeration of supported content source types.
 * Used by Config Service and parser services to identify content sources.
 */
enum class SourceType {
    /** Habr.com source (company, user, or hub) */
    HABR,

    /** VC.ru source (news or articles) */
    VCRU,

    /** Telegram channel as a content source */
    TELEGRAM,

    /** RSS/Atom feed */
    RSS
}
