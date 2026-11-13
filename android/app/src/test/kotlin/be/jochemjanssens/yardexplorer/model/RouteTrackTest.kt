package be.jochemjanssens.yardexplorer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackTest {
  private val z = 17
  private val x0 = 67200
  private val y0 = 43900

  private fun lat(y: Double) = Squadrats.tileToLat(y, z)
  private fun lon(x: Double) = Squadrats.tileToLon(x, z)

  @Test
  fun `a stretch inside one tile crosses just that tile`() {
    val t = Squadrats.tilesBetween(lat(y0 + 0.2), lon(x0 + 0.2), lat(y0 + 0.7), lon(x0 + 0.8), z)
    assertEquals(listOf(Tile(x0, y0)), t)
  }

  @Test
  fun `a horizontal stretch lists every tile it crosses, in order`() {
    val t = Squadrats.tilesBetween(lat(y0 + 0.5), lon(x0 + 0.5), lat(y0 + 0.5), lon(x0 + 3.5), z)
    assertEquals(listOf(Tile(x0, y0), Tile(x0 + 1, y0), Tile(x0 + 2, y0), Tile(x0 + 3, y0)), t)
  }

  @Test
  fun `a stretch heading west and north crosses tiles in that direction`() {
    val t = Squadrats.tilesBetween(lat(y0 + 2.5), lon(x0 + 2.5), lat(y0 + 0.5), lon(x0 + 0.5), z)
    assertEquals(Tile(x0 + 2, y0 + 2), t.first())
    assertEquals(Tile(x0, y0), t.last())
    // 4-connected steps only: consecutive tiles differ by one tile in exactly one direction
    t.zipWithNext().forEach { (a, b) -> assertEquals(1, Math.abs(a.x - b.x) + Math.abs(a.y - b.y)) }
  }

  @Test
  fun `a diagonal stretch visits a connected chain of tiles`() {
    val t = Squadrats.tilesBetween(lat(y0 + 0.1), lon(x0 + 0.1), lat(y0 + 4.6), lon(x0 + 3.2), z)
    assertEquals(Tile(x0, y0), t.first())
    assertEquals(Tile(x0 + 3, y0 + 4), t.last())
    t.zipWithNext().forEach { (a, b) -> assertEquals(1, Math.abs(a.x - b.x) + Math.abs(a.y - b.y)) }
    assertEquals(t.size, t.toSet().size) // no tile twice
  }

  private fun track(visited: Set<Tile>) = RouteTrack { layer -> if (layer == Layer.SQUADRATINHOS) visited else emptySet() }

  @Test
  fun `crossing tiles is split into new and already visited`() {
    val visited = setOf(Tile(x0 + 1, y0)) // the middle tile of the ride was ridden before
    val track = track(visited)
    assertTrue(track.add(lat(y0 + 0.5), lon(x0 + 0.5), 5f, 0))
    assertTrue(track.add(lat(y0 + 0.5), lon(x0 + 3.5), 5f, 60_000)) // ~580 m in a minute
    val layer = Layer.SQUADRATINHOS
    assertEquals(listOf(Tile(x0, y0), Tile(x0 + 1, y0), Tile(x0 + 2, y0), Tile(x0 + 3, y0)), track.crossed.getValue(layer).toList())
    assertEquals(listOf(Tile(x0, y0), Tile(x0 + 2, y0), Tile(x0 + 3, y0)), track.newTiles.getValue(layer).toList())
  }

  @Test
  fun `a tile crossed twice is only counted once`() {
    val track = track(emptySet())
    track.add(lat(y0 + 0.5), lon(x0 + 0.5), 5f, 0)
    track.add(lat(y0 + 0.5), lon(x0 + 2.5), 5f, 60_000)
    track.add(lat(y0 + 0.5), lon(x0 + 0.5), 5f, 120_000) // ride back over the same tiles
    assertEquals(3, track.newTiles.getValue(Layer.SQUADRATINHOS).size)
  }

  @Test
  fun `imprecise fixes, tiny moves and GPS jumps are ignored`() {
    val track = track(emptySet())
    assertTrue(track.add(52.0, 5.0, 5f, 0))
    assertFalse(track.add(52.0005, 5.0, 120f, 5_000)) // 120 m accuracy: too vague
    assertFalse(track.add(52.00001, 5.0, 5f, 5_000)) // ~1 m: standing still
    assertFalse(track.add(52.5, 5.0, 5f, 6_000)) // 55 km in a second: a jump
    assertTrue(track.add(52.0005, 5.0, 5f, 10_000)) // ~55 m in 10 s: fine
    assertEquals(2, track.points.size)
  }

  @Test
  fun `distance adds up the accepted stretches`() {
    val track = track(emptySet())
    track.add(52.0, 5.0, 5f, 0)
    track.add(52.001, 5.0, 5f, 20_000) // ~111 m
    track.add(52.002, 5.0, 5f, 40_000) // ~111 m
    assertEquals(222.0, track.distanceM, 3.0)
  }
}
