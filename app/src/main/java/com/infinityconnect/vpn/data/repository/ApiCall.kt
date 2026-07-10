package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import retrofit2.HttpException
import java.io.IOException

/**
 * Оборачивает suspend-вызов API в [AppResult], классифицируя исключения:
 *  - IOException → Network (нет сети/таймаут/хост недоступен);
 *  - HttpException 401 → Unauthorized (сессия истекла после неудачного refresh);
 *  - HttpException 5xx/прочее → Server;
 *  - остальное → Unknown.
 *
 * 401 на логине отдельно трактуется как InvalidCredentials в AuthRepository.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: IOException) {
    AppResult.Failure(AppError.Network(e))
} catch (e: HttpException) {
    AppResult.Failure(mapHttp(e))
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e))
}

private fun mapHttp(e: HttpException): AppError = when (val code = e.code()) {
    401 -> AppError.Unauthorized
    in 500..599 -> AppError.Server(code, e.message())
    else -> AppError.Server(code, e.message())
}
