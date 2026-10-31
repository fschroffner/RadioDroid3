package com.github.fschroffner.radiodroid3.data.mapper

import com.github.fschroffner.radiodroid3.data.local.entity.RadioStationEntity
import com.github.fschroffner.radiodroid3.data.remote.dto.StationDto
import com.github.fschroffner.radiodroid3.domain.model.RadioStation
import com.github.fschroffner.radiodroid3.station.DataRadioStation

/**
 * Bidirectional mappers converting between API DTOs, Room Entities,
 * Domain Models, and legacy DataRadioStation representations.
 */
object StationMapper {

    fun toDomain(dto: StationDto): RadioStation {
        val uuid = dto.stationUuid?.takeIf { it.isNotBlank() } ?: dto.legacyId.orEmpty()
        return RadioStation(
            stationUuid = uuid,
            name = dto.name.orEmpty(),
            changeUuid = dto.changeUuid.orEmpty(),
            streamUrl = dto.streamUrl.orEmpty(),
            homePageUrl = dto.homePageUrl.orEmpty(),
            iconUrl = dto.iconUrl.orEmpty(),
            country = dto.country.orEmpty(),
            countryCode = dto.countryCode,
            state = dto.state.orEmpty(),
            tagsAll = dto.tags.orEmpty(),
            language = dto.language.orEmpty(),
            clickCount = dto.clickCount ?: 0,
            clickTrend = dto.clickTrend ?: 0,
            votes = dto.votes ?: 0,
            refreshRetryCount = dto.refreshRetryCount ?: 0,
            bitrate = dto.bitrate ?: 0,
            codec = dto.codec,
            working = dto.lastCheckOk != 0,
            hls = dto.hls == 1,
            deletedOnServer = dto.deletedOnServer == 1,
            isFavorite = dto.isFavorite ?: false,
            favoriteOrder = dto.favoriteOrder ?: 0,
            lastPlayed = dto.lastPlayed
        )
    }

    fun toDomain(entity: RadioStationEntity): RadioStation {
        return RadioStation(
            stationUuid = entity.stationUuid,
            name = entity.name,
            changeUuid = entity.changeUuid,
            streamUrl = entity.streamUrl,
            homePageUrl = entity.homePageUrl,
            iconUrl = entity.iconUrl,
            country = entity.country,
            countryCode = entity.countryCode,
            state = entity.state,
            tagsAll = entity.tagsAll,
            language = entity.language,
            clickCount = entity.clickCount,
            clickTrend = entity.clickTrend,
            votes = entity.votes,
            refreshRetryCount = entity.refreshRetryCount,
            bitrate = entity.bitrate,
            codec = entity.codec,
            working = entity.working,
            hls = entity.hls,
            deletedOnServer = entity.deletedOnServer,
            isFavorite = entity.isFavorite,
            favoriteOrder = entity.favoriteOrder,
            lastPlayed = entity.lastPlayed
        )
    }

    fun toDomain(legacy: DataRadioStation): RadioStation {
        return RadioStation(
            stationUuid = legacy.stationUuid,
            name = legacy.name,
            changeUuid = legacy.changeUuid,
            streamUrl = legacy.streamUrl,
            homePageUrl = legacy.homePageUrl,
            iconUrl = legacy.iconUrl,
            country = legacy.country,
            countryCode = legacy.countryCode,
            state = legacy.state,
            tagsAll = legacy.tagsAll,
            language = legacy.language,
            clickCount = legacy.clickCount,
            clickTrend = legacy.clickTrend,
            votes = legacy.votes,
            refreshRetryCount = legacy.refreshRetryCount,
            bitrate = legacy.bitrate,
            codec = legacy.codec,
            working = legacy.working,
            hls = legacy.hls,
            deletedOnServer = legacy.deletedOnServer,
            isFavorite = legacy.isFavorite,
            favoriteOrder = legacy.favoriteOrder,
            lastPlayed = legacy.lastPlayed,
            playableUrl = legacy.playableUrl
        )
    }

