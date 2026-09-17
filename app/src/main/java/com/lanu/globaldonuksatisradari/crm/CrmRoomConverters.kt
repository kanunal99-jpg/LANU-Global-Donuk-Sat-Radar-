package com.lanu.globaldonuksatisradari.crm

import androidx.room.TypeConverter

class CrmRoomConverters {
    @TypeConverter
    fun stageToString(value: CrmStage): String = value.name

    @TypeConverter
    fun stringToStage(value: String): CrmStage = CrmStage.valueOf(value)

    @TypeConverter
    fun activityTypeToString(value: CrmActivityType): String = value.name

    @TypeConverter
    fun stringToActivityType(value: String): CrmActivityType = CrmActivityType.valueOf(value)

    @TypeConverter
    fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}
