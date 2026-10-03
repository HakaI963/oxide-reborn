/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * Adapted from Zalith Launcher 2+ (https://github.com/Star1xr/ZalithLauncher2Plus),
 * Copyright (C) 2026 Star1xr <166748405+Star1xr@users.noreply.github.com>.
 * Original commits: 5cb5f327d480d41bcb45c40e1010459b78716061,
 * 498a5cba10c2f6115104870a7890a9473fe2e3db.
 * That repository is archived; the code is redistributed here under GPL-3.0.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.utils.settings

import dev.oxide.launcher.game.account.Account
import dev.oxide.launcher.game.account.auth_server.data.AuthServer
import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.unit.AbstractSettingUnit
import dev.oxide.launcher.utils.logging.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "SettingsTransferUtils"

/**
 * 账号备份条目
 *
 * 这里刻意**不包含任何凭证**：accessToken、refreshToken、clientToken、expiresAt、xUid 与
 * otherPassword 都不会被写入备份文件。上游 Plus 的实现把整个 [Account] 实体序列化后写入
 * /sdcard 的明文 JSON，第三方账号密码与微软刷新令牌都可以被任意应用读取；这里改为只备份
 * 账号身份信息，导入后该账号需要重新登录。
 */
@Serializable
data class AccountBackup(
    val uniqueUUID: String,
    val username: String,
    val profileId: String,
    val accountType: String? = null,
    val otherBaseUrl: String? = null,
    val otherAccount: String? = null,
    val skinModelType: String = SkinModelType.NONE.name
)

/** 认证服务器备份条目（本身不含凭证） */
@Serializable
data class AuthServerBackup(
    val baseUrl: String,
    val serverName: String,
    val register: String? = null
)

/**
 * 启动器备份文件
 *
 * @param formatVersion 备份格式版本，用于将来迁移
 */
@Serializable
data class SettingsExport(
    @SerialName("formatVersion") val formatVersion: Int = SettingsTransferUtils.FORMAT_VERSION,
    @SerialName("settings") val settings: Map<String, String> = emptyMap(),
    @SerialName("accounts") val accounts: List<AccountBackup> = emptyList(),
    @SerialName("authServers") val authServers: List<AuthServerBackup> = emptyList()
)

object SettingsTransferUtils {
    const val FORMAT_VERSION = 1

    /** 建议的备份文件名 */
    const val BACKUP_FILE_NAME = "oxide-launcher-backup.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** 把账号转换为备份条目，丢弃全部凭证 */
    fun Account.toBackup(): AccountBackup = AccountBackup(
        uniqueUUID = uniqueUUID,
        username = username,
        profileId = profileId,
        accountType = accountType,
        otherBaseUrl = otherBaseUrl,
        otherAccount = otherAccount,
        skinModelType = skinModelType.name
    )

    /**
     * 由备份条目还原账号
     *
     * 凭证字段全部回到默认值，账号处于「需要重新登录」的状态：
     * 微软账号会在启动前触发重新授权，第三方账号会在令牌被拒绝时要求重新输入密码。
     */
    fun AccountBackup.toAccount(): Account = Account(
        uniqueUUID = uniqueUUID,
        username = username,
        profileId = profileId,
        accountType = accountType,
        otherBaseUrl = otherBaseUrl,
        otherAccount = otherAccount,
        skinModelType = runCatching { SkinModelType.valueOf(skinModelType) }
            .getOrDefault(SkinModelType.NONE)
    )

    fun AuthServer.toBackup(): AuthServerBackup = AuthServerBackup(
        baseUrl = baseUrl,
        serverName = serverName,
        register = register
    )

    fun AuthServerBackup.toAuthServer(): AuthServer = AuthServer(
        baseUrl = baseUrl,
        serverName = serverName,
        register = register
    )

    /**
     * 汇总当前账号与认证服务器为可写出的备份对象
     *
     * 设置项读取的是内存中的 [AbstractSettingUnit.state]，而不是 MMKV，
     * 因此不会在设置尚未恢复时把默认值当成用户配置导出。
     */
    fun buildExport(
        accounts: List<Account>,
        authServers: List<AuthServer>,
        settings: Map<String, String> = AllSettings.allSettings.mapNotNull { unit ->
            unit.state?.let { unit.key to it.toString() }
        }.toMap()
    ): SettingsExport = SettingsExport(
        settings = settings,
        accounts = accounts.map { it.toBackup() },
        authServers = authServers.map { it.toBackup() }
    )

    /**
     * 恢复设置项
     *
     * 只处理可以无损还原的基础类型与枚举；无法解析的条目保持当前值，
     * 返回实际恢复的条目数，便于向用户说明部分设置没有被导入。
     */
    fun restoreSettings(values: Map<String, String>): Int {
        var restored = 0
        for (unit in AllSettings.allSettings) {
            val raw = values[unit.key] ?: continue
            if (unit.restoreFrom(raw)) restored++
        }
        return restored
    }

    /**
     * 把字符串还原成该设置单元的值类型
     *
     * @return 是否成功还原
     */
    @Suppress("UNCHECKED_CAST")
    fun <V> AbstractSettingUnit<V>.restoreFrom(raw: String): Boolean {
        val parsed: Any? = when (val def = defaultValue) {
            is Boolean -> raw.toBooleanStrictOrNull()
            is Int -> raw.toIntOrNull()
            is Long -> raw.toLongOrNull()
            is Float -> raw.toFloatOrNull()
            is String -> raw
            is Enum<*> -> def.javaClass.enumConstants
                ?.firstOrNull { (it as Enum<*>).name == raw }

            else -> null
        } ?: return false
        save(parsed as V)
        return true
    }

    /**
     * 序列化备份对象
     *
     * [AccountBackup] 本身就没有凭证字段，所以这里不存在「忘记剔除」的可能：
     * 凭证不会因为调用方传错参数而出现在文件里。
     */
    fun encode(export: SettingsExport): String =
        json.encodeToString(SettingsExport.serializer(), export)

    fun decode(text: String): SettingsExport =
        json.decodeFromString(SettingsExport.serializer(), text)

    /**
     * 将备份写入指定文件
     *
     * 只写入用户通过系统文件选择器主动指定的位置，不使用任何固定路径或共享存储目录。
     */
    fun writeTo(file: File, export: SettingsExport): Boolean = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(encode(export))
        Logger.info(TAG, "Wrote a backup to ${file.name} with ${export.accounts.size} account(s)")
        true
    }.getOrElse { e ->
        Logger.error(TAG, "Failed to write the backup to ${file.name}", e)
        false
    }

    fun readFrom(file: File): SettingsExport? = runCatching {
        decode(file.readText())
    }.onFailure { e ->
        Logger.error(TAG, "Failed to read the backup ${file.name}", e)
    }.getOrNull()
}