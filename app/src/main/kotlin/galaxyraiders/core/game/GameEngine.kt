package galaxyraiders.core.game

import galaxyraiders.Config
import galaxyraiders.ports.RandomGenerator
import galaxyraiders.ports.ui.Controller
import galaxyraiders.ports.ui.Controller.PlayerCommand
import galaxyraiders.ports.ui.Visualizer
import kotlin.system.measureTimeMillis

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.io.File

const val MILLISECONDS_PER_SECOND: Int = 1000

object GameEngineConfig {
  private val config = Config(prefix = "GR__CORE__GAME__GAME_ENGINE__")

  val frameRate = config.get<Int>("FRAME_RATE")
  val spaceFieldWidth = config.get<Int>("SPACEFIELD_WIDTH")
  val spaceFieldHeight = config.get<Int>("SPACEFIELD_HEIGHT")
  val asteroidProbability = config.get<Double>("ASTEROID_PROBABILITY")
  val coefficientRestitution = config.get<Double>("COEFFICIENT_RESTITUTION")

  val msPerFrame: Int = MILLISECONDS_PER_SECOND / this.frameRate
}

@Suppress("TooManyFunctions")
class GameEngine(
  val generator: RandomGenerator,
  val controller: Controller,
  val visualizer: Visualizer,
) {
  val field = SpaceField(
    width = GameEngineConfig.spaceFieldWidth,
    height = GameEngineConfig.spaceFieldHeight,
    generator = generator
  )

  var playing = true

  val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
  val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
  var fileNameScoreboard = "src/main/kotlin/galaxyraiders/core/score/Scoreboard.json"
  var fileNameLeaderboard = "src/main/kotlin/galaxyraiders/core/score/Leaderboard.json"
  var scoreTotal: Int = 0
  var asteroidTotal: Int = 0

  fun execute() {
    while (true) {
      val duration = measureTimeMillis { this.tick() }

      Thread.sleep(
        maxOf(0, GameEngineConfig.msPerFrame - duration)
      )
    }
  }

  fun execute(maxIterations: Int) {
    repeat(maxIterations) {
      this.tick()
    }
  }

  fun tick() {
    this.processPlayerInput()
    this.updateSpaceObjects()
    this.renderSpaceField()
  }

  fun processPlayerInput() {
    this.controller.nextPlayerCommand()?.also {
      when (it) {
        PlayerCommand.MOVE_SHIP_UP ->
          this.field.ship.boostUp()
        PlayerCommand.MOVE_SHIP_DOWN ->
          this.field.ship.boostDown()
        PlayerCommand.MOVE_SHIP_LEFT ->
          this.field.ship.boostLeft()
        PlayerCommand.MOVE_SHIP_RIGHT ->
          this.field.ship.boostRight()
        PlayerCommand.LAUNCH_MISSILE ->
          this.field.generateMissile()
        PlayerCommand.PAUSE_GAME ->
          this.playing = !this.playing
      }
    }
  }

  fun updateSpaceObjects() {
    if (!this.playing) return
    this.handleCollisions()
    this.moveSpaceObjects()
    this.trimSpaceObjects()
    this.generateAsteroids()
    this.setTimerExplosion()
  }

  fun handleCollisions() {
    this.field.spaceObjects.forEachPair {
        (first, second) ->
      if (first.impacts(second)) {
        first.collideWith(second, GameEngineConfig.coefficientRestitution)
        if(first is Missile && second is Asteroid){
          this.field.generateExplosion(second.center)
          this.UpdateScore(CalculateScore(second.mass, second.radius))
          this.field.deleteAsteroid(second)
          this.field.deleteMissile(first)
        }
        else if(first is Asteroid && second is Missile){
          this.field.generateExplosion(first.center)
          this.UpdateScore(CalculateScore(first.mass, first.radius))
          this.field.deleteAsteroid(first)
          this.field.deleteMissile(second)
        }
      }
    }
  }

  private fun CalculateScore(mass: Double, radius: Double): Int {
    val result = mass / radius
    return result.toInt()
  }

  private fun UpdateScore(score: Int){
    scoreTotal = scoreTotal + score
    asteroidTotal = asteroidTotal + 1
    generateScoreboard(fileNameScoreboard, date, time, score, asteroidTotal)
    generateLeaderboard(fileNameLeaderboard, date, time, score, asteroidTotal)
  }

  fun setTimerExplosion(){
    this.field.iniciateExplosion()
  }

  fun moveSpaceObjects() {
    this.field.moveShip()
    this.field.moveAsteroids()
    this.field.moveMissiles()
  }

  fun trimSpaceObjects() {
    this.field.trimAsteroids()
    this.field.trimMissiles()
    this.field.trimExplosions()
  }

  fun generateAsteroids() {
    val probability = generator.generateProbability()

    if (probability <= GameEngineConfig.asteroidProbability) {
      this.field.generateAsteroid()
    }
  }

  fun renderSpaceField() {
    this.visualizer.renderSpaceField(this.field)
  }

  fun findPosition(arrayPositions: JSONArray, newPoints: Int): Int {
    for (i in 0 until arrayPositions.length()) {
        val position: JSONObject = arrayPositions.getJSONObject(i)
        val oldPoints: Int? = position.optInt("Pontuação Final")
        if (oldPoints != null) {
            if (newPoints > oldPoints) {
                return i
            }
        }
    }
    return arrayPositions.length()
}

fun createJSONObject(date: String, time: String, points: Int, asteroids: Int): JSONObject {
    val jsonObject = JSONObject()

    jsonObject.put("Data", date)
    jsonObject.put("Hora", time)
    jsonObject.put("Pontuação Final", points)
    jsonObject.put("Quantidade de Asteroides", asteroids)

    return jsonObject
}

fun getJSONfromFile(fileName: String):  JSONObject {
    var json = JSONObject()
    try {
        val file = File(fileName)
        if (!file.exists())
            file.createNewFile()
        var content = String(Files.readAllBytes(Paths.get(fileName)))

        try {
            json = JSONObject(content)
        } catch (e: JSONException) {}

        if (!json.has("Jogos"))
            json.put("Jogos", JSONArray())

    } catch (e: IOException) {
        println("An error occurred: ${e.message}")
    }
    return json
}

fun writeJSONToFile(fileName: String, json: JSONObject) {
    try {
        val writer = FileWriter(fileName)
        writer.write(json.toString(4))
        writer.close()
    } catch (e: IOException) {
        println("An error occurred: ${e.message}")
    }
}

fun updateArrayGamesScoreboard(gamesArray: JSONArray, newJSONObject: JSONObject) {
    val newDate = newJSONObject.getString("Data")
    val newHour = newJSONObject.getString("Hora")
    if (gamesArray.length() > 0) {
        val lastElement = gamesArray.getJSONObject(gamesArray.length() - 1)
        val lastDate = lastElement.getString("Data")
        val lastHour = lastElement.getString("Hora")
        if (lastDate == newDate && lastHour == newHour)
            gamesArray.put(gamesArray.length() - 1, newJSONObject)
        else
            gamesArray.put(newJSONObject)
    }
    else
        gamesArray.put(newJSONObject)
}

fun generateScoreboard(fileName: String, date: String, time: String, points: Int, asteroids: Int) {
    val jsonObject = createJSONObject(date, time, points, asteroids)
    try {
        val jsonFromFile = getJSONfromFile(fileName)
        var gamesArray = jsonFromFile.getJSONArray("Jogos")
        updateArrayGamesScoreboard(gamesArray, jsonObject)

        jsonFromFile.put("Jogos", gamesArray)
        writeJSONToFile(fileName, jsonFromFile)
    } catch (e: IOException) {
        println("An error occurred: ${e.message}")
    } catch (e: JSONException) {
        println("An error occurred: ${e.message}")
    }
}

fun updateArrayGamesLeaderboard(gamesArray: JSONArray, newJSONObject: JSONObject): Int {
  val newDate = newJSONObject.getString("Data")
  val newHour = newJSONObject.getString("Hora")
  for (i in 0 until gamesArray.length()) {
      val element = gamesArray.getJSONObject(i)
      val lastDate = element.getString("Data")
      val lastHour = element.getString("Hora")
      if (lastDate == newDate && lastHour == newHour) {
          gamesArray.put(i, newJSONObject)
          if (i == 0)
              return 0
          val newPoints = newJSONObject.getInt("Pontuação Final")
          for (j in i - 1 downTo 0) {
              val elementToMove = gamesArray.getJSONObject(j)
              if (newPoints > elementToMove.getInt("Pontuação Final")) {
                  gamesArray.put(j + 1, elementToMove)
                  gamesArray.put(j, newJSONObject)
              }
          }
          return 0
      }
  }
  return 1
}

fun insertGameLeaderboard(gameToInsert: JSONObject, gamesArray: JSONArray) {
    var gameToMove: JSONObject = gameToInsert
    val position = findPosition(gamesArray, gameToMove.optInt("Pontuação Final"))
    if (updateArrayGamesLeaderboard(gamesArray, gameToMove) != 0) {
        var numberOfGames = gamesArray.length()
        for (i in position until numberOfGames + 1) {
            if (i < 3) {
                var nextGame = JSONObject()
                if (i < numberOfGames)
                    nextGame = gamesArray.getJSONObject(i)
                gamesArray.put(i, gameToMove)
                gameToMove = nextGame
            }
        }
    }
}

fun generateLeaderboard(fileName: String, date: String, time: String, points: Int, asteroids: Int) {
    val gameToInsert = createJSONObject(date, time, points, asteroids)

    try {
        val jsonFromFile = getJSONfromFile(fileName)
        var gamesArray = JSONArray()
        try {
            gamesArray = jsonFromFile.getJSONArray("Jogos")
        } catch (e: JSONException) {
            println("An error occurred: ${e.message}")
            throw e
        }
        insertGameLeaderboard(gameToInsert, gamesArray)

        jsonFromFile.put("Jogos", gamesArray)
        writeJSONToFile(fileName, jsonFromFile)
    } catch (e: IOException) {
        println("An error occurred: ${e.message}")
    }
  }
}

fun <T> List<T>.forEachPair(action: (Pair<T, T>) -> Unit) {
  for (i in 0 until this.size) {
    for (j in i + 1 until this.size) {
      action(Pair(this[i], this[j]))
    }
  }
}
