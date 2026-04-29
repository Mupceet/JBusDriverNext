package me.jbusdriver.modern.domain.model

import java.io.Serializable

interface ILink : ICollectCategory, Serializable {
    val link: String
}