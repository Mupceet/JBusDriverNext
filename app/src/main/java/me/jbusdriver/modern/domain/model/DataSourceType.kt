package me.jbusdriver.modern.domain.model

enum class DataSourceType(val key: String, val prefix: String = "/") {
    CENSORED("有碼", "/page/"),
    GENRE("有碼類別"),
    ACTRESSES("有碼女優"),

    UNCENSORED("無碼", "/page/"),
    UNCENSORED_GENRE("無碼類別"),
    UNCENSORED_ACTRESSES("無碼女優"),

    XYZ("歐美", "/page/"),
    XYZ_GENRE("xyz/genre"),
    XYZ_ACTRESSES("xyz/actresses"),

    GENRE_HD("高清"),
    Sub("字幕");
}
