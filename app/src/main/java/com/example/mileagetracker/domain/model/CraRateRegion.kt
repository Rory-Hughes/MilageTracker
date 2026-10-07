package com.example.mileagetracker.domain.model

/**
 * CRA prescribed automobile allowance regions.
 *
 * The territories receive a four-cent supplement for each rate tier.
 */
enum class CraRateRegion {
    PROVINCES,
    TERRITORIES,
}

class CraRateRegionRequiredException : IllegalStateException(
    "Select provinces or territories before calculating a CRA prescribed allowance estimate."
)
