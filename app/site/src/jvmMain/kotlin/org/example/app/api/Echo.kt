package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText

@Api
suspend fun echo(ctx: ApiContext) {
    val msg = ctx.req.params["message"] ?: ""
    ctx.res.setBodyText(msg)
}
