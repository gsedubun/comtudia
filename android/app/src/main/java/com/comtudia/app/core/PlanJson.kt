package com.comtudia.app.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Raised when a plan document cannot be parsed or is not usable. */
class PlanFormatException(message: String) : Exception(message)

/**
 * Reads and writes the desktop-compatible `plan.json` document.
 *
 * Encoding produces two-space indented JSON with the desktop field names and a
 * trailing newline. Decoding keeps the desktop defaults for missing fields,
 * upgrades a zero version, rejects newer schema versions and validates the
 * result.
 */
@OptIn(ExperimentalSerializationApi::class)
object PlanJson {
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val compact = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun encode(plan: Plan): String = pretty.encodeToString(Plan.serializer(), plan) + "\n"

    fun decode(text: String): Plan {
        val parsed = try {
            compact.decodeFromString(Plan.serializer(), text)
        } catch (e: SerializationException) {
            throw PlanFormatException("parse plan: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw PlanFormatException("parse plan: ${e.message}")
        }

        var plan = parsed
        if (plan.version == 0) plan = plan.copy(version = Plan.VERSION)
        if (plan.version > Plan.VERSION) {
            throw PlanFormatException(
                "plan version ${plan.version} is newer than supported version ${Plan.VERSION}",
            )
        }
        plan.validate()?.let { throw PlanFormatException("invalid plan: ${it.message}") }
        return plan
    }
}
