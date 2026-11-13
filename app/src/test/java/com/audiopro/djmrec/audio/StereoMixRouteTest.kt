package com.audiopro.djmrec.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class StereoMixRouteTest {
    @Test
    fun `default stereo mix uses both sides of track one`() {
        assertEquals(StereoMixRoute(0, 1), StereoMixRoute().validated(1))
        assertEquals("Track 1 L", StereoMixRoute.sourceLabel(0))
        assertEquals("Track 1 R", StereoMixRoute.sourceLabel(1))
    }

    @Test
    fun `cross track choices retain their side and track number`() {
        val route = StereoMixRoute(0, 3).validated(3)
        assertEquals(StereoMixRoute(0, 3), route)
        assertEquals("Track 2 R", StereoMixRoute.sourceLabel(route.rightChannel))
    }

    @Test
    fun `identical selected sources produce mono while None keeps two output channels`() {
        for (source in 0..5) assertEquals(1, StereoMixRoute(source, source).validated(3).outputChannelCount)
        for (route in listOf(StereoMixRoute(), StereoMixRoute(0, -1),
            StereoMixRoute(-1, 3), StereoMixRoute(-1, -1))) {
            assertEquals(route, route.validated(3))
            assertEquals(2, route.outputChannelCount)
        }
        assertEquals("None", StereoMixRoute.sourceLabel(StereoMixRoute.NONE))
        assertEquals(StereoMixRoute(-1, 1), StereoMixRoute(-1, 5).validated(1))
    }

    @Test
    fun `removed inputs fall back to track one independently on each side`() {
        assertEquals(StereoMixRoute(2, 1), StereoMixRoute(2, 5).validated(2))
        assertEquals(StereoMixRoute(0, 1), StereoMixRoute(-2, 5).validated(1))
    }
}