    fun toEntity(domain: RadioStation): RadioStationEntity {
        return RadioStationEntity(
            stationUuid = domain.stationUuid,
            name = domain.name,
            changeUuid = domain.changeUuid,
            streamUrl = domain.streamUrl,
            homePageUrl = domain.homePageUrl,
            iconUrl = domain.iconUrl,
            country = domain.country,
            countryCode = domain.countryCode,
            state = domain.state,
            tagsAll = domain.tagsAll,
            language = domain.language,
            clickCount = domain.clickCount,
            clickTrend = domain.clickTrend,
            votes = domain.votes,
            refreshRetryCount = domain.refreshRetryCount,
            bitrate = domain.bitrate,
            codec = domain.codec,
            working = domain.working,
            hls = domain.hls,
            deletedOnServer = domain.deletedOnServer,
            isFavorite = domain.isFavorite,
            favoriteOrder = domain.favoriteOrder,
            lastPlayed = domain.lastPlayed
        )
    }

    fun toLegacy(domain: RadioStation): DataRadioStation {
        val legacy = DataRadioStation()
        legacy.stationUuid = domain.stationUuid
        legacy.name = domain.name
        legacy.changeUuid = domain.changeUuid
        legacy.streamUrl = domain.streamUrl
        legacy.homePageUrl = domain.homePageUrl
        legacy.iconUrl = domain.iconUrl
        legacy.country = domain.country
        legacy.countryCode = domain.countryCode
        legacy.state = domain.state
        legacy.tagsAll = domain.tagsAll
        legacy.language = domain.language
        legacy.clickCount = domain.clickCount
        legacy.clickTrend = domain.clickTrend
        legacy.votes = domain.votes
        legacy.refreshRetryCount = domain.refreshRetryCount
        legacy.bitrate = domain.bitrate
        legacy.codec = domain.codec
        legacy.working = domain.working
        legacy.hls = domain.hls
        legacy.deletedOnServer = domain.deletedOnServer
        legacy.isFavorite = domain.isFavorite
        legacy.favoriteOrder = domain.favoriteOrder
        legacy.lastPlayed = domain.lastPlayed
        legacy.playableUrl = domain.playableUrl
        return legacy
    }

    fun toDto(domain: RadioStation): StationDto {
        return StationDto(
            stationUuid = domain.stationUuid,
            name = domain.name,
            changeUuid = domain.changeUuid,
            streamUrl = domain.streamUrl,
            homePageUrl = domain.homePageUrl,
            iconUrl = domain.iconUrl,
            country = domain.country,
            countryCode = domain.countryCode,
            state = domain.state,
            tags = domain.tagsAll,
            language = domain.language,
            clickCount = domain.clickCount,
            clickTrend = domain.clickTrend,
            votes = domain.votes,
            refreshRetryCount = domain.refreshRetryCount,
            bitrate = domain.bitrate,
            codec = domain.codec,
            lastCheckOk = if (domain.working) 1 else 0,
            hls = if (domain.hls) 1 else 0,
            deletedOnServer = if (domain.deletedOnServer) 1 else 0,
            isFavorite = domain.isFavorite,
            favoriteOrder = domain.favoriteOrder,
            lastPlayed = domain.lastPlayed
        )
    }
}

fun StationDto.toDomain(): RadioStation = StationMapper.toDomain(this)
fun RadioStationEntity.toDomain(): RadioStation = StationMapper.toDomain(this)
fun DataRadioStation.toDomain(): RadioStation = StationMapper.toDomain(this)
fun RadioStation.toEntity(): RadioStationEntity = StationMapper.toEntity(this)
fun RadioStation.toDataRadioStation(): DataRadioStation = StationMapper.toLegacy(this)
fun RadioStation.toDto(): StationDto = StationMapper.toDto(this)
fun RadioStationEntity.toDataRadioStation(): DataRadioStation = StationMapper.toLegacy(this.toDomain())
fun DataRadioStation.toEntity(): RadioStationEntity = StationMapper.toEntity(this.toDomain())
fun DataRadioStation.toDto(): StationDto = StationMapper.toDto(this.toDomain())
