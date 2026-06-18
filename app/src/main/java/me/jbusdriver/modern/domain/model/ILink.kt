package me.jbusdriver.modern.domain.model

import java.io.Serializable

/**
 * Domain objects that can navigate to a target URL.
 *
 * Collection category is persistence metadata and must stay outside this
 * content model contract.
 */
interface ILink : Serializable {
    val link: String
}
