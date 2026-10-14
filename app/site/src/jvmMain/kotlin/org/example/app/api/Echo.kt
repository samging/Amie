package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText

/** `GET /api/echo?text=`: a liveness check for the site's server. */
@Api
fun echo(ctx: ApiContext) {
    ctx.res.setBodyText(ctx.req.params["text"] ?: "ok")
}
