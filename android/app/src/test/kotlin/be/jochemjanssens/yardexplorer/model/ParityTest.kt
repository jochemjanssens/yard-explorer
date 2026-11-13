package be.jochemjanssens.yardexplorer.model

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the Kotlin port ranks a real export exactly like the website's squadrats.js.
 * Only runs when YARD_KML points at an export (exports are personal and never committed):
 *   YARD_KML=/path/to/squadrats.kml ./gradlew :app:testDebugUnitTest
 */
class ParityTest {
  @Test
  fun `same visited tiles and yards as the website`() {
    val path = System.getenv("YARD_KML")
    assumeTrue("Set YARD_KML to run the parity check", !path.isNullOrBlank())
    val webJs = File("../../squadrats.js").canonicalFile
    val script = """
      const S = require(${quote(webJs.path)});
      const d = S.analyse(require("fs").readFileSync(${quote(path!!)}, "utf8"));
      const out = {};
      for (const l of ["squadrats", "squadratinhos"]) out[l] = { visited: d[l].visited.size, yards: d[l].yards.map((y) => y.slice().sort().join(";")) };
      console.log(JSON.stringify(out));
    """.trimIndent()
    val proc = ProcessBuilder("node", "-e", script).redirectErrorStream(true).start()
    val json = proc.inputStream.bufferedReader().readText()
    assertEquals("node failed: $json", 0, proc.waitFor())

    val kotlin = Squadrats.analyse(File(path).readText())
    for (layer in Layer.entries) {
      val data = kotlin.layers.getValue(layer)
      val section = Regex("\"${layer.placemark}\":\\{\"visited\":(\\d+),\"yards\":\\[(.*?)]}").find(json)!!
      assertEquals("${layer.placemark} visited", section.groupValues[1].toInt(), data.visited.size)
      val webYards = Regex("\"([^\"]*)\"").findAll(section.groupValues[2]).map { it.groupValues[1] }.toList()
      val ktYards = data.yards.map { y -> y.map { "${it.x},${it.y}" }.sorted().joinToString(";") }
      assertEquals("${layer.placemark} yard count", webYards.size, ktYards.size)
      assertEquals("${layer.placemark} yards, in rank order", webYards, ktYards)
    }
  }

  private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
