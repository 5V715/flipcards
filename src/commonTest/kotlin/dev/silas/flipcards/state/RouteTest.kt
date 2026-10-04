package dev.silas.flipcards.state

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteTest {
    @Test fun parses() {
        assertEquals(Route.Home, parseRoute(""))
        assertEquals(Route.Home, parseRoute("#"))
        assertEquals(Route.Home, parseRoute("#/"))
        assertEquals(Route.Edit("abc"), parseRoute("#/stack/abc/edit"))
        assertEquals(Route.Play("abc"), parseRoute("#/stack/abc/play"))
        assertEquals(Route.Unknown, parseRoute("#/stack/abc"))
        assertEquals(Route.Unknown, parseRoute("#/nope"))
    }

    @Test fun roundTrips() = listOf(Route.Home, Route.Edit("a"), Route.Play("a")).forEach {
        assertEquals(it, parseRoute(it.toHash()))
    }
}
