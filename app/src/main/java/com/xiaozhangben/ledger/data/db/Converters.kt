package com.xiaozhangben.ledger.data.db

import androidx.room.TypeConverter
import com.xiaozhangben.ledger.domain.model.AccountType
import com.xiaozhangben.ledger.domain.model.RecurrenceFrequency
import com.xiaozhangben.ledger.domain.model.TxnType

class Converters {

    @TypeConverter
    fun txnTypeToString(value: TxnType): String = value.name

    @TypeConverter
    fun stringToTxnType(value: String): TxnType =
        runCatching { TxnType.valueOf(value) }.getOrDefault(TxnType.EXPENSE)

    @TypeConverter
    fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): AccountType =
        runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.OTHER)

    @TypeConverter
    fun frequencyToString(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun stringToFrequency(value: String): RecurrenceFrequency =
        runCatching { RecurrenceFrequency.valueOf(value) }.getOrDefault(RecurrenceFrequency.MONTHLY)
}
