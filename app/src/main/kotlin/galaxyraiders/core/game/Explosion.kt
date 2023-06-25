package galaxyraiders.core.game

import galaxyraiders.core.physics.Point2D
import galaxyraiders.core.physics.Vector2D

class Explosion(
  initialPosition: Point2D,
  initialVelocity: Vector2D,
  radius: Double,
  mass: Double,
) :
  SpaceObject("Explosion", '*', initialPosition, initialVelocity, radius, mass){

  var isTriggered: Boolean = false
  
  var tempo_de_vida: Int = 0
    private set
  
  fun comeca(){
    tempo_de_vida = tempo_de_vida + 1
    if(tempo_de_vida > 15){
      isTriggered = true
    }
  }
  }