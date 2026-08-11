package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.Body
import com.varabyte.kobweb.api.http.text

@Api
suspend fun echo(ctx: ApiContext) {
    val msg = ctx.req.params["message"] ?: ""
    ctx.res.body = Body.text(msg)
}
